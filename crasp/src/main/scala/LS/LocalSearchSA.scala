// File: src/main/scala/LS/LocalSearchSA.scala
package LS

import Crasp.*
import scala.util.Random

/**
 * Simulated Annealing local search for CRASP programs.
 *
 * New objective focus:
 *   - Dominant term: misclassified (huge weight)
 *   - Secondary: number of BoolRules / CountRules
 *   - Tertiary: AST size / unreachable rules
 *
 * Optional behavior:
 *   - After reaching perfect fit (mis=0), you can:
 *       (a) keep optimizing for smaller programs
 *       (b) forbid any move that breaks perfect fit (hardKeepPerfect)
 */
object LocalSearchSA {

  // ============================================================================================
  // Config
  // ============================================================================================
  final case class SAConfig(
    seed: Int = 0,

    // Budget
    iters: Int = 20000,
    // Hard ceiling applied after adaptSA's formula — lets callers (e.g. RunPL --iters N)
    // cap wall-time without touching any other SA knob.  Int.MaxValue = no cap.
    hardIterCap: Int = Int.MaxValue,

    // Temperature schedule
    startTemp: Double = 1.0,
    cooling: Double = 0.9995,
    reheatEvery: Int = 4000,
    reheatFactor: Double = 1.2,

    // Output rule name
    outName: String = "Out",

    // -------------------------
    // Main objective weights
    // -------------------------
    misPenalty: Double = 1_000_000.0,     // dominates everything
    boolRulePenalty: Double = 50.0,       // penalize number of BoolRules
    countRulePenalty: Double = 30.0,      // penalize number of CountRules
    sizePenalty: Double = 0.1,            // penalize total AST nodes (mild)
    unreachablePenalty: Double = 200.0,   // penalize unreachable rules (strong)

    // -------------------------
    // (Optional) extra stats (default 0 => no effect)
    // Keep them for logging / experiments, but they don't matter unless you set >0.
    // -------------------------
    periodPenalty: Double = 0.0,
    countingPenalty: Double = 0.0,
    localCountingPenalty: Double = 0.0,
    conditionalPenalty: Double = 0.0,
    depthPenalty: Double = 0.0,

    // -------------------------
    // After-perfect behavior
    // -------------------------
    continueAfterPerfect: Boolean = true,   // if false, stop immediately once mis=0
    hardKeepPerfect: Boolean = true,        // if true, once mis=0 is achieved, reject any move with mis>0
    usePerfectWeights: Boolean = true,      // if true, switch to *_Perfect weights once best mis=0

    // Perfect-phase weights (only used if usePerfectWeights=true and best mis=0)
    boolRulePenaltyPerfect: Double = 80.0,
    countRulePenaltyPerfect: Double = 50.0,
    sizePenaltyPerfect: Double = 0.2,
    unreachablePenaltyPerfect: Double = 300.0,

    // Early-stop when perfect + stalled
    stallLimit: Int = 5000,
    tempFloor: Double = 1e-6,

    // Logging
    logEvery: Int = 200
  )

  // ============================================================================================
  // AST statistics
  // ============================================================================================
  private final case class AstStats(
    size: Int,
    periodAtoms: Int,
    countingAtoms: Int,
    localCountingAtoms: Int,
    conditionals: Int,
    maxDepth: Int
  )

  private def max(a: Int, b: Int): Int = if (a >= b) a else b

  private def statsBool(e: BoolExpr, depth: Int): AstStats = e match {
    case BoolExprAnd(l, r) =>
      val sl = statsBool(l, depth + 1)
      val sr = statsBool(r, depth + 1)
      AstStats(
        size = 1 + sl.size + sr.size,
        periodAtoms = sl.periodAtoms + sr.periodAtoms,
        countingAtoms = sl.countingAtoms + sr.countingAtoms,
        localCountingAtoms = sl.localCountingAtoms + sr.localCountingAtoms,
        conditionals = sl.conditionals + sr.conditionals,
        maxDepth = max(sl.maxDepth, sr.maxDepth)
      )

    case BoolExprOr(l, r) =>
      val sl = statsBool(l, depth + 1)
      val sr = statsBool(r, depth + 1)
      AstStats(
        size = 1 + sl.size + sr.size,
        periodAtoms = sl.periodAtoms + sr.periodAtoms,
        countingAtoms = sl.countingAtoms + sr.countingAtoms,
        localCountingAtoms = sl.localCountingAtoms + sr.localCountingAtoms,
        conditionals = sl.conditionals + sr.conditionals,
        maxDepth = max(sl.maxDepth, sr.maxDepth)
      )

    case BoolExprNot(x) =>
      val sx = statsBool(x, depth + 1)
      AstStats(
        size = 1 + sx.size,
        periodAtoms = sx.periodAtoms,
        countingAtoms = sx.countingAtoms,
        localCountingAtoms = sx.localCountingAtoms,
        conditionals = sx.conditionals,
        maxDepth = sx.maxDepth
      )

    case a: BoolAtom =>
      a match {
        case _: BoolAtomPositionPeriod =>
          AstStats(1, 1, 0, 0, 0, depth)

        case BoolAtomComparison(_, l, r) =>
          val sl = statsCount(l, depth + 1)
          val sr = statsCount(r, depth + 1)
          AstStats(
            size = 1 + sl.size + sr.size,
            periodAtoms = sl.periodAtoms + sr.periodAtoms,
            countingAtoms = sl.countingAtoms + sr.countingAtoms,
            localCountingAtoms = sl.localCountingAtoms + sr.localCountingAtoms,
            conditionals = sl.conditionals + sr.conditionals,
            maxDepth = max(sl.maxDepth, sr.maxDepth)
          )

        case BoolAtomEqual(l, r) =>
          val sl = statsCount(l, depth + 1)
          val sr = statsCount(r, depth + 1)
          AstStats(
            size = 1 + sl.size + sr.size,
            periodAtoms = sl.periodAtoms + sr.periodAtoms,
            countingAtoms = sl.countingAtoms + sr.countingAtoms,
            localCountingAtoms = sl.localCountingAtoms + sr.localCountingAtoms,
            conditionals = sl.conditionals + sr.conditionals,
            maxDepth = max(sl.maxDepth, sr.maxDepth)
          )

        case _ =>
          AstStats(1, 0, 0, 0, 0, depth)
      }
  }

  private def statsCount(e: CountExpr, depth: Int): AstStats = e match {
    case CountExprAdd(l, r) =>
      val sl = statsCount(l, depth + 1)
      val sr = statsCount(r, depth + 1)
      AstStats(
        size = 1 + sl.size + sr.size,
        periodAtoms = sl.periodAtoms + sr.periodAtoms,
        countingAtoms = sl.countingAtoms + sr.countingAtoms,
        localCountingAtoms = sl.localCountingAtoms + sr.localCountingAtoms,
        conditionals = sl.conditionals + sr.conditionals,
        maxDepth = max(sl.maxDepth, sr.maxDepth)
      )

    case CountExprSubtract(l, r) =>
      val sl = statsCount(l, depth + 1)
      val sr = statsCount(r, depth + 1)
      AstStats(
        size = 1 + sl.size + sr.size,
        periodAtoms = sl.periodAtoms + sr.periodAtoms,
        countingAtoms = sl.countingAtoms + sr.countingAtoms,
        localCountingAtoms = sl.localCountingAtoms + sr.localCountingAtoms,
        conditionals = sl.conditionals + sr.conditionals,
        maxDepth = max(sl.maxDepth, sr.maxDepth)
      )

    case CountExprMin(l, r) =>
      val sl = statsCount(l, depth + 1)
      val sr = statsCount(r, depth + 1)
      AstStats(
        size = 1 + sl.size + sr.size,
        periodAtoms = sl.periodAtoms + sr.periodAtoms,
        countingAtoms = sl.countingAtoms + sr.countingAtoms,
        localCountingAtoms = sl.localCountingAtoms + sr.localCountingAtoms,
        conditionals = sl.conditionals + sr.conditionals,
        maxDepth = max(sl.maxDepth, sr.maxDepth)
      )

    case CountExprMax(l, r) =>
      val sl = statsCount(l, depth + 1)
      val sr = statsCount(r, depth + 1)
      AstStats(
        size = 1 + sl.size + sr.size,
        periodAtoms = sl.periodAtoms + sr.periodAtoms,
        countingAtoms = sl.countingAtoms + sr.countingAtoms,
        localCountingAtoms = sl.localCountingAtoms + sr.localCountingAtoms,
        conditionals = sl.conditionals + sr.conditionals,
        maxDepth = max(sl.maxDepth, sr.maxDepth)
      )

    case CountExprConditional(_, t, el) =>
      val st = statsCount(t, depth + 1)
      val se = statsCount(el, depth + 1)
      AstStats(
        size = 1 + st.size + se.size,
        periodAtoms = st.periodAtoms + se.periodAtoms,
        countingAtoms = st.countingAtoms + se.countingAtoms,
        localCountingAtoms = st.localCountingAtoms + se.localCountingAtoms,
        conditionals = 1 + st.conditionals + se.conditionals,
        maxDepth = max(st.maxDepth, se.maxDepth)
      )

    case CountAtomCounting(b, rs, _) =>
      val sb = statsBool(b, depth + 1)
      val isLocal = if (rs.isDefined) 1 else 0
      AstStats(
        size = 1 + sb.size,
        periodAtoms = sb.periodAtoms,
        countingAtoms = 1 + sb.countingAtoms,
        localCountingAtoms = isLocal + sb.localCountingAtoms,
        conditionals = sb.conditionals,
        maxDepth = sb.maxDepth
      )

    case _: CountAtom =>
      AstStats(1, 0, 0, 0, 0, depth)
  }

  private def programStats(p: CRASP): AstStats = {
    val sb = p.boolRules.foldLeft(AstStats(0, 0, 0, 0, 0, 0)) { (acc, r) =>
      val s = statsBool(r.body, depth = 1)
      AstStats(
        size = acc.size + s.size,
        periodAtoms = acc.periodAtoms + s.periodAtoms,
        countingAtoms = acc.countingAtoms + s.countingAtoms,
        localCountingAtoms = acc.localCountingAtoms + s.localCountingAtoms,
        conditionals = acc.conditionals + s.conditionals,
        maxDepth = max(acc.maxDepth, s.maxDepth)
      )
    }
    val sc = p.countRules.foldLeft(AstStats(0, 0, 0, 0, 0, 0)) { (acc, r) =>
      val s = statsCount(r.body, depth = 1)
      AstStats(
        size = acc.size + s.size,
        periodAtoms = acc.periodAtoms + s.periodAtoms,
        countingAtoms = acc.countingAtoms + s.countingAtoms,
        localCountingAtoms = acc.localCountingAtoms + s.localCountingAtoms,
        conditionals = acc.conditionals + s.conditionals,
        maxDepth = max(acc.maxDepth, s.maxDepth)
      )
    }
    AstStats(
      size = sb.size + sc.size,
      periodAtoms = sb.periodAtoms + sc.periodAtoms,
      countingAtoms = sb.countingAtoms + sc.countingAtoms,
      localCountingAtoms = sb.localCountingAtoms + sc.localCountingAtoms,
      conditionals = sb.conditionals + sc.conditionals,
      maxDepth = max(sb.maxDepth, sc.maxDepth)
    )
  }

  // ============================================================================================
  // Reachability (to penalize garbage rules)
  // ============================================================================================
  private final case class ReachInfo(reachableBool: Set[String], reachableCount: Set[String]) {
    def unreachableTotal(p: CRASP): Int =
      (p.boolRules.count(br => !reachableBool.contains(br.name)) +
        p.countRules.count(cr => !reachableCount.contains(cr.name)))
  }

  /** Compute which rules are reachable from the last BoolRule (assumed Out) by following references. */
  private def reachabilityFromOut(p: CRASP): ReachInfo = {
    if (p.boolRules.isEmpty) return ReachInfo(Set.empty, Set.empty)

    val outName = p.boolRules.last.name

    val boolByName: Map[String, BoolRule] =
      p.boolRules.map(r => r.name -> r).toMap
    val countByName: Map[String, CountRule] =
      p.countRules.map(r => r.name -> r).toMap

    val seenBool = collection.mutable.LinkedHashSet.empty[String]
    val seenCount = collection.mutable.LinkedHashSet.empty[String]

    def visitBool(e: BoolExpr): Unit = e match {
      case BoolExprAnd(l, r) => visitBool(l); visitBool(r)
      case BoolExprOr(l, r)  => visitBool(l); visitBool(r)
      case BoolExprNot(x)    => visitBool(x)

      case BoolAtomBoolRule(n) =>
        if (seenBool.add(n)) boolByName.get(n).foreach(br => visitBool(br.body))

      case BoolAtomComparison(_, l, r) =>
        visitCount(l); visitCount(r)

      case BoolAtomEqual(l, r) =>
        visitCount(l); visitCount(r)

      case _: BoolAtomInitial        => ()
      case _: BoolAtomPositionPeriod => ()
      case _: BoolAtomRunParity      => ()
    }

    def visitCount(e: CountExpr): Unit = e match {
      case a: CountAtom => visitCountAtom(a)
      case CountExprAdd(l, r)      => visitCount(l); visitCount(r)
      case CountExprSubtract(l, r) => visitCount(l); visitCount(r)
      case CountExprMin(l, r)      => visitCount(l); visitCount(r)
      case CountExprMax(l, r)      => visitCount(l); visitCount(r)
      case CountExprConditional(g, t, el) =>
        if (seenBool.add(g)) boolByName.get(g).foreach(br => visitBool(br.body))
        visitCount(t); visitCount(el)
    }

    def visitCountAtom(a: CountAtom): Unit = a match {
      case CountAtomConst(_) => ()
      case CountAtomCountRule(n) =>
        if (seenCount.add(n)) countByName.get(n).foreach(cr => visitCount(cr.body))
      case CountAtomCounting(be, _, _) =>
        visitBool(be)
    }

    // start from Out
    seenBool.add(outName)
    visitBool(p.boolRules.last.body)

    ReachInfo(seenBool.toSet, seenCount.toSet)
  }

  // ============================================================================================
  // Score / objective
  // ============================================================================================
  final case class Score(
    misclassified: Int,
    numBoolRules: Int,
    numCountRules: Int,
    unreachableRules: Int,

    // extra structure stats (optional penalties)
    size: Int,
    periodAtoms: Int,
    countingAtoms: Int,
    localCountingAtoms: Int,
    conditionals: Int,
    maxDepth: Int
  ) {
    def total(sa: SAConfig, perfectPhase: Boolean): Double = {
      val (bPen, cPen, sPen, uPen) =
        if (perfectPhase && sa.usePerfectWeights)
          (sa.boolRulePenaltyPerfect, sa.countRulePenaltyPerfect, sa.sizePenaltyPerfect, sa.unreachablePenaltyPerfect)
        else
          (sa.boolRulePenalty, sa.countRulePenalty, sa.sizePenalty, sa.unreachablePenalty)

      misclassified.toDouble * sa.misPenalty +
        bPen * numBoolRules.toDouble +
        cPen * numCountRules.toDouble +
        uPen * unreachableRules.toDouble +
        sPen * size.toDouble +
        // Optional extras (default 0)
        sa.periodPenalty * periodAtoms.toDouble +
        sa.countingPenalty * countingAtoms.toDouble +
        sa.localCountingPenalty * localCountingAtoms.toDouble +
        sa.conditionalPenalty * conditionals.toDouble +
        sa.depthPenalty * maxDepth.toDouble
    }
  }

  /**
   * Cheap misclassification count — skips AST stats (programStats / reachabilityFromOut).
   * Used as a fast gate in hardKeepPerfect mode before paying for the full score() call.
   */
  private def countMisclassified(
    p: CRASP,
    examples: List[(Vector[String], Boolean)],
    outName: String
  ): Int = {
    try {
      val ev = new CRASPEvaluator(p)
      var bad = 0
      var j = 0
      while (j < examples.length) {
        val (w, y) = examples(j)
        if (ev.classifyWith(outName, w) != y) bad += 1
        j += 1
      }
      bad
    } catch {
      case _: IllegalArgumentException => examples.length + 10_000
    }
  }

  /** Score = misclassified + structure stats. NEVER throws: invalid programs get huge misclassified. */
  private def score(
    p: CRASP,
    examples: List[(Vector[String], Boolean)],
    outName: String
  ): Score = {
    val st = programStats(p)
    val reach = reachabilityFromOut(p)
    val unreachable = reach.unreachableTotal(p)

    try {
      val ev = new CRASPEvaluator(p)
      var bad = 0
      var j = 0
      while (j < examples.length) {
        val (w, y) = examples(j)
        val pred = ev.classifyWith(outName, w)
        if (pred != y) bad += 1
        j += 1
      }
      Score(
        misclassified = bad,
        numBoolRules = p.boolRules.length,
        numCountRules = p.countRules.length,
        unreachableRules = unreachable,
        size = st.size,
        periodAtoms = st.periodAtoms,
        countingAtoms = st.countingAtoms,
        localCountingAtoms = st.localCountingAtoms,
        conditionals = st.conditionals,
        maxDepth = st.maxDepth
      )
    } catch {
      case _: IllegalArgumentException =>
        // treat invalid programs as extremely bad
        Score(
          misclassified = examples.length + 10_000,
          numBoolRules = p.boolRules.length,
          numCountRules = p.countRules.length,
          unreachableRules = unreachable + 10_000,
          size = st.size + 10_000,
          periodAtoms = st.periodAtoms,
          countingAtoms = st.countingAtoms,
          localCountingAtoms = st.localCountingAtoms,
          conditionals = st.conditionals,
          maxDepth = st.maxDepth + 50
        )
    }
  }

  /** Compare scores: primary misclassified; then total objective; then smaller size as tie-breaker. */
  private def better(a: Score, b: Score, sa: SAConfig, perfectPhase: Boolean): Boolean = {
    if (a.misclassified < b.misclassified) true
    else if (a.misclassified > b.misclassified) false
    else {
      val ta = a.total(sa, perfectPhase)
      val tb = b.total(sa, perfectPhase)
      if (ta < tb) true
      else if (ta > tb) false
      else a.size < b.size
    }
  }

  // ============================================================================================
  // SA Search
  // ============================================================================================
  def search(
    init: CRASP,
    examples: List[(Vector[String], Boolean)],
    mutCfg: GMConfig,
    sa: SAConfig
  ): CRASP = {

    val rnd = new Random(sa.seed)

    var cur = init
    var curScore = score(cur, examples, sa.outName)

    var best = cur
    var bestScore = curScore

    var temp = sa.startTemp

    // Track stagnation once we are perfect
    var lastImproveIter = 0
    // Separate stall counter for perfect phase: only resets when rule COUNT drops,
    // not on every tiny objective tweak that resets lastImproveIter.
    // If the initial program is already perfect, record it immediately so stall
    // detection fires correctly (otherwise perfectEnteredIter stays at MaxValue
    // and the SA runs the full budget without ever triggering the stall check).
    var perfectEnteredIter = if (bestScore.misclassified == 0) 0 else Int.MaxValue
    var lastStructuralImproveIter = 0  // last iter where (numBool+numCount) decreased

    def perfectPhase: Boolean = bestScore.misclassified == 0

    def accept(delta: Double): Boolean = {
      if (delta <= 0.0) true
      else {
        val p = math.exp(-delta / math.max(1e-12, temp))
        rnd.nextDouble() < p
      }
    }

    def shouldContinue(i: Int): Boolean = {
      if (bestScore.misclassified > 0) true
      else {
        if (!sa.continueAfterPerfect) false
        else {
          // In perfect phase we only care about structural improvement (fewer rules).
          // Stop if no rule-count reduction in the last stallLimit iters since entering
          // perfect phase. This avoids the original bug where tiny objective tweaks
          // kept resetting lastImproveIter and reheating prevented cold from firing.
          val ref = math.max(perfectEnteredIter, lastStructuralImproveIter)
          val stalled = (i - ref) >= sa.stallLimit
          !stalled
        }
      }
    }

    // Cache the current program's objective total to avoid recomputing it every iteration.
    // Must be invalidated when perfectPhase transitions (weights change) or cur changes.
    var cachedCurTotal = curScore.total(sa, perfectPhase)
    var lastPP         = perfectPhase

    var i = 0
    while (i < sa.iters && shouldContinue(i)) {

      val pp = perfectPhase

      // perfectPhase just flipped false->true: recompute cached total under new weights
      if (pp != lastPP) {
        cachedCurTotal = curScore.total(sa, pp)
        lastPP = pp
      }

      val nxt = GenericMutator.mutate(cur, mutCfg, rnd)

      // Fast gate for hardKeepPerfect: count misclassified cheaply (no AST stats) and
      // skip the full score() call if the mutation clearly breaks the perfect solution.
      val skipNxt = sa.hardKeepPerfect && pp && (countMisclassified(nxt, examples, sa.outName) > 0)

      if (!skipNxt) {
        val nxtScore = score(nxt, examples, sa.outName)
        val nxtTotal = nxtScore.total(sa, pp)
        val d = nxtTotal - cachedCurTotal

        if (accept(d)) {
          cur = nxt
          curScore = nxtScore
          cachedCurTotal = nxtTotal
        }

        // Update best
        if (better(curScore, bestScore, sa, pp)) {
          val prevRules = bestScore.numBoolRules + bestScore.numCountRules
          best = cur
          bestScore = curScore
          lastImproveIter = i
          // Track first entry into perfect phase
          if (bestScore.misclassified == 0 && perfectEnteredIter == Int.MaxValue)
            perfectEnteredIter = i
          // Track structural improvement (fewer total rules)
          if (bestScore.numBoolRules + bestScore.numCountRules < prevRules)
            lastStructuralImproveIter = i

          if (sa.logEvery > 0 && (i % sa.logEvery == 0)) {
            val tot = bestScore.total(sa, pp)
            println(
              s"[SA] iter=$i " +
                s"curBad=${curScore.misclassified} bestBad=${bestScore.misclassified} " +
                s"total=${"%.4f".format(tot)} " +
                s"|B|=${bestScore.numBoolRules} |C|=${bestScore.numCountRules} " +
                s"unreach=${bestScore.unreachableRules} size=${bestScore.size} " +
                s"temp=${"%.6f".format(temp)}"
            )
          }
        }

      }

      // Unconditional progress print every 1000 iters showing best accuracy so far
      if (i > 0 && i % 1000 == 0) {
        val n = examples.size
        val acc = if (n == 0) 1.0 else (n - bestScore.misclassified).toDouble / n * 100.0
        println(f"[SA] iter=$i/${sa.iters} bestAcc=$acc%.2f%% bestBad=${bestScore.misclassified}/$n temp=${"%.4f".format(temp)}")
      }

      // Cooling + optional reheat
      temp *= sa.cooling
      if (sa.reheatEvery > 0 && (i + 1) % sa.reheatEvery == 0) {
        temp *= sa.reheatFactor
      }

      i += 1
    }

    best
  }
}
