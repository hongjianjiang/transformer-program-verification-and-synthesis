package Crasp

import java.nio.file.{Files, Paths}
import scala.util.control.NonFatal

// ==============================================================================================
// CRASP Parser (handwritten, fast, Scala 3) -- verifier-aligned
//
// FIXES in this version:
//   (1) Correct precedence for "#": it no longer greedily consumes a full BoolExpr.
//       - "#a" / "#Viol" parses as CountAtomCounting(BoolAtomInitial/BoolAtomBoolRule(...))
//       - "#( ... )" parses as CountAtomCounting(<full BoolExpr>)
//       Therefore "#Viol = 0" is parsed as "(#Viol) = 0", not "#(Viol = 0)".
//
//   (2) Same precedence fix for "#<(" ... ")": after the window bounds, it consumes
//       either a BoolAtom, or a parenthesized full BoolExpr if you write "(...)"
//
//   (3) Optional post-parse type validation:
//       - CountAtomCountRule(name) must refer to an existing count rule name
//       - BoolAtomBoolRule(name) must refer to an existing bool rule name
//       - CountExprConditional(guardBoolName, ...) must refer to an existing bool rule name
//
// This prevents silent mis-typing (e.g., CountAtomCountRule("Viol") where Viol is a BoolRule).
// ==============================================================================================
object CRASPParser {

  def parseFile(path: String, enforceOrder: Boolean = true): Either[String, CRASP] =
    try parse(Files.readString(Paths.get(path)), enforceOrder)
    catch { case NonFatal(e) => Left(s"Error reading file: ${e.getMessage}") }

  def parse(input: String, enforceOrder: Boolean = true): Either[String, CRASP] =
    try {
      // Normalize NBSP early; harmless for normal inputs.
      val normalized = input.replace('\u00A0', ' ')
      val p = parseProgramInternal(normalized, enforceOrder)
      Right(p)
    } catch { case ParseError(msg) => Left(msg) }

  // ============================================================================================
  // Errors
  // ============================================================================================
  private final case class ParseError(msg: String) extends RuntimeException(msg)

  // ============================================================================================
  // Tokens
  // ============================================================================================
  private enum Tok {
    case IdentU(name: String) // UNAME
    case IdentL(name: String) // LNAME
    case IntLit(value: Int)

    case KwAlphabet // "ALPHABET:"
    case KwMin      // "min"
    case KwMax      // "max"

    case LParen, RParen, Comma
    case Eq, Lt, Le
    case Or, And, Not
    case QMark, Colon
    case Plus, Minus
    case Hash
    case Percent
    case HashLtLParen // "#<("

    case Eof
  }

  // ============================================================================================
  // Lexer
  // ============================================================================================
  private final case class Lex(in: String) {
    private val n = in.length
    private var i = 0

    // expose for snapshot/restore
    def pos: Int = i
    def setPos(p: Int): Unit = i = p

    private def atEnd: Boolean = i >= n
    private def ch: Char = in.charAt(i)

    private def isUStart(c: Char): Boolean = (c == '_') || (c >= 'A' && c <= 'Z')
    private def isUCont(c: Char): Boolean = (c == '_') || c.isLetterOrDigit
    private def isLStart(c: Char): Boolean = (c == '_') || (c >= 'a' && c <= 'z')
    private def isLCont(c: Char): Boolean =
      (c == '_') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')

    // IMPORTANT: includes ASCII space U+0020
    private def isWS(c: Char): Boolean =
      (c <= ' ') || java.lang.Character.isWhitespace(c) || java.lang.Character.isSpaceChar(c)

    private def skipWsAndComments(): Unit = {
      var changed = true
      while (changed) {
        changed = false

        // whitespace
        while (!atEnd && isWS(ch)) { i += 1; changed = true }

        // block comment /* ... */
        if (!atEnd && ch == '/' && i + 1 < n && in.charAt(i + 1) == '*') {
          changed = true
          i += 2
          while (i + 1 < n && !(in.charAt(i) == '*' && in.charAt(i + 1) == '/')) i += 1
          if (i + 1 >= n) throw ParseError("Unterminated comment")
          i += 2
        }
      }
    }

    def nextTok(): Tok = {
      skipWsAndComments()
      if (atEnd) return Tok.Eof

      // Special "ALPHABET:" keyword (must be exact)
      if (startsWithAt(i, "ALPHABET:")) {
        i += "ALPHABET:".length
        return Tok.KwAlphabet
      }

      // Special "#<(" token
      if (ch == '#' && i + 2 < n && in.charAt(i + 1) == '<' && in.charAt(i + 2) == '(') {
        i += 3
        return Tok.HashLtLParen
      }

      // operators with 2 chars
      if (ch == '<' && i + 1 < n && in.charAt(i + 1) == '=') { i += 2; return Tok.Le }

      // single-char tokens
      ch match {
        case '(' => i += 1; return Tok.LParen
        case ')' => i += 1; return Tok.RParen
        case ',' => i += 1; return Tok.Comma
        case '=' => i += 1; return Tok.Eq
        case '<' => i += 1; return Tok.Lt
        case '|' => i += 1; return Tok.Or
        case '&' => i += 1; return Tok.And
        case '!' => i += 1; return Tok.Not
        case '?' => i += 1; return Tok.QMark
        case ':' => i += 1; return Tok.Colon
        case '+' => i += 1; return Tok.Plus
        case '-' => i += 1; return Tok.Minus
        case '#' => i += 1; return Tok.Hash
        case '%' => i += 1; return Tok.Percent
        case _   =>
      }

      // int literal (starts with digit; '-' handled as Tok.Minus)
      if (ch.isDigit) {
        val start = i
        while (!atEnd && in.charAt(i).isDigit) i += 1
        return Tok.IntLit(in.substring(start, i).toInt)
      }

      // identifiers / keywords min/max
      if (isUStart(ch)) {
        val start = i
        i += 1
        while (!atEnd && isUCont(in.charAt(i))) i += 1
        return Tok.IdentU(in.substring(start, i))
      }

      if (isLStart(ch)) {
        val start = i
        i += 1
        while (!atEnd && isLCont(in.charAt(i))) i += 1
        val name = in.substring(start, i)
        return name match {
          case "min" => Tok.KwMin
          case "max" => Tok.KwMax
          case _     => Tok.IdentL(name)
        }
      }

      throw ParseError(f"Unexpected character '$ch' (U+${ch.toInt}%04X) at pos=$i")
    }

    private def startsWithAt(pos: Int, s: String): Boolean =
      pos + s.length <= n && in.regionMatches(pos, s, 0, s.length)
  }

  // ============================================================================================
  // Token stream
  // ============================================================================================
  private final class TS(in: String) {
    private val lex = Lex(in)
    private var la: Tok = lex.nextTok()

    def peek: Tok = la
    def consume(): Tok = { val t = la; la = lex.nextTok(); t }

    def expect(t: Tok): Unit =
      if (la == t) consume()
      else throw ParseError(s"Expected $t but found $la")

    def atEnd: Boolean = la == Tok.Eof

    // snapshot/restore used by bool comparison backtracking and conditional parsing
    final case class Snap(pos: Int, la: Tok)
    def snapshot(): Snap = Snap(lex.pos, la)
    def restore(s: Snap): Unit = {
      lex.setPos(s.pos)
      la = s.la
    }
  }

  private def TS(s: String): TS = new TS(s)

  // ============================================================================================
  // Program parsing
  // ============================================================================================
  private def parseProgramInternal(input: String, enforceOrder: Boolean): CRASP = {
    val lines =
      input.linesIterator
        .map(_.replace('\u00A0', ' '))
        .map(_.stripTrailing)
        .filter(l => l.nonEmpty && !l.stripLeading.startsWith("//"))
        .toVector

    if (lines.isEmpty) throw ParseError("Empty program")

    val alpha = parseAlphabetLine(lines.head)

    val boolRules  = scala.collection.mutable.ArrayBuffer.empty[BoolRule]
    val countRules = scala.collection.mutable.ArrayBuffer.empty[CountRule]

    // verifier-like order checks
    val definedBool   = scala.collection.mutable.LinkedHashSet.empty[String]
    val definedCount  = scala.collection.mutable.LinkedHashSet.empty[String]
    val requiredCount = scala.collection.mutable.Set.empty[String]
    var inCountRules  = false

    for (line <- lines.tail) {
      val eqIdx = findTopLevelEq(line)
      if (eqIdx < 0) throw ParseError(s"Expected '=' in rule line: '$line'")

      val name = line.substring(0, eqIdx).trim
      val rhs  = line.substring(eqIdx + 1).trim
      if (name.isEmpty || rhs.isEmpty) throw ParseError(s"Bad rule line: '$line'")

      // Try bool first; must consume all tokens
      val boolTry = tryParseAllBool(rhs)
      boolTry match {
        case Some(b) =>
          if (enforceOrder) {
            val usedBool = depsBool(b)
            val badBool  = usedBool.filterNot(definedBool.contains)
            if (badBool.nonEmpty) throw ParseError(s"SyntaxError: Rule ${badBool.head} used before defined.")

            val usedCount = depsCountInBool(b)
            if (!inCountRules) requiredCount ++= usedCount
            else {
              val badCount = usedCount.filterNot(definedCount.contains)
              if (badCount.nonEmpty) throw ParseError(s"SyntaxError: Rule ${badCount.head} used before defined.")
            }
          }

          boolRules += BoolRule(name, b)
          definedBool += name

        case None =>
          val c = parseAllCount(rhs)

          inCountRules = true
          if (enforceOrder) {
            val usedBool = depsBoolInCount(c)
            val badBool  = usedBool.filterNot(definedBool.contains)
            if (badBool.nonEmpty) throw ParseError(s"SyntaxError: Rule ${badBool.head} used before defined.")

            val usedCount = depsCount(c)
            val badCount  = usedCount.filterNot(definedCount.contains)
            if (badCount.nonEmpty) throw ParseError(s"SyntaxError: Rule ${badCount.head} used before defined.")
          }

          countRules += CountRule(name, c)
          definedCount += name
      }
    }

    if (enforceOrder) {
      val undef = requiredCount.diff(definedCount)
      if (undef.nonEmpty)
        throw ParseError(s"SyntaxError: Undefined count rules ${undef.mkString("{", ", ", "}")} used.")
    }

    val prog = CRASP(alpha, boolRules.toList, countRules.toList)

    // Stronger checks (recommended even when enforceOrder=false)
    validateOutNotReferenced(prog)
    validateRuleNameTyping(prog)

    prog
  }

  private def parseAlphabetLine(line: String): Set[String] = {
    val ts = TS(line)
    ts.expect(Tok.KwAlphabet)
    val letters = scala.collection.mutable.Set.empty[String]
    while (!ts.atEnd) {
      ts.peek match {
        case Tok.IdentL(s) => ts.consume(); letters += s
        case Tok.Eof       =>
        case other         => throw ParseError(s"Parse error in alphabet line: expected letter, got $other")
      }
    }
    letters.toSet
  }

  private def tryParseAllBool(rhs: String): Option[BoolExpr] =
    try {
      val ts = TS(rhs)
      val e  = parseBoolExpr(ts)
      if (!ts.atEnd) None else Some(e)
    } catch { case _: ParseError => None }

  private def parseAllCount(rhs: String): CountExpr = {
    val ts = TS(rhs)
    val e  = parseCountExpr(ts)
    if (!ts.atEnd) throw ParseError(s"Trailing tokens in count expr: ${ts.peek}")
    e
  }

  // ============================================================================================
  // Bool: precedence ! > & > |
  // ============================================================================================
  private def parseBoolExpr(ts: TS): BoolExpr = parseBoolOr(ts)

  private def parseBoolOr(ts: TS): BoolExpr = {
    var left = parseBoolAnd(ts)
    while (ts.peek == Tok.Or) {
      ts.consume()
      val right = parseBoolAnd(ts)
      left = BoolExprOr(left, right)
    }
    left
  }

  private def parseBoolAnd(ts: TS): BoolExpr = {
    var left = parseBoolUnary(ts)
    while (ts.peek == Tok.And) {
      ts.consume()
      val right = parseBoolUnary(ts)
      left = BoolExprAnd(left, right)
    }
    left
  }

  private def parseBoolUnary(ts: TS): BoolExpr =
    ts.peek match {
      case Tok.Not =>
        ts.consume()
        BoolExprNot(parseBoolUnary(ts))
      case _ =>
        parseBoolAtomish(ts)
    }

  private def parseBoolAtomish(ts: TS): BoolExpr = ts.peek match {
    case Tok.LParen =>
      ts.consume()
      val e = parseBoolExpr(ts)
      ts.expect(Tok.RParen)
      e

    case Tok.Percent =>
      // position_period: "%" INT "=" INT
      ts.consume()
      val p = parseSignedInt(ts)
      ts.expect(Tok.Eq)
      val o = parseSignedInt(ts)
      BoolAtomPositionPeriod(p, o)

    case _ =>
      // comparison OR atom (needs backtracking)
      val save = ts.snapshot()
      try {
        val l = parseCountExpr(ts)
        ts.peek match {
          case Tok.Le =>
            ts.consume()
            val r = parseCountExpr(ts)
            BoolAtomComparison(strict = false, l, r)
          case Tok.Lt =>
            ts.consume()
            val r = parseCountExpr(ts)
            BoolAtomComparison(strict = true, l, r)
          case Tok.Eq =>
            ts.consume()
            val r = parseCountExpr(ts)
            BoolAtomEqual(l, r)
          case _ =>
            ts.restore(save)
            parseBoolAtom(ts)
        }
      } catch {
        case _: ParseError =>
          ts.restore(save)
          parseBoolAtom(ts)
      }
  }

  private def parseBoolAtom(ts: TS): BoolExpr =
    ts.peek match {
      case Tok.IdentU(name) => ts.consume(); BoolAtomBoolRule(name)
      case Tok.IdentL(ch)   => ts.consume(); BoolAtomInitial(ch)
      case other            => throw ParseError(s"Expected bool atom, got $other")
    }

  // ============================================================================================
  // Count: precedence atom/minmax/paren > +/- > conditional
  // ============================================================================================
  private def parseCountExpr(ts: TS): CountExpr = parseCountConditional(ts)

  private def parseCountConditional(ts: TS): CountExpr = {
    val save0 = ts.snapshot()
    ts.peek match {
      case Tok.IdentU(bn) =>
        ts.consume()
        if (ts.peek == Tok.QMark) {
          ts.consume()
          val t = parseCountExpr(ts)
          ts.expect(Tok.Colon)
          val e = parseCountExpr(ts)
          CountExprConditional(bn, t, e)
        } else {
          ts.restore(save0)
          parseCountAddSub(ts)
        }
      case _ =>
        parseCountAddSub(ts)
    }
  }

  private def parseCountAddSub(ts: TS): CountExpr = {
    var left = parseCountTerm(ts)
    while (ts.peek == Tok.Plus || ts.peek == Tok.Minus) {
      val op = ts.consume()
      val right = parseCountTerm(ts)
      left = op match {
        case Tok.Plus  => CountExprAdd(left, right)
        case Tok.Minus => CountExprSubtract(left, right)
        case _         => left
      }
    }
    left
  }

  // NEW helper: parse the boolean argument of counting (# ...).
  // Rule:
  //   - "#( <BoolExpr> )" allowed (full BoolExpr)
  //   - otherwise parse ONLY a single BoolAtom (tight binding)
  private def parseHashBoolArg(ts: TS): BoolExpr = ts.peek match {
    case Tok.LParen =>
      ts.consume()
      val e = parseBoolExpr(ts)
      ts.expect(Tok.RParen)
      e
    case _ =>
      // critical: ONLY ONE ATOM here, not a whole BoolExpr
      parseBoolAtom(ts)
  }

  private def parseCountTerm(ts: TS): CountExpr = ts.peek match {
    case Tok.KwMin =>
      ts.consume()
      ts.expect(Tok.LParen)
      val a = parseCountExpr(ts)
      ts.expect(Tok.Comma)
      val b = parseCountExpr(ts)
      ts.expect(Tok.RParen)
      CountExprMin(a, b)

    case Tok.KwMax =>
      ts.consume()
      ts.expect(Tok.LParen)
      val a = parseCountExpr(ts)
      ts.expect(Tok.Comma)
      val b = parseCountExpr(ts)
      ts.expect(Tok.RParen)
      CountExprMax(a, b)

    case Tok.Hash =>
      // FIX: do NOT parseBoolExpr here; that caused "#Viol = 0" to become "#(Viol = 0)"
      ts.consume()
      val b = parseHashBoolArg(ts)
      CountAtomCounting(b, None, None)

    case Tok.HashLtLParen =>
      ts.consume()
      val a = parseSignedInt(ts)
      ts.peek match {
        case Tok.Comma =>
          ts.consume()
          val bnd = parseSignedInt(ts)
          ts.expect(Tok.RParen)
          val be = parseHashBoolArg(ts) // FIX: same tight binding
          CountAtomCounting(be, Some(a), Some(bnd))
        case Tok.RParen =>
          ts.consume()
          val be = parseHashBoolArg(ts) // FIX
          CountAtomCounting(be, Some(a), Some(a))
        case other =>
          throw ParseError(s"Expected ',' or ')' after '#<($a', got $other")
      }

    case Tok.LParen =>
      ts.consume()
      val e = parseCountExpr(ts)
      ts.expect(Tok.RParen)
      e

    case Tok.Minus | Tok.IntLit(_) =>
      CountAtomConst(parseSignedInt(ts))

    case Tok.IdentU(name) =>
      ts.consume()
      CountAtomCountRule(name)

    case other =>
      throw ParseError(s"Expected count term, got $other")
  }

  private def parseSignedInt(ts: TS): Int =
    ts.peek match {
      case Tok.Minus =>
        ts.consume()
        ts.peek match {
          case Tok.IntLit(v) => ts.consume(); -v
          case other         => throw ParseError(s"Expected int after '-', got $other")
        }
      case Tok.IntLit(v) =>
        ts.consume(); v
      case other =>
        throw ParseError(s"Expected int, got $other")
    }

  // ============================================================================================
  // Out final-only validation
  // ============================================================================================
  private val ReservedOut = "Out"

  private def validateOutNotReferenced(p: CRASP): Unit = {

    def containsOutBool(e: BoolExpr, selfName: String): Boolean = e match {
      case BoolExprAnd(l, r) => containsOutBool(l, selfName) || containsOutBool(r, selfName)
      case BoolExprOr(l, r)  => containsOutBool(l, selfName) || containsOutBool(r, selfName)
      case BoolExprNot(x)    => containsOutBool(x, selfName)

      case BoolAtomBoolRule(nm) =>
        !selfName.equalsIgnoreCase(ReservedOut) && nm.equalsIgnoreCase(ReservedOut)

      case BoolAtomComparison(_, l, r) => containsOutCount(l) || containsOutCount(r)
      case BoolAtomEqual(l, r)         => containsOutCount(l) || containsOutCount(r)
      case _                           => false
    }

    def containsOutCount(e: CountExpr): Boolean = e match {
      case CountExprConditional(bn, t, el) =>
        bn.equalsIgnoreCase(ReservedOut) || containsOutCount(t) || containsOutCount(el)

      case CountExprAdd(l, r)      => containsOutCount(l) || containsOutCount(r)
      case CountExprSubtract(l, r) => containsOutCount(l) || containsOutCount(r)
      case CountExprMin(l, r)      => containsOutCount(l) || containsOutCount(r)
      case CountExprMax(l, r)      => containsOutCount(l) || containsOutCount(r)

      case CountAtomCounting(b, _, _) => containsOutBool(b, selfName = "CountRule")
      case CountAtomConst(_)          => false
      case CountAtomCountRule(_)      => false
    }

    p.boolRules.foreach { r =>
      if (!r.name.equalsIgnoreCase(ReservedOut) && containsOutBool(r.body, r.name))
        throw ParseError(
          s"SyntaxError: 'Out' is reserved as final output and cannot be referenced (found in bool rule '${r.name}')."
        )
    }

    p.countRules.foreach { r =>
      if (containsOutCount(r.body))
        throw ParseError(
          s"SyntaxError: 'Out' is reserved as final output and cannot be referenced (found in count rule '${r.name}')."
        )
    }
  }

  // ============================================================================================
  // NEW: type/name validation (highly recommended)
  // ============================================================================================
  private def validateRuleNameTyping(p: CRASP): Unit = {
    val boolNames  = p.boolRules.map(_.name.toLowerCase).toSet
    val countNames = p.countRules.map(_.name.toLowerCase).toSet

    def checkBool(e: BoolExpr): Unit = e match {
      case BoolExprAnd(l, r) => checkBool(l); checkBool(r)
      case BoolExprOr(l, r)  => checkBool(l); checkBool(r)
      case BoolExprNot(x)    => checkBool(x)

      case BoolAtomBoolRule(nm) =>
        val k = nm.toLowerCase
        if (!boolNames.contains(k))
          throw ParseError(s"TypeError: Bool rule '$nm' referenced but not defined as BoolRule.")

      case BoolAtomComparison(_, l, r) => checkCount(l); checkCount(r)
      case BoolAtomEqual(l, r)         => checkCount(l); checkCount(r)

      case _: BoolAtomInitial          => ()
      case _: BoolAtomPositionPeriod   => ()
      case _: BoolAtomRunParity        => ()
    }

    def checkCount(e: CountExpr): Unit = e match {
      case CountExprConditional(bn, t, el) =>
        val k = bn.toLowerCase
        if (!boolNames.contains(k))
          throw ParseError(s"TypeError: Conditional guard '$bn' referenced but not defined as BoolRule.")
        checkCount(t); checkCount(el)

      case CountExprAdd(l, r)      => checkCount(l); checkCount(r)
      case CountExprSubtract(l, r) => checkCount(l); checkCount(r)
      case CountExprMin(l, r)      => checkCount(l); checkCount(r)
      case CountExprMax(l, r)      => checkCount(l); checkCount(r)

      case CountAtomConst(_) => ()

      case CountAtomCountRule(nm) =>
        val k = nm.toLowerCase
        if (!countNames.contains(k)) {
          if (boolNames.contains(k))
            throw ParseError(s"TypeError: '$nm' used as CountRule, but it is a BoolRule.")
          else
            throw ParseError(s"TypeError: Count rule '$nm' referenced but not defined.")
        }

      case CountAtomCounting(b, _, _) =>
        checkBool(b)
    }

    // check all rules
    p.boolRules.foreach(r => checkBool(r.body))
    p.countRules.foreach(r => checkCount(r.body))
  }

  // ============================================================================================
  // Dependencies (for enforceOrder=true)
  // ============================================================================================
  private def depsBool(e: BoolExpr): Set[String] = e match {
    case BoolExprAnd(l, r)           => depsBool(l) ++ depsBool(r)
    case BoolExprOr(l, r)            => depsBool(l) ++ depsBool(r)
    case BoolExprNot(x)              => depsBool(x)
    case BoolAtomBoolRule(nm)        => Set(nm)
    case BoolAtomComparison(_, l, r) => depsCount(l) ++ depsCount(r)
    case BoolAtomEqual(l, r)         => depsCount(l) ++ depsCount(r)
    case _                           => Set.empty
  }

  private def depsCountInBool(e: BoolExpr): Set[String] = e match {
    case BoolExprAnd(l, r)           => depsCountInBool(l) ++ depsCountInBool(r)
    case BoolExprOr(l, r)            => depsCountInBool(l) ++ depsCountInBool(r)
    case BoolExprNot(x)              => depsCountInBool(x)
    case BoolAtomComparison(_, l, r) => depsCount(l) ++ depsCount(r)
    case BoolAtomEqual(l, r)         => depsCount(l) ++ depsCount(r)
    case _                           => Set.empty
  }

  private def depsCount(e: CountExpr): Set[String] = e match {
    case CountExprConditional(bn, t, el) => Set(bn) ++ depsCount(t) ++ depsCount(el)
    case CountExprAdd(l, r)             => depsCount(l) ++ depsCount(r)
    case CountExprSubtract(l, r)        => depsCount(l) ++ depsCount(r)
    case CountExprMin(l, r)             => depsCount(l) ++ depsCount(r)
    case CountExprMax(l, r)             => depsCount(l) ++ depsCount(r)
    case CountAtomCountRule(nm)         => Set(nm)
    case CountAtomCounting(b, _, _)     => depsBool(b)
    case CountAtomConst(_)              => Set.empty
  }

  private def depsBoolInCount(e: CountExpr): Set[String] = e match {
    case CountExprConditional(bn, t, el) => Set(bn) ++ depsBoolInCount(t) ++ depsBoolInCount(el)
    case CountExprAdd(l, r)             => depsBoolInCount(l) ++ depsBoolInCount(r)
    case CountExprSubtract(l, r)        => depsBoolInCount(l) ++ depsBoolInCount(r)
    case CountExprMin(l, r)             => depsBoolInCount(l) ++ depsBoolInCount(r)
    case CountExprMax(l, r)             => depsBoolInCount(l) ++ depsBoolInCount(r)
    case CountAtomCounting(b, _, _)     => depsBool(b)
    case _                              => Set.empty
  }

  // ============================================================================================
  // Utility: find '=' at top-level (ignore parentheses)
  // ============================================================================================
  private def findTopLevelEq(s: String): Int = {
    var depth = 0
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '(' => depth += 1
        case ')' => depth -= 1
        case '=' if depth == 0 => return i
        case _ =>
      }
      i += 1
    }
    -1
  }
}
