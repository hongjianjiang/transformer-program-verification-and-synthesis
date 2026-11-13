// File: src/main/scala/Crasp/Printer.scala
package Crasp

import LS.GlobalOrder

/**
 * Printer (verifier/translator compatible)
 * - Prints ALL rules (bool + count) in a single topo order (straight-line program)
 * - Forces Out (cfg.outName) to be printed last if present
 *
 * Verifier grammar supports counting only as:
 *   # bool_expr
 *   #<(INT) bool_expr
 *   #<(INT,INT) bool_expr
 *
 * We print counted bool ALWAYS parenthesized, so it fits "bool_expr":
 *   #( ... )
 *   #<(k)( ... )
 *   #<(s,e)( ... )
 *
 * IMPORTANT:
 * - We do NOT print suffix windows (None, Some(e)) because verifier rejects them.
 * - We do NOT allow start/end = 0 for local counting; verifier rejects 0.
 */
object Printer {

  // Keep rule names EXACTLY (do NOT change casing)
  private def fixName(name: String): String = name

  private def fixChar(c: String, mapDigitsToLetters: Boolean): String = {
    if (!mapDigitsToLetters) c
    else if (c.matches("\\d+")) {
      val i = c.toInt
      if (i >= 0 && i < 26) ('a' + i).toChar.toString else "x" + c
    } else c
  }

  final case class PrintCfg(
    outName: String = "Out",
    mapDigitsToLetters: Boolean = false
  )

  // ============================================================================================
  // Public API
  // ============================================================================================

  def pretty(program: CRASP, cfg: PrintCfg = PrintCfg()): String = {
    val sb = new StringBuilder

    val fixedAlphabet =
      program.alphabet.toList.map(a => fixChar(a, cfg.mapDigitsToLetters)).sorted
    sb.append(s"ALPHABET: ${fixedAlphabet.mkString(" ")}\n\n")

    // Global topo order across BOTH bool+count, with Out forced last
    val order: Vector[String] =
      GlobalOrder.makeRuleOrder(program.boolRules, program.countRules, cfg.outName)

    val boolMap: Map[String, BoolRule] =
      program.boolRules.map(r => r.name -> r).toMap
    val countMap: Map[String, CountRule] =
      program.countRules.map(r => r.name -> r).toMap

    order.foreach { name =>
      boolMap.get(name) match {
        case Some(r) =>
          sb.append(s"${fixName(r.name)} = ${pretty(r.body, cfg)}\n")
        case None =>
          countMap.get(name) match {
            case Some(r) =>
              sb.append(s"${fixName(r.name)} = ${pretty(r.body, cfg)}\n")
            case None => ()
          }
      }
    }

    sb.toString()
  }

  // backward-compatible overload
  def pretty(program: CRASP): String = pretty(program, PrintCfg())

  // ============================================================================================
  // Bool pretty
  // ============================================================================================

  private def prettyBoolParenIfNeeded(e: BoolExpr, cfg: PrintCfg): String = e match {
    case _: BoolAtom    => pretty(e, cfg)
    case BoolExprNot(_) => pretty(e, cfg)
    case _              => s"(${pretty(e, cfg)})"
  }

  def pretty(expr: BoolExpr, cfg: PrintCfg): String = expr match {
    case BoolExprAnd(l, r) => s"${prettyBoolParenIfNeeded(l, cfg)} & ${prettyBoolParenIfNeeded(r, cfg)}"
    case BoolExprOr(l, r)  => s"${prettyBoolParenIfNeeded(l, cfg)} | ${prettyBoolParenIfNeeded(r, cfg)}"
    case BoolExprNot(e)    => s"!${prettyBoolParenIfNeeded(e, cfg)}"
    case atom: BoolAtom    => pretty(atom, cfg)
  }

  def pretty(atom: BoolAtom, cfg: PrintCfg): String = atom match {
    case BoolAtomBoolRule(name) => fixName(name)
    case BoolAtomInitial(char)  => fixChar(char, cfg.mapDigitsToLetters)

    case BoolAtomComparison(strict, l, r) =>
      val op = if (strict) "<" else "<="
      s"${pretty(l, cfg)} $op ${pretty(r, cfg)}"

    case BoolAtomEqual(l, r) =>
      s"${pretty(l, cfg)} = ${pretty(r, cfg)}"

    case BoolAtomPositionPeriod(p, o) =>
      s"%$p=$o"

    case BoolAtomRunParity(ch) =>
      s"rp(${fixChar(ch, cfg.mapDigitsToLetters)})"
  }

  // ============================================================================================
  // Count pretty
  // ============================================================================================

  private def prettyCountParenIfNeeded(e: CountExpr, cfg: PrintCfg): String = e match {
    case _: CountAtom => pretty(e, cfg)
    case _            => s"(${pretty(e, cfg)})"
  }

  def pretty(expr: CountExpr, cfg: PrintCfg): String = expr match {
    case CountExprConditional(cond, t, e) =>
      s"${fixName(cond)} ? ${pretty(t, cfg)} : ${pretty(e, cfg)}"

    case CountExprAdd(l, r) =>
      s"${prettyCountParenIfNeeded(l, cfg)} + ${prettyCountParenIfNeeded(r, cfg)}"

    case CountExprSubtract(l, r) =>
      s"${prettyCountParenIfNeeded(l, cfg)} - ${prettyCountParenIfNeeded(r, cfg)}"

    case CountExprMin(l, r) =>
      s"min(${pretty(l, cfg)}, ${pretty(r, cfg)})"

    case CountExprMax(l, r) =>
      s"max(${pretty(l, cfg)}, ${pretty(r, cfg)})"

    case atom: CountAtom =>
      pretty(atom, cfg)
  }

  def pretty(atom: CountAtom, cfg: PrintCfg): String = atom match {
    case CountAtomConst(v) =>
      v.toString

    case CountAtomCountRule(n) =>
      fixName(n)

    case CountAtomCounting(b, start, end) =>
      // bool_expr in verifier can be parenthesized, so we always do ( ... )
      val bStr = s"(${pretty(b, cfg)})"

      (start, end) match {
        case (None, None) =>
          s"#$bStr" // "# bool_expr"

        case (Some(s), None) =>
          val ss = requirePastSteps(s)
          s"#<($ss)$bStr" // "#<(INT) bool_expr"

        case (Some(s), Some(e)) =>
          val ss = requirePastSteps(s)
          val ee = requirePastSteps(e)
          if (ee < ss) {
            throw new IllegalArgumentException(s"Invalid CountAtomCounting window: start=$ss end=$ee (end < start)")
          }
          s"#<($ss,$ee)$bStr" // "#<(INT,INT) bool_expr"

        case (None, Some(e)) =>
          // Not supported by verifier grammar at all
          throw new IllegalArgumentException(
            s"Verifier does not support suffix-only CountAtomCounting(None, Some($e))."
          )
      }
  }

  private def requirePastSteps(x: Int): Int = {
    if (x < 1) {
      throw new IllegalArgumentException(
        s"Verifier requires local counting offsets >= 1 (got $x)."
      )
    }
    x
  }
}
