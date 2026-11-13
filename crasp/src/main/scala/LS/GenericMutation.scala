package LS

import scala.util.Random
import Crasp.*

/**
 * GenericMutator — depth-1, type-directed, finite-domain.
 *
 * Performance notes (all hot-path allocations eliminated):
 *  - Rule name lookups use cfg.boolNamesArr(j) / cfg.countNamesArr(j) → O(1) Array access
 *    instead of p.boolRules(j).name → O(j) List traversal.
 *  - Rule counts use cfg.numBoolRules / cfg.numCountRules → O(1) instead of List.length O(n).
 *  - sampleCountAtom takes countRuleIdx: Int (sentinel -1 = "no count context") instead of
 *    Option[Int], eliminating per-call Some[Int] boxing.
 *  - outIndex cached as a val at the start of every multi-call function.
 *  - Sampling pools are static pre-computed arrays (see companion object).
 *
 * Correctness guarantees (unchanged):
 *  - BoolRule bodies depth≤1: atom | !atom | atom&atom | atom|atom
 *  - CountRule bodies depth≤1: atom | op(atom,atom) | if Bj then atom else atom
 *  - No forward references: BoolAtomBoolRule(j) requires j < i; same for count rules
 *  - CountAtomCounting predicate never references Out
 *  - CountAtomCounting window always normalised (start ≤ end)
 *  - No pure count aliases Ci = Cj
 *  - No negative constants
 */
object GenericMutator {

  // ===========================================================================================
  // Pre-computed sampling pools (static; selected by boolean flag bits)
  //
  // sampleBoolAtom — index = (hasRunParity ? 4 : 0) + (hasRef ? 2 : 0) + (hasPeriod ? 1 : 0)
  //   type codes: 0=initial, 1=boolref, 2=period, 3=comparison, 4=equality, 5=runparity
  //   weights (no runparity): 4× initial, 3× boolref, 2× period, 3× comparison, 3× equality
  //   weights (runparity):    same + 2× runparity
  // ===========================================================================================
  private val boolAtomPools: Array[Array[Int]] = Array(
    // --- no runparity ---
    Array(0,0,0,0, 3,3,3, 4,4,4),                   // [0] noRef, noPeriod
    Array(0,0,0,0, 2,2, 3,3,3, 4,4,4),              // [1] noRef, hasPeriod
    Array(0,0,0,0, 1,1,1, 3,3,3, 4,4,4),            // [2] hasRef, noPeriod
    Array(0,0,0,0, 1,1,1, 2,2, 3,3,3, 4,4,4),       // [3] hasRef, hasPeriod
    // --- with runparity (index + 4) ---
    Array(0,0,0,0, 3,3,3, 4,4,4, 5,5),              // [4] noRef, noPeriod,  +rp
    Array(0,0,0,0, 2,2, 3,3,3, 4,4,4, 5,5),         // [5] noRef, hasPeriod, +rp
    Array(0,0,0,0, 1,1,1, 3,3,3, 4,4,4, 5,5),       // [6] hasRef, noPeriod, +rp
    Array(0,0,0,0, 1,1,1, 2,2, 3,3,3, 4,4,4, 5,5)   // [7] hasRef, hasPeriod,+rp
  )

  // sampleCountAtom — index = hasRef ? 1 : 0
  //   weights: 4× const, 3× countref, 5× counting
  private val countAtomPools: Array[Array[Int]] = Array(
    Array(0,0,0,0, 2,2,2,2,2),        // [0] noRef
    Array(0,0,0,0, 1,1,1, 2,2,2,2,2)  // [1] hasRef
  )

  // sampleBoolAtomForCounting — index = (hasRunParity ? 4 : 0) + (hasBoolRef ? 2 : 0) + (hasPeriod ? 1 : 0)
  //   type codes: 0=initial, 1=boolref, 2=period, 5=runparity
  //   weights (no runparity): 5× initial, 3× boolref, 2× period
  //   weights (runparity):    same + 2× runparity
  private val countingBoolPools: Array[Array[Int]] = Array(
    // --- no runparity ---
    Array(0,0,0,0,0),                    // [0] noRef, noPeriod
    Array(0,0,0,0,0, 2,2),              // [1] noRef, hasPeriod
    Array(0,0,0,0,0, 1,1,1),            // [2] hasRef, noPeriod
    Array(0,0,0,0,0, 1,1,1, 2,2),       // [3] hasRef, hasPeriod
    // --- with runparity (index + 4) ---
    Array(0,0,0,0,0, 5,5),              // [4] noRef, noPeriod,  +rp
    Array(0,0,0,0,0, 2,2, 5,5),         // [5] noRef, hasPeriod, +rp
    Array(0,0,0,0,0, 1,1,1, 5,5),       // [6] hasRef, noPeriod, +rp
    Array(0,0,0,0,0, 1,1,1, 2,2, 5,5)   // [7] hasRef, hasPeriod,+rp
  )

  // ===========================================================================================
  // Public API
  // ===========================================================================================

  def mutate(p: CRASP, cfg: GMConfig, rnd: Random): CRASP = {
    if (p.boolRules.isEmpty) return p
    val doBool = (rnd.nextDouble() < cfg.pMutateBoolRule) || p.countRules.isEmpty
    val p2 = if (doBool) mutateOneBoolRule(p, cfg, rnd) else mutateOneCountRule(p, cfg, rnd)
    if (cfg.pruneUnreachable) prune(p2) else p2
  }

  // ===========================================================================================
  // Bool-rule mutation
  // ===========================================================================================

  private def mutateOneBoolRule(p: CRASP, cfg: GMConfig, rnd: Random): CRASP = {
    val bLen = cfg.numBoolRules                  // O(1) — avoids List.length O(n)
    val i    = rnd.nextInt(bLen)
    val br   = p.boolRules(i)                    // List.apply(i) — unavoidable for copy

    val newBody = pickAction(rnd, cfg.pReplaceRhs, cfg.pMicroOp) match {
      case 0 => sampleBoolExpr(p, cfg, rnd, i)
      case 1 => boolMicroOp(p, cfg, rnd, i, br.body)
      case _ => boolMicroLeaf(p, cfg, rnd, i, br.body)
    }

    p.copy(boolRules = p.boolRules.updated(i, br.copy(body = newBody)))
  }

  private def sampleBoolExpr(p: CRASP, cfg: GMConfig, rnd: Random, i: Int): BoolExpr =
    rnd.nextInt(4) match {
      case 0 => sampleBoolAtom(p, cfg, rnd, i)
      case 1 => BoolExprNot(sampleBoolAtom(p, cfg, rnd, i))
      case 2 => BoolExprAnd(sampleBoolAtom(p, cfg, rnd, i), sampleBoolAtom(p, cfg, rnd, i))
      case _ => BoolExprOr(sampleBoolAtom(p, cfg, rnd, i), sampleBoolAtom(p, cfg, rnd, i))
    }

  /**
   * Sample a BoolAtom for BoolRule at index i.
   * Uses cfg.boolNamesArr(j) for O(1) name lookup (vs List.apply O(j)).
   */
  private def sampleBoolAtom(p: CRASP, cfg: GMConfig, rnd: Random, i: Int): BoolAtom = {
    val pool = boolAtomPools(
      (if (cfg.hasRunParity) 4 else 0) + (if (i > 0) 2 else 0) + (if (cfg.hasPeriod) 1 else 0)
    )

    pool(rnd.nextInt(pool.length)) match {
      case 0 =>
        BoolAtomInitial(cfg.alphabetVec(rnd.nextInt(cfg.alphabetVec.length)))

      case 1 =>
        // earlier rule only: index j < i  →  O(1) name lookup via cached array
        BoolAtomBoolRule(cfg.boolNamesArr(rnd.nextInt(i)))

      case 2 =>
        BoolAtomPositionPeriod(
          cfg.periods(rnd.nextInt(cfg.periods.length)),
          cfg.offsets(rnd.nextInt(cfg.offsets.length))
        )

      case 3 =>
        BoolAtomComparison(
          rnd.nextBoolean(),
          sampleCountAtom(p, cfg, rnd, -1, i),
          sampleCountAtom(p, cfg, rnd, -1, i)
        )

      case 4 =>
        BoolAtomEqual(
          sampleCountAtom(p, cfg, rnd, -1, i),
          sampleCountAtom(p, cfg, rnd, -1, i)
        )

      case _ =>
        BoolAtomRunParity(cfg.alphabetVec(rnd.nextInt(cfg.alphabetVec.length)))
    }
  }

  private def boolMicroOp(p: CRASP, cfg: GMConfig, rnd: Random, i: Int, e: BoolExpr): BoolExpr =
    e match {
      case BoolExprAnd(l: BoolAtom, r: BoolAtom) => BoolExprOr(l, r)
      case BoolExprOr(l: BoolAtom, r: BoolAtom)  => BoolExprAnd(l, r)
      case BoolExprNot(a: BoolAtom)               => a
      case a: BoolAtom                            => BoolExprNot(a)
      case b: BoolAtomComparison                  => b.copy(strict = !b.strict)
      case b: BoolAtomEqual =>
        BoolAtomComparison(strict = rnd.nextBoolean(), b.left, b.right)
      case _ =>
        sampleBoolExpr(p, cfg, rnd, i)
    }

  private def boolMicroLeaf(p: CRASP, cfg: GMConfig, rnd: Random, i: Int, e: BoolExpr): BoolExpr =
    e match {
      case _: BoolAtom =>
        sampleBoolAtom(p, cfg, rnd, i)

      case BoolExprNot(_: BoolAtom) =>
        if (rnd.nextBoolean()) BoolExprNot(sampleBoolAtom(p, cfg, rnd, i))
        else sampleBoolAtom(p, cfg, rnd, i)

      case BoolExprAnd(l: BoolAtom, r: BoolAtom) =>
        if (rnd.nextBoolean()) BoolExprAnd(sampleBoolAtom(p, cfg, rnd, i), r)
        else BoolExprAnd(l, sampleBoolAtom(p, cfg, rnd, i))

      case BoolExprOr(l: BoolAtom, r: BoolAtom) =>
        if (rnd.nextBoolean()) BoolExprOr(sampleBoolAtom(p, cfg, rnd, i), r)
        else BoolExprOr(l, sampleBoolAtom(p, cfg, rnd, i))

      case b: BoolAtomComparison =>
        if (rnd.nextBoolean()) b.copy(left  = sampleCountAtom(p, cfg, rnd, -1, i))
        else                   b.copy(right = sampleCountAtom(p, cfg, rnd, -1, i))

      case b: BoolAtomEqual =>
        if (rnd.nextBoolean()) b.copy(left  = sampleCountAtom(p, cfg, rnd, -1, i))
        else                   b.copy(right = sampleCountAtom(p, cfg, rnd, -1, i))

      case _ =>
        sampleBoolExpr(p, cfg, rnd, i)
    }

  // ===========================================================================================
  // Count-rule mutation
  // ===========================================================================================

  private def mutateOneCountRule(p: CRASP, cfg: GMConfig, rnd: Random): CRASP = {
    if (p.countRules.isEmpty) return p
    val ci = rnd.nextInt(cfg.numCountRules)      // O(1) — avoids List.length O(n)
    val cr = p.countRules(ci)
    val oi = cfg.numBoolRules - 1                // outIndex, cached once

    val raw = pickAction(rnd, cfg.pReplaceRhs, cfg.pMicroOp) match {
      case 0 => sampleCountExpr(p, cfg, rnd, ci, oi)
      case 1 => countMicroOp(p, cfg, rnd, ci, oi, cr.body)
      case _ => countMicroLeaf(p, cfg, rnd, ci, oi, cr.body)
    }

    val newBody = ensureNoCountAlias(p, cfg, rnd, ci, oi, raw)
    p.copy(countRules = p.countRules.updated(ci, cr.copy(body = newBody)))
  }

  private def sampleCountExpr(p: CRASP, cfg: GMConfig, rnd: Random, ci: Int, oi: Int): CountExpr = {
    val allowCond = cfg.allowConditional && cfg.numBoolRules > 0
    val raw = (if (allowCond) rnd.nextInt(3) else rnd.nextInt(2)) match {
      case 0 =>
        sampleCountAtom(p, cfg, rnd, ci, oi)

      case 1 =>
        pickCountBinOp(rnd,
          sampleCountAtom(p, cfg, rnd, ci, oi),
          sampleCountAtom(p, cfg, rnd, ci, oi))

      case _ =>
        // Guard must not be Out (index oi); pick from [0 .. oi-1], or oi if oi==0
        val bound = math.max(1, math.min(oi, cfg.numBoolRules))
        CountExprConditional(
          cfg.boolNamesArr(rnd.nextInt(bound)),  // O(1) name lookup
          sampleCountAtom(p, cfg, rnd, ci, oi),
          sampleCountAtom(p, cfg, rnd, ci, oi)
        )
    }
    ensureNoCountAlias(p, cfg, rnd, ci, oi, raw)
  }

  /**
   * Sample a CountAtom.
   *
   * @param countRuleIdx  index of the enclosing count rule (-1 = no count context,
   *                      i.e. called from inside a bool rule comparison/equality).
   *                      Using Int sentinel avoids Option[Int] boxing.
   * @param boolBound     exclusive upper bound for bool-rule references inside counting predicates.
   */
  private def sampleCountAtom(
    p: CRASP, cfg: GMConfig, rnd: Random,
    countRuleIdx: Int,   // -1 = no count context (inside a bool rule)
    boolBound: Int       // exclusive: refs only in [0, boolBound)
  ): CountAtom = {
    // When inside a count rule (countRuleIdx >= 0): only earlier rules to avoid cycles.
    // When inside a bool rule context (countRuleIdx == -1): any count rule is safe to reference.
    val hasEarlierCount = if (countRuleIdx >= 0) countRuleIdx > 0
                          else cfg.numCountRules > 0
    val pool = countAtomPools(if (hasEarlierCount) 1 else 0)

    pool(rnd.nextInt(pool.length)) match {
      case 0 =>
        CountAtomConst(sampleConst(rnd, cfg.constRange))

      case 1 =>
        // Bool rule context: reference any count rule.
        // Count rule context: reference only earlier count rules (no cycles).
        val refBound = if (countRuleIdx < 0) cfg.numCountRules else countRuleIdx
        CountAtomCountRule(cfg.countNamesArr(rnd.nextInt(refBound)))

      case _ =>
        val oi    = cfg.numBoolRules - 1
        val bound = math.min(boolBound, oi)
        val pred  = sampleBoolAtomForCounting(cfg, rnd, bound)

        if (!cfg.allowCountingRanges || cfg.rangeBounds.isEmpty) {
          mkCounting(pred, None, None)
        } else if (rnd.nextBoolean()) {
          mkCounting(pred, None, None)
        } else {
          val a = cfg.rangeBounds(rnd.nextInt(cfg.rangeBounds.length))
          val b = cfg.rangeBounds(rnd.nextInt(cfg.rangeBounds.length))
          if (a <= b) mkCounting(pred, Some(a), Some(b))
          else        mkCounting(pred, Some(b), Some(a))
        }
    }
  }

  /**
   * BoolAtom for use inside a counting predicate — no comparisons or equalities.
   * Uses cfg.boolNamesArr for O(1) name lookup.
   */
  private def sampleBoolAtomForCounting(cfg: GMConfig, rnd: Random, boolBound: Int): BoolAtom = {
    val bound      = math.max(0, math.min(boolBound, cfg.numBoolRules))
    val hasBoolRef = bound > 0
    val pool       = countingBoolPools(
      (if (cfg.hasRunParity) 4 else 0) + (if (hasBoolRef) 2 else 0) + (if (cfg.hasPeriod) 1 else 0)
    )

    pool(rnd.nextInt(pool.length)) match {
      case 0 =>
        BoolAtomInitial(cfg.alphabetVec(rnd.nextInt(cfg.alphabetVec.length)))
      case 1 =>
        BoolAtomBoolRule(cfg.boolNamesArr(rnd.nextInt(bound)))  // O(1)
      case 2 =>
        BoolAtomPositionPeriod(
          cfg.periods(rnd.nextInt(cfg.periods.length)),
          cfg.offsets(rnd.nextInt(cfg.offsets.length))
        )
      case _ =>
        BoolAtomRunParity(cfg.alphabetVec(rnd.nextInt(cfg.alphabetVec.length)))
    }
  }

  private def countMicroOp(p: CRASP, cfg: GMConfig, rnd: Random, ci: Int, oi: Int, e: CountExpr): CountExpr = {
    val raw: CountExpr = e match {
      case CountExprAdd(x: CountAtom, y: CountAtom)      => pickCountBinOp(rnd, x, y)
      case CountExprSubtract(x: CountAtom, y: CountAtom) => pickCountBinOp(rnd, x, y)
      case CountExprMin(x: CountAtom, y: CountAtom)      => pickCountBinOp(rnd, x, y)
      case CountExprMax(x: CountAtom, y: CountAtom)      => pickCountBinOp(rnd, x, y)

      case CountExprConditional(g, t: CountAtom, f: CountAtom) =>
        if (rnd.nextBoolean()) CountExprConditional(g, f, t)
        else {
          val bound = math.max(1, math.min(oi, cfg.numBoolRules))
          CountExprConditional(cfg.boolNamesArr(rnd.nextInt(bound)), t, f)  // O(1)
        }

      case a: CountAtom =>
        if (rnd.nextBoolean()) pickCountBinOp(rnd, a, sampleCountAtom(p, cfg, rnd, ci, oi))
        else sampleCountAtom(p, cfg, rnd, ci, oi)

      case _ =>
        sampleCountExpr(p, cfg, rnd, ci, oi)
    }
    ensureNoCountAlias(p, cfg, rnd, ci, oi, raw)
  }

  private def countMicroLeaf(p: CRASP, cfg: GMConfig, rnd: Random, ci: Int, oi: Int, e: CountExpr): CountExpr = {
    val raw: CountExpr = e match {
      case _: CountAtom =>
        sampleCountAtom(p, cfg, rnd, ci, oi)

      case CountExprAdd(x: CountAtom, y: CountAtom) =>
        if (rnd.nextBoolean()) CountExprAdd(sampleCountAtom(p, cfg, rnd, ci, oi), y)
        else                   CountExprAdd(x, sampleCountAtom(p, cfg, rnd, ci, oi))

      case CountExprSubtract(x: CountAtom, y: CountAtom) =>
        if (rnd.nextBoolean()) CountExprSubtract(sampleCountAtom(p, cfg, rnd, ci, oi), y)
        else                   CountExprSubtract(x, sampleCountAtom(p, cfg, rnd, ci, oi))

      case CountExprMin(x: CountAtom, y: CountAtom) =>
        if (rnd.nextBoolean()) CountExprMin(sampleCountAtom(p, cfg, rnd, ci, oi), y)
        else                   CountExprMin(x, sampleCountAtom(p, cfg, rnd, ci, oi))

      case CountExprMax(x: CountAtom, y: CountAtom) =>
        if (rnd.nextBoolean()) CountExprMax(sampleCountAtom(p, cfg, rnd, ci, oi), y)
        else                   CountExprMax(x, sampleCountAtom(p, cfg, rnd, ci, oi))

      case CountExprConditional(g, t: CountAtom, f: CountAtom) =>
        rnd.nextInt(3) match {
          case 0 =>
            val bound = math.max(1, math.min(oi, cfg.numBoolRules))
            CountExprConditional(cfg.boolNamesArr(rnd.nextInt(bound)), t, f)  // O(1)
          case 1 => CountExprConditional(g, sampleCountAtom(p, cfg, rnd, ci, oi), f)
          case _ => CountExprConditional(g, t, sampleCountAtom(p, cfg, rnd, ci, oi))
        }

      case c: CountAtomCounting =>
        val pred = sampleBoolAtomForCounting(cfg, rnd, oi)
        if (!cfg.allowCountingRanges) mkCounting(pred, None, None)
        else if (rnd.nextBoolean())   mkCounting(pred, c.rangeStart, c.rangeEnd)
        else {
          val rs0 = if (cfg.rangeBounds.isEmpty) None else Some(cfg.rangeBounds(rnd.nextInt(cfg.rangeBounds.length)))
          val re0 = if (cfg.rangeBounds.isEmpty) None else Some(cfg.rangeBounds(rnd.nextInt(cfg.rangeBounds.length)))
          (rs0, re0) match {
            case (Some(a), Some(b)) => if (a <= b) mkCounting(pred, rs0, re0) else mkCounting(pred, re0, rs0)
            case _                  => mkCounting(pred, rs0, re0)
          }
        }

      case _ =>
        sampleCountExpr(p, cfg, rnd, ci, oi)
    }
    ensureNoCountAlias(p, cfg, rnd, ci, oi, raw)
  }

  // ===========================================================================================
  // Prune unreachable rules (optional, off by default)
  // NOTE: when enabled, boolNamesArr/countNamesArr in cfg become out-of-sync with p —
  // cfg.numBoolRules/numCountRules must not be used for rule indexing in that case.
  // ===========================================================================================

  private def prune(p: CRASP): CRASP = {
    if (p.boolRules.isEmpty) return p
    val outName = p.boolRules.last.name

    val boolByName  = p.boolRules.map(r => r.name -> r).toMap
    val countByName = p.countRules.map(r => r.name -> r).toMap

    val seenBool  = collection.mutable.LinkedHashSet.empty[String]
    val seenCount = collection.mutable.LinkedHashSet.empty[String]

    def visitBool(e: BoolExpr): Unit = e match {
      case BoolExprAnd(l, r)            => visitBool(l); visitBool(r)
      case BoolExprOr(l, r)             => visitBool(l); visitBool(r)
      case BoolExprNot(x)               => visitBool(x)
      case BoolAtomBoolRule(n)          => if (seenBool.add(n)) boolByName.get(n).foreach(br => visitBool(br.body))
      case BoolAtomComparison(_, l, r)  => visitCount(l); visitCount(r)
      case BoolAtomEqual(l, r)          => visitCount(l); visitCount(r)
      case _: BoolAtomInitial           => ()
      case _: BoolAtomPositionPeriod    => ()
      case _: BoolAtomRunParity         => ()
    }

    def visitCount(e: CountExpr): Unit = e match {
      case a: CountAtom                      => visitCountAtom(a)
      case CountExprAdd(l, r)                => visitCount(l); visitCount(r)
      case CountExprSubtract(l, r)           => visitCount(l); visitCount(r)
      case CountExprMin(l, r)                => visitCount(l); visitCount(r)
      case CountExprMax(l, r)                => visitCount(l); visitCount(r)
      case CountExprConditional(g, t, el)    =>
        if (seenBool.add(g)) boolByName.get(g).foreach(br => visitBool(br.body))
        visitCount(t); visitCount(el)
    }

    def visitCountAtom(a: CountAtom): Unit = a match {
      case CountAtomConst(_)             => ()
      case CountAtomCountRule(n)         => if (seenCount.add(n)) countByName.get(n).foreach(cr => visitCount(cr.body))
      case CountAtomCounting(be, _, _)   => visitBool(be)
    }

    seenBool.add(outName)
    visitBool(p.boolRules.last.body)

    val keptBool  = p.boolRules.filter(br => seenBool.contains(br.name))
    val keptCount = p.countRules.filter(cr => seenCount.contains(cr.name))
    val (outs, rest) = keptBool.partition(_.name == outName)
    val outRule = outs.lastOption.getOrElse(p.boolRules.last)
    p.copy(boolRules = rest.filter(_.name != outName) :+ outRule, countRules = keptCount)
  }

  // ===========================================================================================
  // Alias elimination
  // ===========================================================================================

  private def ensureNoCountAlias(
    p: CRASP, cfg: GMConfig, rnd: Random,
    ci: Int, oi: Int, e: CountExpr
  ): CountExpr = e match {
    case alias: CountAtomCountRule =>
      val k = CountAtomConst(sampleConst(rnd, cfg.constRange))
      rnd.nextInt(4) match {
        case 0 => k
        case 1 => mkCounting(sampleBoolAtomForCounting(cfg, rnd, oi), None, None)
        case 2 => CountExprAdd(alias, k)
        case _ => CountExprMin(alias, k)
      }
    case other => other
  }

  // ===========================================================================================
  // Helpers
  // ===========================================================================================

  private def pickCountBinOp(rnd: Random, x: CountAtom, y: CountAtom): CountExpr =
    rnd.nextInt(4) match {
      case 0 => CountExprAdd(x, y)
      case 1 => CountExprSubtract(x, y)
      case 2 => CountExprMin(x, y)
      case _ => CountExprMax(x, y)
    }

  private def mkCounting(pred: BoolExpr, rs: Option[Int], re: Option[Int]): CountAtomCounting =
    (rs, re) match {
      case (Some(a), Some(b)) =>
        if (a <= b) CountAtomCounting(pred, Some(a), Some(b))
        else        CountAtomCounting(pred, Some(b), Some(a))
      case _ =>
        CountAtomCounting(pred, rs, re)
    }

  // Returns 0=replace, 1=microOp, 2=microLeaf
  private def pickAction(rnd: Random, pReplace: Double, pOp: Double): Int = {
    val x = rnd.nextDouble()
    if (x < pReplace)        0
    else if (x < pReplace + pOp) 1
    else                     2
  }

  private def sampleConst(rnd: Random, range: Int): Int =
    if (range <= 0) 0 else rnd.nextInt(range + 1)
}
