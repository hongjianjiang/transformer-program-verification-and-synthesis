// File: src/main/scala/LS/LsCegis.scala
package LS

import scala.collection.mutable
import java.io.{File, PrintWriter}

import Crasp._
import utils.Verify

object LsCegis {

  // ----------------------------
  // Helpers
  // ----------------------------
  private def strToWord(s: String): Vector[String] =
    if (s.isEmpty) Vector.empty else s.toVector.map(_.toString)

  private def parseCexToWord(cexStr: String, allowSpaces: Boolean = true): Vector[String] = {
    val t = cexStr.trim
    if (t.isEmpty) Vector.empty
    else if (allowSpaces && t.contains(" ")) t.split("\\s+").toVector.filter(_.nonEmpty)
    else t.toVector.map(_.toString)
  }

  private def wordToString(w: Vector[String], allowSpaces: Boolean): String =
    if (allowSpaces) w.mkString(" ") else w.mkString

  private def datasetAccuracy(
    ev: CRASPEvaluator,
    out: String,
    pos: List[String],
    neg: List[String]
  ): (Int, Int, Int, Int, Double) = {
    val posCorrect = pos.count(s => ev.classifyWith(out, strToWord(s)))
    val negCorrect = neg.count(s => !ev.classifyWith(out, strToWord(s)))
    val total = pos.size + neg.size
    val correct = posCorrect + negCorrect
    val acc = if (total == 0) 1.0 else correct.toDouble / total.toDouble
    (total, correct, posCorrect, negCorrect, acc)
  }

  // Accept-all seed (tautology)
  private def acceptAllSeed(alphabet: Set[String], outName: String): CRASP = {
    // 0 <= 0  is always true
    CRASP(
      alphabet = alphabet,
      boolRules = List(
        BoolRule(outName, BoolAtomComparison(strict = false, CountAtomConst(0), CountAtomConst(0)))
      ),
      countRules = Nil
    )
  }

  // ----------------------------
  // SA adaptation
  // ----------------------------
  /**
   * @param programSizeHint  approximate number of bool rules in the program being searched.
   *                         Larger programs → fewer iters per restart (each iter costs more).
   */
  private def adaptSA(
    base: LocalSearchSA.SAConfig,
    totalSamples: Int,
    round: Int,
    outName: String,
    programSizeHint: Int = 4
  ): LocalSearchSA.SAConfig = {

    // Scale iters inversely with program size.
    // Each SA iter evaluates all rules on all training examples; cost ∝ programSize × examples.
    // sizeScale > 1 compresses the iter budget for large programs so total wall-time stays bounded.
    val sizeScale = math.max(1.0, programSizeHint.toDouble / 8.0)

    // Cap raw iters lower for large programs to stay within the per-target time budget.
    val rawCap = if (programSizeHint > 12) 120_000 else 500_000

    val it = {
      // If the caller set an explicit hard cap (e.g. RunPL --iters N), use it directly.
      // The sizeScale reduction is an internal heuristic; it should not silently override
      // an explicit user budget.  The cap still acts as a ceiling (never exceeds it).
      if (base.hardIterCap < Int.MaxValue) {
        base.hardIterCap
      } else {
        val baseI = math.max(base.iters, 50_000)
        val byData = baseI + totalSamples * 2_000
        val byRound = byData + round * 1_000
        val raw = math.min(rawCap, byRound)
        math.max(20_000, (raw / sizeScale).toInt)
      }
    }
    println(s"[SA Adapt] programSizeHint=$programSizeHint sizeScale=${"%.2f".format(sizeScale)} iters adjusted to $it")

    val isLarge = programSizeHint > 12

    // Slower cooling for large programs: fewer iters means each one must count more.
    val cool =
      if (isLarge)       0.9999
      else if (it >= 200_000) 0.99995
      else if (it >= 80_000)  0.9998
      else 0.9995

    val reheat =
      if (isLarge)             2000
      else if (it >= 200_000)  2000
      else if (it >= 80_000)   3000
      else 4000

    // Gentler reheat factor for large programs to avoid over-heating.
    val reheatFactor = if (isLarge) 1.15 else 1.25

    val early = (round <= 3 && totalSamples <= 10)

    base.copy(
      outName = outName,
      iters = it,
      startTemp = math.max(if (isLarge) 2.0 else 1.5, base.startTemp),
      cooling = cool,
      reheatEvery = reheat,
      reheatFactor = reheatFactor,

      // Regularization schedule
      sizePenalty = if (early) 0.0003 else base.sizePenalty,
      countingPenalty = if (early) 0.0006 else base.countingPenalty,
      conditionalPenalty = if (early) 0.0008 else base.conditionalPenalty,
      depthPenalty = if (early) 0.0002 else base.depthPenalty,

      // keep positional hack penalty strong
      periodPenalty = base.periodPenalty,

      // stall: don’t stop too early when iters is big
      stallLimit = math.max(base.stallLimit, it / 5),
      tempFloor = base.tempFloor
    )
  }

  // ----------------------------
  // Learn Perfect: LEX over (b,c), repeat epochs with SA schedule
  // ----------------------------
  private def learnPerfectWithLexSearch(
    positives: List[String],
    negatives: List[String],
    alphabet: Set[String],
    outName: String,
    baseConfig: UniversalLearnerConfig,
    seed: Int,
    cegisRound: Int,
    // knobs
    maxBool: Int = 16,
    maxCount: Int = 12,
    restartsPerShape: Int = 1,
    epochs: Int = 4,
    verbose: Boolean = true
  ): LearningResult = {

    val totalSamples = positives.size + negatives.size
    var best: Option[LearningResult] = None

    var epoch = 0
    while (epoch < epochs) {

      val tuned = baseConfig.copy(
        sa = adaptSA(
          base = baseConfig.sa,
          totalSamples = totalSamples,
          round = cegisRound + epoch,
          outName = outName,
          programSizeHint = math.max(maxBool, maxCount)
        )
      )

      // LEX on (b,c): smallest b first, then smallest c.
      val res = Learners.learnByLexEnumeration(
        positives = positives,
        negatives = negatives,
        baseConfig = tuned,
        maxBool = maxBool,
        maxCount = maxCount,
        restartsPerShape = 1,
        seed = seed + 10_000 * epoch,
        outName = outName,
        targetAccuracy = 1.0,
        preferSmall = true,
        keepSearchingAfterHit = 0,
        initProgram = None,
        alphabetOverride = Some(alphabet),
        verbose = verbose
      )

      best = best match {
        case None => Some(res)
        case Some(cur) =>
          val better =
            if (res.accuracy > cur.accuracy) res
            else if (res.accuracy < cur.accuracy) cur
            else {
              val sa = ProgramSize.programSize(res.program)
              val sb = ProgramSize.programSize(cur.program)
              if (sa <= sb) res else cur
            }
          Some(better)
      }

      if (best.get.accuracy >= 1.0 - 1e-12) return best.get

      if (verbose) {
        val acc = best.get.accuracy * 100.0
        println(f"[LexSearch] epoch=$epoch bestAcc=$acc%.2f%% (not perfect yet)")
      }

      epoch += 1
    }

    best.get
  }

  // ============================================================================================
  // Equality refinement: use verifier to get CEX, label CEX via Verify.accept(oracle, word)
  // ============================================================================================
  def optimizeByEqualityRefinement(
    referenceFile: String,
    config: UniversalLearnerConfig = UniversalLearnerConfig(),
    maxRounds: Int = 50,
    save_name: String = "cegis_opt",
    flag: String = "Out",         // oracle output rule
    outNameExact: String = "Out", // candidate output rule
    allowSpaceSeparatedCex: Boolean = true,

    // learner knobs (match passive_learning defaults)
    restartsPerShape: Int = 1,
    startK: Int = 1,
    tryBoundary: Boolean = true,
    tryInterior: Boolean = false,

    // verifier knobs
    verifierRetries: Int = 5       // retry equality a few times if only repeated CEXs are returned
  ): Option[CRASP] = {

    println(s"[LsCegis] Starting optimizeByEqualityRefinement maxRounds=$maxRounds save_name=$save_name flag=$flag")

    // ---- Parse oracle once (only to get alphabet / size caps) ----
    val oracleProg: CRASP =
      CRASPParser.parseFile(referenceFile, enforceOrder = false) match {
        case Left(err) =>
          println(s"[LsCegis] Failed to parse oracle file '$referenceFile': $err")
          return None
        case Right(p) => p
      }

    val alphabet = oracleProg.alphabet
    if (alphabet.isEmpty) {
      println("[LsCegis] Oracle alphabet is empty; abort.")
      return None
    }

    // ---- Accumulated datasets ----
    val posSet = mutable.LinkedHashSet.empty[String]
    val negSet = mutable.LinkedHashSet.empty[String]
    val seen   = mutable.HashSet.empty[String] // canonical string form of seen examples

    val outFile = new File(s"cegis/$save_name.crasp")
    if (outFile.getParentFile != null) outFile.getParentFile.mkdirs()

    var round = 0
    var bestProgram: Option[CRASP] = None

    // Initial candidate = tautology (accept-all)
    val tautologySeed = acceptAllSeed(alphabet, outNameExact)

    while (round < maxRounds) {
      println(s"--- Round $round ---")
      println(s"[Total] Dataset Size: ${posSet.size} pos, ${negSet.size} neg")
      println(s"[Positives] ${posSet.mkString(", ")}")
      println(s"[Negatives] ${negSet.mkString(", ")}")

      val positives = posSet.toList
      val negatives = negSet.toList

      // ============================================================
      // (1) Learn candidate
      // ============================================================
      val candidate: CRASP =
        if (positives.isEmpty && negatives.isEmpty) {
          tautologySeed
        } else {
          val totalSamples = positives.size + negatives.size
          val oracleBoolSize = math.max(1, oracleProg.boolRules.size)
          val tuned = config.copy(
            sa = adaptSA(
              base = config.sa,
              totalSamples = totalSamples,
              round = round,
              outName = outNameExact,
              programSizeHint = oracleBoolSize
            )
          )

          val res = Learners.learnByLevelJumpSearch(
            positives = positives,
            negatives = negatives,
            baseConfig = tuned,
            cfg = Learners.LevelSearchCfg(
              maxBool = oracleBoolSize,
              maxCount = math.max(0, oracleProg.countRules.size),
              startK = 1,
              tryBoundary = tryBoundary,
              tryInterior = tryInterior,
              targetAccuracy = 1.0,
              restartsPerShape = 1,
              seed = 42 + 1000 * round,
              outName = outNameExact,
              preferSmall = true,
              verbose = true
            ),
            initProgram = None,
            alphabetOverride = Some(alphabet)
          )

          if (res.accuracy < 1.0 - 1e-12) {
            val acc = res.accuracy * 100.0
            println(f"[LsCegis] Failed to learn perfect fit. Best acc=$acc%.2f%%")
            return bestProgram
          }

          val ev = new CRASPEvaluator(res.program)
          val (total, _, posCorrect, negCorrect, acc) =
            datasetAccuracy(ev, outNameExact, positives, negatives)
          println(f"[ACCURACY] total=$total pos=${positives.size}(ok=$posCorrect) neg=${negatives.size}(ok=$negCorrect) acc=${acc * 100}%.2f%%")

          res.program
        }

      bestProgram = Some(candidate)

      // Save candidate to disk for verifier
      val pw = new PrintWriter(outFile)
      try {
        pw.write(
          Printer.pretty(candidate, Printer.PrintCfg(outName = outNameExact, mapDigitsToLetters = false))
        )
      } finally pw.close()

      // ============================================================
      // (2) Equality check + refine using CEX labeled by Verify.accept(oracle)
      // ============================================================
      var tries = 0
      var eq = false
      var addedAny = false
      var lastCexStr: String = ""

      while (tries < verifierRetries && !eq && !addedAny) {
        val (eq0, cexStr0) =
          Verify.equal(outFile.getAbsolutePath, outNameExact, referenceFile, flag)

        eq = eq0
        lastCexStr = cexStr0

        if (!eq) {
          val cexs = cexStr0.split("\n").map(_.trim).filter(_.nonEmpty)

          for (raw <- cexs) {
            val cexVec = parseCexToWord(raw, allowSpaceSeparatedCex)
            val key = cexVec.mkString

            if (seen.add(key)) {
              addedAny = true

              val wStr = wordToString(cexVec, allowSpaceSeparatedCex)

              // Label by oracle using verifier membership query
              val oracleAccepts =
                try Verify.accept(referenceFile, flag, wStr)
                catch {
                  case e: Exception =>
                    println(s"[LsCegis] WARNING: Verify.accept failed on word='$wStr': ${e.getMessage}")
                    false
                }

              if (oracleAccepts) {
                negSet.remove(key)
                posSet.addOne(key)
              } else {
                posSet.remove(key)
                negSet.addOne(key)
              }
            }
          }
        }

        tries += 1
      }

      if (eq) {
        println("[EQ] Verified: Cand == Oracle (no counterexample).")
        return bestProgram
      }

      if (!addedAny) {
        val show = if (lastCexStr == null || lastCexStr.trim.isEmpty) "<empty>" else lastCexStr.trim
        println(s"[Stuck] Could not obtain any NEW counterexamples after $verifierRetries verifier calls. lastCex='$show'")
        return bestProgram
      }

      round += 1
    }

    println("Max rounds reached without proving equality.")
    bestProgram
  }

  // ============================================================================================
  // Constraint learning: enforce Cand ⊆ Oracle
  // ============================================================================================
  def constraint_learning(
    referenceFile: String,                 // oracle file
    positives0: List[String],
    negatives0: List[String],
    config: UniversalLearnerConfig = UniversalLearnerConfig(),
    maxRounds: Int = 30,
    save_name: String = "constraint_learning",
    flag: String = "Out",                  // oracle output rule name
    outNameExact: String = "Out",          // candidate output rule name
    allowSpaceSeparatedCex: Boolean = true
  ): Option[CRASP] = {

    // ---- Parse oracle once ----
    val oracleProg: CRASP =
      CRASPParser.parseFile(referenceFile, enforceOrder = false) match {
        case Left(err) =>
          println(s"[constraint_learning] Failed to parse oracle file '$referenceFile': $err")
          return None
        case Right(p) => p
      }

    val oracleEval = new CRASPEvaluator(oracleProg)
    val alphabet = oracleProg.alphabet
    if (alphabet.isEmpty) {
      println("[constraint_learning] Oracle alphabet is empty; abort.")
      return None
    }

    // datasets
    val posSet = mutable.LinkedHashSet.empty[String]
    val negSet = mutable.LinkedHashSet.empty[String]

    
    positives0.foreach(posSet.addOne)
    negatives0.foreach(negSet.addOne)

    val seen = mutable.HashSet.empty[String]
    seen ++= posSet
    seen ++= negSet

    val outFile = new File(s"cegis/$save_name.crasp")
    if (outFile.getParentFile != null) outFile.getParentFile.mkdirs()

    var round = 0
    var bestProgram: Option[CRASP] = None

    while (round < maxRounds) {
      println(s"--- Round $round ---")
      println(s"[Data] pos=${posSet.size} neg=${negSet.size}")

      val positives = posSet.toList
      val negatives = negSet.toList
      val oracleBoolSize = math.max(1, oracleProg.boolRules.size)
      val tuned = config.copy(
          sa = adaptSA(
            base = config.sa,
            totalSamples = positives.size + negatives.size,
            round = 0,
            outName = outNameExact,
            programSizeHint = oracleBoolSize
          )
        )
      // (1) Learn perfect-fit candidate (LEX search)
      val res = Learners.learnByLevelJumpSearch(
          positives = positives,
          negatives = negatives,
          baseConfig = tuned,
          cfg = Learners.LevelSearchCfg(
            maxBool = oracleBoolSize,
            maxCount = math.max(0, oracleProg.countRules.size),
            startK = 1,
            tryBoundary = true,
            tryInterior = false,
            targetAccuracy = 1.0,
            restartsPerShape = 1,
            seed = 42 + 1000 * round,
            outName = outNameExact,
            preferSmall = true,
            verbose = true
          ),
          initProgram = None,
          alphabetOverride = Some(alphabet)
        )
      if (res.accuracy < 1.0 - 1e-12) {
        val acc = res.accuracy * 100.0
        println(f"[constraint_learning] Failed to learn 100%% fit. Best acc=$acc%.2f%%")
        return bestProgram
      }

      val candidate = res.program
      bestProgram = Some(candidate)

      val candEval = new CRASPEvaluator(candidate)
      val (total, _, posCorrect, negCorrect, acc) =
        datasetAccuracy(candEval, outNameExact, positives, negatives)
      println(f"[ACCURACY] total=$total pos=${positives.size}(ok=$posCorrect) neg=${negatives.size}(ok=$negCorrect) acc=${acc * 100}%.2f%%")

      // Save candidate for verifier
      val pw = new PrintWriter(outFile)
      try {
        val text = Printer.pretty(candidate, Printer.PrintCfg(outName = outNameExact, mapDigitsToLetters = false))
        pw.write(text)
      } finally pw.close()

      // (2) Verify subset: Cand ⊆ Oracle
      val (isSubset, cexStr) = Verify.subset(
        outFile.getAbsolutePath, outNameExact,
        referenceFile, flag
      )

      if (isSubset) {
        println("[constraint_learning] Verified Cand ⊆ Oracle. Done.")
        return bestProgram
      }

      println("CEX: " + cexStr)

      // (3) Refine with counterexample => NEG (since Cand accepts but Oracle rejects)
      val cexVec = parseCexToWord(cexStr, allowSpaceSeparatedCex)
      val cexStringRep = cexVec.mkString

      // sanity-check
      val oracleTruth = oracleEval.classifyWith(flag, cexVec)
      println(s"[CEX] Oracle classifies CEX as: " + (if (oracleTruth) "ACCEPT" else "REJECT"))

      if (oracleTruth) {
        println(s"[WARN] Verifier returned CEX but oracle accepts. Adding to POS: $cexStringRep")
        negSet.remove(cexStringRep)
        posSet.addOne(cexStringRep)
      } else {
        posSet.remove(cexStringRep)
        negSet.addOne(cexStringRep)
        println(s"[Refine] Added to NEG: $cexStringRep")
      }

      if (!seen.add(cexStringRep)) {
        println(s"[constraint_learning] Stuck: already saw CEX '$cexStringRep'.")
        return bestProgram
      }

      round += 1
    }

    println("[constraint_learning] Max rounds reached; returning best program so far.")
    bestProgram
  }

  // ============================================================================================
  // Passive learning: fit samples only (no oracle / no refinement), WITH SAVE, using LEX shapes
  // ============================================================================================
  def passive_learning(
    positives0: List[String],
    negatives0: List[String],
    alphabet: Set[String],
    config: UniversalLearnerConfig = UniversalLearnerConfig(),

    // output
    save_name: String = "passive",
    outNameExact: String = "Out",

    // knobs
    seed: Int = 42,
    restartsPerShape: Int = 1,
    epochs: Int = 3,
    maxBool: Int = 8,
    maxCount: Int = 6,

    // behavior
    requirePerfectFit: Boolean = true,
    verbose: Boolean = true,
    onCheckpoint: (CRASP, Double) => Unit = (_, _) => (),

    // Optional warm-start seed.  When provided for large programs, a seeded run at the
    // seed's exact dimensions is attempted first before falling back to direct search.
    // The seed must use rule names compatible with UniversalLearner (B1..Bk, Out, C1..Cm).
    initProgram: Option[CRASP] = None
  ): Option[CRASP] = {

    if (alphabet.isEmpty) {
      println("[passive_learning] Alphabet is empty; abort.")
      return None
    }

    // De-duplicate (stable order)
    val posSet = mutable.LinkedHashSet.empty[String]
    val negSet = mutable.LinkedHashSet.empty[String]
    positives0.foreach(s => if (s != null) posSet.addOne(s.trim))
    negatives0.foreach(s => if (s != null) negSet.addOne(s.trim))

    // Remove overlap
    val overlap = posSet.intersect(negSet)
    if (overlap.nonEmpty) {
      println(s"[passive_learning] WARNING: pos/neg overlap; removing from NEG: ${overlap.mkString(", ")}")
      overlap.foreach(negSet.remove)
    }

    val positives = posSet.toList
    val negatives = negSet.toList

    println(s"[passive_learning] Start. pos=${positives.size} neg=${negatives.size} alphabet={${alphabet.mkString(" ")}}")

    val result: LearningResult =
      if (positives.isEmpty && negatives.isEmpty) {
        println("[passive_learning] Empty dataset → returning accept-all seed.")
        LearningResult(acceptAllSeed(alphabet, outNameExact), 1.0)
      } else {
        val totalSamples = positives.size + negatives.size

        // programSizeHint drives iter scaling: larger programs → fewer iters per restart
        // (each iter evaluates all rules on all examples, so cost ∝ programSize × samples).
        val programSizeHint = math.max(maxBool, maxCount)

        val tuned = config.copy(
          sa = adaptSA(
            base = config.sa,
            totalSamples = totalSamples,
            round = 0,
            outName = outNameExact,
            programSizeHint = programSizeHint
          )
        )

        val checkpointFile = new File(s"cegis/$save_name.crasp")
        if (checkpointFile.getParentFile != null) checkpointFile.getParentFile.mkdirs()

        def saveCheckpoint(r: LearningResult): Unit = {
          onCheckpoint(r.program, r.accuracy)
          val pw = new PrintWriter(checkpointFile)
          try pw.write(Printer.pretty(r.program, Printer.PrintCfg(outName = outNameExact, mapDigitsToLetters = false)))
          finally pw.close()
        }

        // Level-jump uses K = min(maxBool, maxCount) and caps each dimension at k,
        // so it can never explore (maxBool, maxCount) when the two differ.
        // Also fall back to direct search for genuinely large programs.
        val isLargeProgram = maxBool != maxCount || maxBool > 12

        val res =
          if (isLargeProgram) {
            // For large programs the level-jump search wastes too much time on shapes that are
            // too small to represent the target language.  Instead, go directly to a bounded
            // search at the oracle-derived (maxBool, maxCount) size.
            //
            // Subsample training data: cost per SA iter ∝ n_samples × n_rules × word_length.
            // With large bounds (e.g. b=13,c=5) and 800+ examples this easily exceeds 300s/restart.
            // 200+200 gives enough coverage while keeping per-iter cost ~4x lower.
            // Full-dataset accuracy is evaluated after learning (in the accuracy block below).
            val rng = new scala.util.Random(seed + 777)
            val dirPos = rng.shuffle(positives).take(200)
            val dirNeg = rng.shuffle(negatives).take(200)

            initProgram match {
              case Some(seedProg) =>
                // Seeded run: use exact seed dimensions so the mutator name arrays stay
                // consistent with the program (cfg.numBoolRules must == p.boolRules.length).
                val seedB = seedProg.boolRules.length - 1  // non-Out bool vars
                val seedC = seedProg.countRules.length
                // Turn off continue-after-perfect: when the seed already classifies all
                // examples correctly, shouldContinue returns false immediately at iter 0
                // and the SA exits without running any iterations.
                val seededConfig = tuned.copy(sa = tuned.sa.copy(continueAfterPerfect = false))
                println(s"[passive_learning] Large program mode (seeded): seed=(b=$seedB,c=$seedC) max=(b=$maxBool,c=$maxCount) iters=${tuned.sa.iters} restarts=5 samples=${dirPos.size}+${dirNeg.size}")
                val seededResult = Learners.learnBoundedWithRestarts(
                  positives = dirPos,
                  negatives = dirNeg,
                  baseConfig = seededConfig,
                  b = seedB,
                  c = seedC,
                  targetAccuracy = 1.0,
                  restarts = 5,
                  seed = seed,
                  outName = outNameExact,
                  initProgram = Some(seedProg),
                  alphabetOverride = Some(alphabet),
                  preferSmall = true,
                  keepSearchingAfterHit = 0,
                  verbose = verbose
                )
                saveCheckpoint(seededResult)
                if (seededResult.accuracy >= 1.0 - 1e-12) seededResult
                else {
                  // Seeded run fell short; fall back to full-bounds direct search
                  println(s"[passive_learning] Seeded run acc=${seededResult.accuracy}; falling back to direct search at (b=$maxBool,c=$maxCount)")
                  val directResult = Learners.learnBoundedWithRestarts(
                    positives = dirPos,
                    negatives = dirNeg,
                    baseConfig = tuned,
                    b = maxBool,
                    c = maxCount,
                    targetAccuracy = 1.0,
                    restarts = 2,
                    seed = seed + 1001,
                    outName = outNameExact,
                    alphabetOverride = Some(alphabet),
                    preferSmall = true,
                    keepSearchingAfterHit = 0,
                    verbose = verbose
                  )
                  val best = if (directResult.accuracy > seededResult.accuracy) directResult else seededResult
                  saveCheckpoint(best)
                  best
                }

              case None =>
                println(s"[passive_learning] Large program mode: direct search at (b=$maxBool, c=$maxCount) iters=${tuned.sa.iters} restarts=2 samples=${dirPos.size}+${dirNeg.size}")
                val directResult = Learners.learnBoundedWithRestarts(
                  positives = dirPos,
                  negatives = dirNeg,
                  baseConfig = tuned,
                  b = maxBool,
                  c = maxCount,
                  targetAccuracy = 1.0,
                  restarts = 2,
                  seed = seed,
                  outName = outNameExact,
                  alphabetOverride = Some(alphabet),
                  preferSmall = true,
                  keepSearchingAfterHit = 0,
                  verbose = verbose
                )
                saveCheckpoint(directResult)
                directResult
            }
          } else {
            // For small programs, the level-jump search efficiently finds the minimal shape.
            // Skip the smallest shapes (very unlikely to work) by starting at 2/3 of K.
            val K = math.min(maxBool, maxCount)
            val startK = math.max(1, K * 2 / 3)

            // Subsample training data to keep per-iter cost low.
            // 200+200 gives enough coverage to avoid spurious 100%-fits on wrong shapes,
            // while keeping per-iter cost ~4x lower than the full dataset.
            // Full-dataset accuracy is evaluated after learning (in the accuracy block below).
            val rng = new scala.util.Random(seed + 999)
            val levPos = rng.shuffle(positives).take(200)
            val levNeg = rng.shuffle(negatives).take(200)

            // Cap iters AND stallLimit together — if only iters is capped, stallLimit stays at
            // the uncapped value (80k+) and stall detection never fires, so every shape runs the
            // full iter limit even when it clearly cannot represent the target language.
            val cappedIters = math.min(tuned.sa.iters, 30_000)
            val levelTuned = tuned.copy(sa = tuned.sa.copy(
              iters      = cappedIters,
              stallLimit = math.max(1000, cappedIters / 5)
            ))
            println(s"[passive_learning] Level-jump: K=$K startK=$startK iters=$cappedIters stallLimit=${math.max(1000, cappedIters / 5)} samples=${levPos.size}+${levNeg.size}")
            Learners.learnByLevelJumpSearch(
              positives = levPos,
              negatives = levNeg,
              baseConfig = levelTuned,
              cfg = Learners.LevelSearchCfg(
                maxBool = maxBool,
                maxCount = maxCount,
                startK = startK,
                tryBoundary = true,
                tryInterior = false,
                targetAccuracy = 1.0,
                restartsPerShape = 1,
                seed = seed,
                outName = outNameExact,
                preferSmall = true,
                verbose = verbose
              ),
              initProgram = None,
              alphabetOverride = Some(alphabet),
              onBestUpdate = saveCheckpoint
            )
          }
        res
      }

    // Accuracy report
    val ev = new CRASPEvaluator(result.program)
    val (total, _, posCorrect, negCorrect, acc) =
      datasetAccuracy(ev, outNameExact, positives, negatives)

    println(f"[passive_learning][ACCURACY] total=$total pos=${positives.size}(ok=$posCorrect) neg=${negatives.size}(ok=$negCorrect) acc=${acc * 100}%.2f%%")
    println(s"[passive_learning] Program size = ${ProgramSize.programSize(result.program)}")

    if (requirePerfectFit && acc < 1.0 - 1e-12) {
      println("[passive_learning] Failed to achieve perfect fit; not saving.")
      return None
    }

    // Save to disk
    val outFile = new File(s"cegis/$save_name.crasp")
    if (outFile.getParentFile != null) outFile.getParentFile.mkdirs()

    val pw = new PrintWriter(outFile)
    try {
      val text = Printer.pretty(
        result.program,
        Printer.PrintCfg(outName = outNameExact, mapDigitsToLetters = false)
      )
      pw.write(text)
    } finally pw.close()

    println(s"[passive_learning] Saved program to ${outFile.getPath}")
    Some(result.program)
  }
}
