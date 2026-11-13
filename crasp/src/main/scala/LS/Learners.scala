// File: src/main/scala/LS/Learners.scala
package LS

import Crasp._
import scala.util.Random

// ==============================================================================================
// Result type
// ==============================================================================================
final case class LearningResult(program: CRASP, accuracy: Double)

// ==============================================================================================
// Universal Learner (SA-based, uses LocalSearchSA + GenericMutator)
// ==============================================================================================

final case class UniversalLearnerConfig(
  numBoolVars: Int = 4,
  numCountVars: Int = 3,
  sa: LocalSearchSA.SAConfig = LocalSearchSA.SAConfig(),
  constRange: Int = 5, // used by mutator random constants
  allowRunParity: Boolean = true // disabled by adapters targeting the text/Lustre grammar
)

object UniversalLearner {

  /** Convert compact string like "lrlr" or spaced "l r l r" to Vector tokens. */
  private def strToWord(s: String): Vector[String] = {
    val t = s.trim
    if (t.isEmpty) Vector.empty
    else if (t.contains(" ")) t.split("\\s+").toVector.filter(_.nonEmpty)
    else t.toVector.map(_.toString)
  }

  private def accuracy(p: CRASP, pos: List[String], neg: List[String], outName: String): Double = {
    val ev = new CRASPEvaluator(p)
    val samples = pos.map(w => strToWord(w) -> true) ::: neg.map(w => strToWord(w) -> false)
    val correct = samples.count { case (w, y) => ev.classifyWith(outName, w) == y }
    if (samples.isEmpty) 0.0 else correct.toDouble / samples.size.toDouble
  }

  /**
   * Learn one program with fixed (numBoolVars, numCountVars) using SA.
   *
   * - initProgram: if provided, SA starts from it
   * - alphabetOverride: force alphabet (useful when samples empty)
   * - outName: candidate output rule name
   */
  def learn(
    positives: List[String],
    negatives: List[String],
    config: UniversalLearnerConfig = UniversalLearnerConfig(),
    seed: Int = 0,
    outName: String = "Out",
    initProgram: Option[CRASP] = None,
    alphabetOverride: Option[Set[String]] = None
  ): LearningResult = {

    val rnd = new Random(seed)

    // 1) Alphabet selection priority:
    //    alphabetOverride > initProgram.alphabet > inferred from samples > fallback
    val inferredAlphabet: Set[String] = {
      val allChars = (positives ++ negatives).mkString.toVector.map(_.toString).toSet
      if (allChars.nonEmpty) allChars else Set.empty[String]
    }

    val alphabet: Set[String] =
      alphabetOverride
        .orElse(initProgram.map(_.alphabet).filter(_.nonEmpty))
        .orElse(if (inferredAlphabet.nonEmpty) Some(inferredAlphabet) else None)
        .getOrElse(Set("a", "b"))

    // 2) Rule names (IMPORTANT: UNAME must start uppercase for your verifier)
    val boolNames: Vector[String] =
      (1 to config.numBoolVars).map(i => s"B$i").toVector :+ outName

    val countNames: Vector[String] =
      (1 to config.numCountVars).map(i => s"C$i").toVector

    // 3) Mutator config (kept compatible with your existing Learners.scala call sites)
    val mutatorConfig = GMConfig(
      alphabet = alphabet,
      constRange = config.constRange,
      outName = outName,
      mutableBoolNames = boolNames.toSet,
      mutableCountNames = countNames.toSet,
      constCountNames = Set("C0", "C1"),
      allowRunParity = config.allowRunParity,
      // Pre-computed name arrays for O(1) index->name lookup in the mutator
      boolNamesArr  = boolNames.toArray,
      countNamesArr = countNames.toArray
    )

    // 4) Default init program (only used if initProgram=None)
    def defaultInit(): CRASP = {
      val chars = alphabet.toVector.sorted

      def randCharAtom(): BoolExpr =
        if (chars.nonEmpty) BoolAtomInitial(chars(rnd.nextInt(chars.length)))
        else BoolAtomPositionPeriod(2, 0)

      // Bool rules: B1..Bk
      val initBool: List[BoolRule] =
        (1 to config.numBoolVars).map(i => BoolRule(s"B$i", randCharAtom())).toList

      // Count rules: C1..Cm (simple chain)
      val initCount: List[CountRule] =
        (1 to config.numCountVars).toList.map { i =>
          val body: CountExpr =
            if (i == 1) {
              if (config.numBoolVars >= 1) CountAtomCounting(BoolAtomBoolRule("B1"), None, None)
              else CountAtomConst(0)
            } else {
              val prev = CountAtomCountRule(s"C${i - 1}")
              val add =
                if (config.numBoolVars >= 1) CountAtomCounting(BoolAtomBoolRule("B1"), None, None)
                else CountAtomConst(0)
              CountExprAdd(prev, add)
            }
          CountRule(s"C$i", body)
        }

      // Out references B1
      val outLit: BoolExpr = {
        val atom: BoolExpr =
          if (config.numBoolVars >= 1) BoolAtomBoolRule("B1")
          else BoolAtomPositionPeriod(2, 0)
        if (rnd.nextBoolean()) atom else BoolExprNot(atom)
      }

      CRASP(
        alphabet = alphabet,
        boolRules = initBool :+ BoolRule(outName, outLit),
        countRules = initCount
      )
    }

    val init: CRASP = initProgram.getOrElse(defaultInit())

    // 5) Build examples once (Vector tokens)
    val examples: List[(Vector[String], Boolean)] =
      positives.map(s => strToWord(s) -> true) ::: negatives.map(s => strToWord(s) -> false)

    // 6) Run SA
    val best = LocalSearchSA.search(
      init = init,
      examples = examples,
      mutCfg = mutatorConfig,
      sa = config.sa.copy(seed = seed, outName = outName)
    )

    LearningResult(best, accuracy(best, positives, negatives, outName))
  }

  // ============================================================================================
  // Auto search wrappers (kept from your code)
  // ============================================================================================

  def learnAuto(
    positives: List[String],
    negatives: List[String],
    baseConfig: UniversalLearnerConfig = UniversalLearnerConfig(),
    complexities: List[(Int, Int)] = List((2, 1), (3, 2), (4, 3), (5, 4)),
    targetAccuracy: Double = 1.0,
    seed: Int = 0
  ): LearningResult = {
    var best: Option[LearningResult] = None
    for ((b, c) <- complexities) {
      val res = learn(positives, negatives, baseConfig.copy(numBoolVars = b, numCountVars = c), seed)
      best = best match {
        case None => Some(res)
        case Some(cur) => Some(if (res.accuracy > cur.accuracy) res else cur)
      }
      if (res.accuracy >= targetAccuracy) return res
    }
    best.getOrElse(LearningResult(learn(positives, negatives, baseConfig, seed).program, 0.0))
  }

  def learnAutoExpand(
    positives: List[String],
    negatives: List[String],
    baseConfig: UniversalLearnerConfig = UniversalLearnerConfig(),
    startBool: Int = 4,
    startCount: Int = 2,
    maxBool: Int = 8,
    maxCount: Int = 6,
    targetAccuracy: Double = 1.0,
    seed: Int = 0,
    preferSmall: Boolean = true,
    keepSearchingAfterHit: Int = 4,
    outName: String = "Out",
    initProgram: Option[CRASP] = None,
    alphabetOverride: Option[Set[String]] = None
  ): LearningResult = {

    def better(a: LearningResult, b: LearningResult): LearningResult = {
      if (a.accuracy > b.accuracy) a
      else if (a.accuracy < b.accuracy) b
      else if (!preferSmall) a
      else {
        val sa = ProgramSize.programSize(a.program)
        val sb = ProgramSize.programSize(b.program)
        if (sa <= sb) a else b
      }
    }

    var b = startBool
    var c = startCount
    var best: Option[LearningResult] = None
    var hitCount = 0

    while (b <= maxBool || c <= maxCount) {
      val bb = math.min(b, maxBool)
      val cc = math.min(c, maxCount)

      val res = learn(
        positives = positives,
        negatives = negatives,
        config = baseConfig.copy(numBoolVars = bb, numCountVars = cc),
        seed = seed,
        outName = outName,
        initProgram = initProgram,
        alphabetOverride = alphabetOverride
      )

      best = best match {
        case None => Some(res)
        case Some(cur) => Some(better(res, cur))
      }

      if (res.accuracy >= targetAccuracy) {
        hitCount += 1
        if (hitCount > keepSearchingAfterHit) return best.get
      }

      val canGrowB = b < maxBool
      val canGrowC = c < maxCount

      if (canGrowB && canGrowC) {
        if (b <= c) b += 1 else c += 1
      } else if (canGrowB) {
        b += 1
      } else if (canGrowC) {
        c += 1
      } else {
        return best.get
      }
    }

    best.get
  }
}

// ==============================================================================================
// Search policies and shape search live here
// ==============================================================================================
object Learners {
  // ============================================================================================
  // Level (k=max(b,c)) search with "jumping" schedule
  // ============================================================================================

  final case class LevelSearchCfg(
    maxBool: Int,
    maxCount: Int,

    // Start with k=1 (i.e., try (1,1) first)
    startK: Int = 1,

    // Try (k,k) first, then boundary shapes (k,1..k-1) and (1..k-1,k)
    tryBoundary: Boolean = true,

    // If true, also enumerate interior (b,c) with b<k and c<k after boundary fails
    tryInterior: Boolean = false,

    // Learning knobs
    targetAccuracy: Double = 1.0,
    restartsPerShape: Int = 1,
    seed: Int = 0,
    outName: String = "Out",
    preferSmall: Boolean = true,
    verbose: Boolean = true
  )

  /** Shapes to try for a given k. */
  private def shapesAtLevel(k: Int, cfg: LevelSearchCfg): Vector[(Int, Int)] = {
    val maxB = math.min(cfg.maxBool, k)
    val maxC = math.min(cfg.maxCount, k)

    val kk = Vector((maxB, maxC)) // usually (k,k) unless one side capped

    if (!cfg.tryBoundary && !cfg.tryInterior) return kk

    val boundary =
      if (!cfg.tryBoundary) Vector.empty
      else {
        val bs =
          (1 until maxC).map(c => (maxB, c)).toVector // (k,1..k-1)
        val cs =
          (1 until maxB).map(b => (b, maxC)).toVector // (1..k-1,k)
        // keep deterministic order: (k,1..), then (1..,k)
        bs ++ cs
      }

    val interior =
      if (!cfg.tryInterior) Vector.empty
      else {
        (1 until maxB).flatMap { b =>
          (1 until maxC).map(c => (b, c))
        }.toVector
      }

    // de-dup (if k=1 etc.)
    (kk ++ boundary ++ interior).distinct
  }

  /** "Jump schedule" over k: 1, mid, 3/4, 1/4, 7/8, 3/8, ... (dyadic splits). */
  private def jumpSchedule(start: Int, hi: Int): Vector[Int] = {
    if (hi < start) return Vector.empty
    val used = scala.collection.mutable.LinkedHashSet.empty[Int]
    def add(x: Int): Unit = if (x >= start && x <= hi) used.addOne(x)

    add(start)

    // recursive dyadic splitting
    def split(lo: Int, hi0: Int): Unit = {
      if (lo > hi0) return
      val mid = (lo + hi0) / 2
      add(mid)
      // upper-mid (towards hi)
      val q3 = (mid + hi0) / 2
      add(q3)
      // lower-mid (towards lo)
      val q1 = (lo + mid) / 2
      add(q1)
      if (hi0 - lo <= 2) return
      split(mid + 1, hi0)
      split(lo, mid - 1)
    }

    split(start, hi)
    used.toVector
  }

  /**
   * Level search:
   *  - try k=1 (i.e., (1,1))
   *  - if fail, jump over k using a dyadic/quartile schedule
   *  - for each k, try shapesAtLevel(k) (usually starts with (k,k))
   *  - return immediately on first perfect fit (or best overall if none)
   */
  def learnByLevelJumpSearch(
    positives: List[String],
    negatives: List[String],
    baseConfig: UniversalLearnerConfig,
    cfg: LevelSearchCfg,
    initProgram: Option[CRASP] = None,
    alphabetOverride: Option[Set[String]] = None,
    onBestUpdate: LearningResult => Unit = _ => ()
  ): LearningResult = {

    // Use min so the schedule stays compact for small programs.
    // Large-oracle cases (piecewise-10 etc.) bypass this entirely via direct search in passive_learning.
    val K = math.min(cfg.maxBool, cfg.maxCount)
    val ks = jumpSchedule(cfg.startK, K)

    var best: Option[LearningResult] = None
    var usedInit = false

    var i = 0
    while (i < ks.length) {
      val k = ks(i)
      val shapes = shapesAtLevel(k, cfg)

      // if (cfg.verbose) println(s"[LevelJump] k=$k shapes=${shapes.mkString(",")}")

      var j = 0
      while (j < shapes.length) {
        val (b, c) = shapes(j)

        // if (cfg.verbose) println(s"[LevelJump] try (b=$b,c=$c)")

        val res = learnBoundedWithRestarts(
          positives = positives,
          negatives = negatives,
          baseConfig = baseConfig,
          b = b,
          c = c,
          targetAccuracy = cfg.targetAccuracy,
          restarts = cfg.restartsPerShape,
          seed = cfg.seed + 10007 * b + 10009 * c,
          outName = cfg.outName,
          initProgram = if (!usedInit) initProgram else None,
          alphabetOverride = alphabetOverride,
          preferSmall = cfg.preferSmall,
          keepSearchingAfterHit = 0,
          verbose = cfg.verbose
        )

        usedInit = true

        val newBest = best match {
          case None => Some(res)
          case Some(cur) => Some(better(res, cur, cfg.preferSmall))
        }
        if (newBest.exists(b => best.forall(_.accuracy < b.accuracy)))
          newBest.foreach(onBestUpdate)
        best = newBest

        if (res.accuracy >= cfg.targetAccuracy) {
          return best.get
        }

        j += 1
      }

      i += 1
    }

    best.getOrElse {
      LearningResult(
        program = CRASP(alphabetOverride.getOrElse(Set("a", "b")), Nil, Nil),
        accuracy = 0.0
      )
    }
  }

  // -----------------------------
  // Growth policy / knobs
  // -----------------------------
  final case class GrowthPolicy(
    startBool: Int,
    startCount: Int,
    maxBool: Int,
    maxCount: Int,
    growBool: Int = 1,
    growCount: Int = 1,
    preferSmall: Boolean = true,
    keepSearchingAfterHit: Int = 0 // 0 = stop immediately at first 100%
  ) {
    require(startBool >= 0 && startCount >= 0, "start bounds must be >= 0")
    require(maxBool >= startBool && maxCount >= startCount, "max must cover start")
    require(growBool >= 1 && growCount >= 1, "growth steps must be >= 1")
  }

  // -----------------------------
  // Helpers
  // -----------------------------
  private def better(a: LearningResult, b: LearningResult, preferSmall: Boolean): LearningResult = {
    if (a.accuracy > b.accuracy) a
    else if (a.accuracy < b.accuracy) b
    else if (!preferSmall) a
    else {
      val sa = ProgramSize.programSize(a.program)
      val sb = ProgramSize.programSize(b.program)
      if (sa <= sb) a else b
    }
  }

  // ============================================================================================
  // 1) Bounded learning with restarts
  // ============================================================================================
  def learnBoundedWithRestarts(
    positives: List[String],
    negatives: List[String],
    baseConfig: UniversalLearnerConfig,
    b: Int,
    c: Int,
    targetAccuracy: Double = 1.0,
    restarts: Int = 10,
    seed: Int = 0,
    outName: String = "Out",
    initProgram: Option[CRASP] = None,
    alphabetOverride: Option[Set[String]] = None,
    preferSmall: Boolean = true,
    keepSearchingAfterHit: Int = 0,
    verbose: Boolean = true
  ): LearningResult = {

    val cfg = baseConfig.copy(numBoolVars = b, numCountVars = c)

    var best: Option[LearningResult] = None
    var hitCount = 0

    val rndBound = new scala.util.Random(seed + 77777)

    // Track the (b, c) of the best result so far so we can bias future restarts toward it.
    var bestB = b
    var bestC = c

    var r = 0
    while (r < restarts) {
      val thisSeed = seed + r * 1009
      val init = if (r == 0) initProgram else None

      // Bound selection per restart:
      //   r=0         → full (b, c): ensure the max space is always tried first
      //   r odd       → deterministically reduced bounds at (1 - step*0.25) fraction of max,
      //                 clamped to [1/3..3/4]; step = (r+1)/2 so r=1→75%, r=3→50%, r=5→33%.
      //                 This guarantees diversity — pure random can collapse back to (b,c).
      //   r even (>0) → near best (b', c') found so far: exploit the most promising size
      //                 by sampling within ±2 of the best, clamped to [1..b] × [1..c]
      val (rb, rc) =
        if (r == 0) (b, c)
        else if (r % 2 == 1) {
          val step = (r + 1) / 2
          val frac = math.max(0.33, 1.0 - step * 0.25)
          (math.max(1, (b * frac).toInt), math.max(1, (c * frac).toInt))
        }
        else {
          val nb = math.min(b, math.max(1, bestB + rndBound.nextInt(5) - 2))
          val nc = math.min(c, math.max(1, bestC + rndBound.nextInt(3) - 1))
          (nb, nc)
        }
      val restartCfg = baseConfig.copy(numBoolVars = rb, numCountVars = rc)

      val res = UniversalLearner.learn(
        positives = positives,
        negatives = negatives,
        config = restartCfg,
        seed = thisSeed,
        outName = outName,
        initProgram = init,
        alphabetOverride = alphabetOverride
      )

      // Update best-size tracking when this restart improves accuracy
      best = best match {
        case None =>
          bestB = rb; bestC = rc
          Some(res)
        case Some(cur) =>
          val b2 = better(res, cur, preferSmall)
          if (b2.accuracy > cur.accuracy) { bestB = rb; bestC = rc }
          Some(b2)
      }

      if (verbose) {
        println(f"[Learners] bounded: b=$rb c=$rc restart=${r + 1}/$restarts acc=${res.accuracy * 100}%.2f%%")
      }

      if (res.accuracy >= targetAccuracy) {
        hitCount += 1
        if (keepSearchingAfterHit <= 0 || hitCount > keepSearchingAfterHit) {
          return best.get
        }
      }

      r += 1
    }

    best.getOrElse {
      LearningResult(
        program = CRASP(alphabetOverride.getOrElse(Set("a", "b")), Nil, Nil),
        accuracy = 0.0
      )
    }
  }

  // ============================================================================================
  // 2) Grow (b,c) on failure to reach target
  // ============================================================================================
  def learnWithGrowth(
    positives: List[String],
    negatives: List[String],
    baseConfig: UniversalLearnerConfig,
    policy: GrowthPolicy,
    targetAccuracy: Double = 1.0,
    restartsPerBound: Int = 10,
    seed: Int = 0,
    outName: String = "Out",
    initProgram: Option[CRASP] = None,
    alphabetOverride: Option[Set[String]] = None,
    verbose: Boolean = true
  ): LearningResult = {

    var b = math.max(0, policy.startBool)
    var c = math.max(0, policy.startCount)

    var bestOverall: Option[LearningResult] = None
    var initOnce: Option[CRASP] = initProgram

    while (b <= policy.maxBool && c <= policy.maxCount) {
      val bb = math.min(b, policy.maxBool)
      val cc = math.min(c, policy.maxCount)

      if (verbose) {
        println(s"[Learners] try bounds: b=$bb c=$cc (restarts=$restartsPerBound) targetAcc=$targetAccuracy")
      }

      val res = learnBoundedWithRestarts(
        positives = positives,
        negatives = negatives,
        baseConfig = baseConfig,
        b = bb,
        c = cc,
        targetAccuracy = targetAccuracy,
        restarts = restartsPerBound,
        seed = seed + bb * 17 + cc * 29,
        outName = outName,
        initProgram = initOnce,
        alphabetOverride = alphabetOverride,
        preferSmall = policy.preferSmall,
        keepSearchingAfterHit = policy.keepSearchingAfterHit,
        verbose = verbose
      )

      initOnce = None

      bestOverall = bestOverall match {
        case None => Some(res)
        case Some(cur) => Some(better(res, cur, policy.preferSmall))
      }

      if (bestOverall.get.accuracy >= targetAccuracy) return bestOverall.get

      // grow bounds
      val canGrowB = bb < policy.maxBool
      val canGrowC = cc < policy.maxCount

      if (!canGrowB && !canGrowC) return bestOverall.get
      else if (canGrowB && canGrowC) {
        if (bb <= cc) b = math.min(policy.maxBool, bb + policy.growBool)
        else c = math.min(policy.maxCount, cc + policy.growCount)
      } else if (canGrowB) {
        b = math.min(policy.maxBool, bb + policy.growBool)
      } else {
        c = math.min(policy.maxCount, cc + policy.growCount)
      }
    }

    bestOverall.get
  }

  // ============================================================================================
  // 3) Shape enumeration over (b,c)
  // ============================================================================================
  final case class ShapeSearchCfg(
    maxBool: Int,
    maxCount: Int,
    order: String = "lex", // "lex" or "sum"
    targetAccuracy: Double = 1.0,
    restartsPerShape: Int = 1,
    seed: Int = 0,
    outName: String = "Out",
    preferSmall: Boolean = true,
    keepSearchingAfterHit: Int = 0,
    verbose: Boolean = true
  ) {
    require(maxBool >= 0 && maxCount >= 0)
    require(restartsPerShape >= 1)
  }

  def learnByShapeEnumeration(
    positives: List[String],
    negatives: List[String],
    baseConfig: UniversalLearnerConfig,
    cfg: ShapeSearchCfg,
    initProgram: Option[CRASP] = None,
    alphabetOverride: Option[Set[String]] = None
  ): LearningResult = {

    def shapes: Vector[(Int, Int)] = {
      val all =
        (0 to cfg.maxBool).flatMap { b =>
          (0 to cfg.maxCount).map(c => (b, c))
        }.toVector

      cfg.order match {
        case "sum" => all.sortBy { case (b, c) => (b + c, b, c) }
        case _     => all.sortBy { case (b, c) => (b, c) } // lex default
      }
    }

    var best: Option[LearningResult] = None
    var hitCount = 0

    val ss = shapes
    var i = 0
    while (i < ss.length) {
      val (b, c) = ss(i)

      if (cfg.verbose) println(s"[ShapeEnum] try shape (b=$b,c=$c)")

      val res = learnBoundedWithRestarts(
        positives = positives,
        negatives = negatives,
        baseConfig = baseConfig,
        b = b,
        c = c,
        targetAccuracy = cfg.targetAccuracy,
        restarts = cfg.restartsPerShape,
        seed = cfg.seed + 10007 * b + 10009 * c,
        outName = cfg.outName,
        initProgram = if (i == 0) initProgram else None,
        alphabetOverride = alphabetOverride,
        preferSmall = cfg.preferSmall,
        keepSearchingAfterHit = 0, // keep semantics here
        verbose = cfg.verbose
      )

      best = best match {
        case None => Some(res)
        case Some(cur) => Some(better(res, cur, cfg.preferSmall))
      }

      if (res.accuracy >= cfg.targetAccuracy) {
        hitCount += 1
        if (cfg.keepSearchingAfterHit <= 0 || hitCount > cfg.keepSearchingAfterHit) {
          return best.get
        }
      }

      i += 1
    }

    best.get
  }

  // ============================================================================================
  // 4) Convenience wrapper: lex enumeration (your preferred order)
  // ============================================================================================
  def learnByLexEnumeration(
    positives: List[String],
    negatives: List[String],
    baseConfig: UniversalLearnerConfig,
    maxBool: Int,
    maxCount: Int,
    restartsPerShape: Int,
    seed: Int,
    outName: String,
    targetAccuracy: Double = 1.0,
    preferSmall: Boolean = true,
    keepSearchingAfterHit: Int = 0,
    initProgram: Option[CRASP] = None,
    alphabetOverride: Option[Set[String]] = None,
    verbose: Boolean = true
  ): LearningResult = {
    learnByShapeEnumeration(
      positives,
      negatives,
      baseConfig,
      ShapeSearchCfg(
        maxBool = maxBool,
        maxCount = maxCount,
        order = "lex",
        targetAccuracy = targetAccuracy,
        restartsPerShape = restartsPerShape,
        seed = seed,
        outName = outName,
        preferSmall = preferSmall,
        keepSearchingAfterHit = keepSearchingAfterHit,
        verbose = verbose
      ),
      initProgram = initProgram,
      alphabetOverride = alphabetOverride
    )
  }

  // ============================================================================================
  // 5) Convenience: keep growing until 100% or max
  // ============================================================================================
  def learnAutoExpandUntilPerfect(
    positives: List[String],
    negatives: List[String],
    baseConfig: UniversalLearnerConfig,
    startBool: Int,
    startCount: Int,
    maxBool: Int,
    maxCount: Int,
    seed: Int,
    outName: String,
    initProgram: Option[CRASP],
    alphabetOverride: Option[Set[String]],
    preferSmall: Boolean = true,
    keepSearchingAfterHit: Int = 4,
    targetAccuracy: Double = 1.0
  ): LearningResult = {
    UniversalLearner.learnAutoExpand(
      positives = positives,
      negatives = negatives,
      baseConfig = baseConfig,
      startBool = startBool,
      startCount = startCount,
      maxBool = maxBool,
      maxCount = maxCount,
      targetAccuracy = targetAccuracy,
      seed = seed,
      preferSmall = preferSmall,
      keepSearchingAfterHit = keepSearchingAfterHit,
      outName = outName,
      initProgram = initProgram,
      alphabetOverride = alphabetOverride
    )
  }
}
