// File: src/main/scala/RunConsLearning.scala
//
// Experiment runner for Constraint Learning (enforce Cand ⊆ Oracle) USING Verify.subset.
//
// Records per target:
//   - ok (proved subset?)
//   - rounds used
//   - local search time (ms)   [time spent inside learner]
//   - verification time (ms)   [time spent inside Verify.subset]
//   - total time (ms)
//   - final program size (pretty-printed length)
//   - last_cex (if failed/stuck)
//   - saved_path
//   - timeout (true/false)
//
// Usage (example):
//   sbt "runMain RunConsLearning.cl --examplesDir examples --targets dyck,tomita-2 --maxRounds 30 --iters 80000 --out cons_results.csv"
//
// Per-target timeout (default 5 minutes):
//   sbt "runMain RunConsLearning.cl --targets dyck,tomita-2 --timeoutMs 300000"
//
import Crasp._
import utils.Dataset
import utils.Verify
import LS._
import java.io.{File, PrintWriter}
import scala.collection.mutable
import scala.util.Try

object RunConsLearning {

  // -------------------------
  // tiny arg helpers
  // -------------------------
  private def getOpt(args: Seq[String], name: String): Option[String] = {
    val i = args.indexOf(name)
    if (i >= 0 && i + 1 < args.length) Some(args(i + 1)) else None
  }
  private def getInt(args: Seq[String], name: String, default: Int): Int =
    getOpt(args, name).flatMap(s => Try(s.toInt).toOption).getOrElse(default)
  private def getStr(args: Seq[String], name: String, default: String): String =
    getOpt(args, name).getOrElse(default)
  private def getBool(args: Seq[String], name: String, default: Boolean): Boolean =
    getOpt(args, name).map(_.toLowerCase match {
      case "1" | "true" | "yes" | "y" => true
      case "0" | "false" | "no"  | "n" => false
      case _ => default
    }).getOrElse(default)
  private def parseTargets(s: String): List[String] =
    s.split("[,\\s]+").toList.map(_.trim).filter(_.nonEmpty)

  // -------------------------
  // per-target timeout
  // -------------------------
  private def withTimeout[A](timeoutMs: Long)(thunk: => A): Either[String, A] = {
    val ex = java.util.concurrent.Executors.newSingleThreadExecutor()
    try {
      val fut = ex.submit(new java.util.concurrent.Callable[A] { def call(): A = thunk })
      try Right(fut.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS))
      catch {
        case _: java.util.concurrent.TimeoutException =>
          fut.cancel(true)
          Left(s"timeout(${timeoutMs}ms)")
      }
    } finally ex.shutdownNow()
  }

  // -------------------------
  // dataset router
  // -------------------------
  private def genByTarget(
    target: String,
    nPos: Int,
    nNeg: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int
  ): (List[String], List[String]) = target match {
    case "dyck"        => Dataset.generateDyck1(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "asbs"        => Dataset.generateAsBs(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "anbncn"      => Dataset.generateAnBnCn(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "aastar"      => Dataset.generateAAStar(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "majority"    => Dataset.generateMajority(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "exist"       => Dataset.generateExistential(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "parity"      => Dataset.generateParity(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-1"    => Dataset.generateTomita1(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-2"    => Dataset.generateTomita2(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-3"    => Dataset.generateTomita3(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-4"    => Dataset.generateTomita4(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-5"    => Dataset.generateTomita5(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-6"    => Dataset.generateTomita6(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-7"    => Dataset.generateTomita7(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "ababstar"    => Dataset.generateABABStar(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-2"  => Dataset.generatePiecewiseAB(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-3"  => Dataset.generatePiecewiseABC(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-4"  => Dataset.generatePiecewise(4, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-5"  => Dataset.generatePiecewiseABCDE(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-6"  => Dataset.generatePiecewise(6, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-7"  => Dataset.generatePiecewise(7, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-8"  => Dataset.generatePiecewise(8, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-9"  => Dataset.generatePiecewise(9, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-10" => Dataset.generatePiecewise(10, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "starabstar"  => Dataset.generateSigmaStarABSigmaStar(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "D2"          => Dataset.generateD2(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "D3"          => Dataset.generateD3(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "D4"          => Dataset.generateD4(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "D12"         => Dataset.generateD12(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "next_max"    => Dataset.generateNextAArgmax(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case _             => Dataset.generateDyck1(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
  }

  // -------------------------
  // tokenization helpers
  // -------------------------
  private def parseWord(s: String, allowSpaces: Boolean): Vector[String] = {
    val t = s.trim
    if (t.isEmpty) Vector.empty
    else if (allowSpaces && t.contains(" ")) t.split("\\s+").toVector.filter(_.nonEmpty)
    else t.toVector.map(_.toString)
  }
  private def canonStringOf(word: Vector[String]): String = word.mkString

  // -------------------------
  // accept-all seed
  // -------------------------
  private def acceptAllSeed(alphabet: Set[String], outName: String): CRASP = {
    CRASP(
      alphabet = alphabet,
      boolRules = List(
        BoolRule(outName, BoolAtomComparison(strict = false, CountAtomConst(0), CountAtomConst(0))) // 0 <= 0
      ),
      countRules = Nil
    )
  }

  // -------------------------
  // SA adaptation
  // -------------------------
  private def adaptSA(
    base: LocalSearchSA.SAConfig,
    totalSamples: Int,
    round: Int,
    outName: String
  ): LocalSearchSA.SAConfig = {

    val it = {
      val baseI = math.max(base.iters, 50_000)
      val byData = baseI + totalSamples * 2_000
      val byRound = byData + round * 1_000
      math.min(250_000, byRound)
    }
    println("[SA Adapt] iters adjusted to " + it)

    val cool =
      if (it >= 200_000) 0.99995
      else if (it >= 80_000) 0.9998
      else 0.9995

    val reheat =
      if (it >= 200_000) 2000
      else if (it >= 80_000) 3000
      else 4000

    val early = (round <= 3 && totalSamples <= 10)

    base.copy(
      outName = outName,
      iters = it,
      startTemp = math.max(1.5, base.startTemp),
      cooling = cool,
      reheatEvery = reheat,
      reheatFactor = 1.25,

      sizePenalty = if (early) 0.0003 else base.sizePenalty,
      countingPenalty = if (early) 0.0006 else base.countingPenalty,
      conditionalPenalty = if (early) 0.0008 else base.conditionalPenalty,
      depthPenalty = if (early) 0.0002 else base.depthPenalty,

      periodPenalty = base.periodPenalty,

      stallLimit = math.max(base.stallLimit, it / 6),
      tempFloor = base.tempFloor
    )
  }

  // -------------------------
  // stats
  // -------------------------
  final case class ConsStats(
    ok: Boolean,
    rounds: Int,
    localSearchMs: Long,
    verifyMs: Long,
    totalMs: Long,
    programSize: Int,
    savedPath: String,
    lastCex: String,
    timeout: Boolean
  )

  // ============================================================================================
  // Instrumented Constraint Learning using Verify.subset:
  //
  // Loop:
  //   1) learn candidate that fits current pos/neg (LevelJump)
  //   2) Verify.subset(Cand, Oracle). If true => done
  //   3) else get CEX (a word in Cand but not in Oracle) and add it to NEG, repeat
  // ============================================================================================
  private def consLearningInstrumented(
    referenceFile: String,
    config: UniversalLearnerConfig,
    maxRounds: Int,
    save_name: String,
    flag: String,             // oracle output
    outNameExact: String,      // candidate output
    allowSpaceSeparatedCex: Boolean,

    positives0: List[String],
    negatives0: List[String],

    // learner knobs
    restartsPerShape: Int,
    startK: Int,
    tryBoundary: Boolean,
    tryInterior: Boolean,

    // verifier knobs
    numCexs: Int,
    verifierRetries: Int
  ): (Option[CRASP], ConsStats) = {

    val tStart = System.nanoTime()

    // ---- Parse oracle once (to get alphabet & caps) ----
    val oracleProg: CRASP =
      CRASPParser.parseFile(referenceFile, enforceOrder = false) match {
        case Left(err) =>
          println(s"[ConsLearning] Failed to parse oracle '$referenceFile': $err")
          val stats = ConsStats(false, 0, 0L, 0L, 0L, 0, "", s"parse_error: $err", timeout = false)
          return (None, stats)
        case Right(p) => p
      }
    // println(s"[ConsLearning] Loaded oracle from $referenceFile: $oracleProg")


    val oracleEval = new CRASPEvaluator(oracleProg)
    val alphabet = oracleProg.alphabet
    if (alphabet.isEmpty) {
      println("[ConsLearning] Oracle alphabet is empty; abort.")
      val stats = ConsStats(false, 0, 0L, 0L, 0L, 0, "", "empty_alphabet", timeout = false)
      return (None, stats)
    }

    // datasets (accumulated)
    val posSet = mutable.LinkedHashSet.empty[String]
    val negSet = mutable.LinkedHashSet.empty[String]
    positives0.foreach(s => if (s != null) posSet.addOne(s.trim))
    negatives0.foreach(s => if (s != null) negSet.addOne(s.trim))

    // remove overlap
    val overlap = posSet.intersect(negSet)
    if (overlap.nonEmpty) overlap.foreach(negSet.remove)

    val seen = mutable.HashSet.empty[String]
    seen ++= posSet
    seen ++= negSet

    val outFile = new File(s"cegis/$save_name.crasp")
    if (outFile.getParentFile != null) outFile.getParentFile.mkdirs()

    var round = 0
    var bestProgram: Option[CRASP] = None

    var localSearchNs: Long = 0L
    var verifyNs: Long = 0L
    var lastCex: String = ""

    val tautologySeed = acceptAllSeed(alphabet, outNameExact)

    while (round < maxRounds) {
      println(s"--- Round $round language $save_name ---")
      println(s"[Data] pos=${posSet.size} neg=${negSet.size}")

      val positives = posSet.toList
      val negatives = negSet.toList

      // ------------------------------------------------------------
      // (1) Learn candidate (same as optimize)
      // ------------------------------------------------------------
      // ------------------------------------------------------------
      // (1) Learn candidate USING THE SAME learner as PL:
      //     LsCegis.passive_learning (fits current pos/neg perfectly)
      // ------------------------------------------------------------
      val tLS0 = System.nanoTime()

      val learnedOpt: Option[CRASP] =
        if (positives.isEmpty && negatives.isEmpty) {
          Some(tautologySeed)
        } else {
          // IMPORTANT: do NOT adaptSA / do not level-jump-search.
          // Just use the same config as PL (cfg = UniversalLearnerConfig(sa = SAConfig(iters = iters))).
          LsCegis.passive_learning(
            positives0 = positives,
            negatives0 = negatives,
            alphabet = alphabet,
            config = config,
            save_name = s"${save_name}_round$round",  // avoid overwriting if you want; optional
            outNameExact = outNameExact,
            seed = 99 + 1000 * round,
            verbose = true,
            requirePerfectFit = true
          )
        }

      val tLS1 = System.nanoTime()
      localSearchNs += (tLS1 - tLS0)

      if (learnedOpt.isEmpty) {
        println(s"[ConsLearning] Failed to learn perfect fit on current samples.")
        val tEnd = System.nanoTime()
        val stats = ConsStats(
          ok = false,
          rounds = round,
          localSearchMs = localSearchNs / 1000000L,
          verifyMs = verifyNs / 1000000L,
          totalMs = (tEnd - tStart) / 1000000L,
          programSize = 0,
          savedPath = outFile.getPath,
          lastCex = lastCex,
          timeout = false
        )
        return (bestProgram, stats)
      }

      val candidate = learnedOpt.get
      bestProgram = Some(candidate)

      // Save candidate for verifier (keep your existing outFile path!)
      val pw = new PrintWriter(outFile)
      try {
        pw.write(
          Printer.pretty(candidate, Printer.PrintCfg(outName = outNameExact, mapDigitsToLetters = false))
        )
      } finally pw.close()


     

      // ------------------------------------------------------------
      // (2) Verify subset: Cand ⊆ Oracle
      //     Retry until we get a NEW cex (avoid repeats)
      // ------------------------------------------------------------
      var tries = 0
      var isSubset = false
      var addedAny = false
      var lastCexStr: String = ""

      while (tries < verifierRetries && !isSubset && !addedAny) {
        val tV0 = System.nanoTime()
        val (ok0, cexStr0) =
          Verify.subset(outFile.getAbsolutePath, outNameExact, referenceFile, flag)
        val tV1 = System.nanoTime()
        verifyNs += (tV1 - tV0)

        isSubset = ok0
        lastCexStr = cexStr0
        lastCex = cexStr0
        if (!isSubset) {
          print(cexStr0)
          val cexs = cexStr0.split("\n").map(_.trim).filter(_.nonEmpty).take(math.max(1, numCexs))
          for (raw <- cexs) {
            val w = parseWord(raw, allowSpaceSeparatedCex)
            val key = canonStringOf(w)

            if (seen.add(key)) {
              addedAny = true

              // OPTIONAL sanity check with oracle evaluator:
              // If verifier returns subset counterexample, oracle should REJECT it.
              // But due to tokenization/EOS semantics differences, keep it non-fatal.
              val oracleTruth = oracleEval.classifyWith(flag, w)
              if (oracleTruth) {
                // weird: oracle accepts; then it's not actually a subset cex.
                // keep it in POS to avoid breaking, but you might want to print.
                println(s"[WARN] subset CEX but oracle accepts. Add to POS: $key")
                negSet.remove(key); posSet.addOne(key)
              } else {
                // expected: add to NEG
                posSet.remove(key); negSet.addOne(key)
                println(s"[Refine] Added to NEG: $key")
              }
            }
          }
        }

        tries += 1
      }

      if (isSubset) {
        println("[ConsLearning] Verified Cand ⊆ Oracle. Done.")
        val tEnd = System.nanoTime()
        val pp = Try(Printer.pretty(candidate)).getOrElse("")
        val stats = ConsStats(
          ok = true,
          rounds = round + 1,
          localSearchMs = localSearchNs / 1000000L,
          verifyMs = verifyNs / 1000000L,
          totalMs = (tEnd - tStart) / 1000000L,
          programSize = pp.length,
          savedPath = outFile.getPath,
          lastCex = "",
          timeout = false
        )
        return (bestProgram, stats)
      }

      if (!addedAny) {
        val show = if (lastCexStr == null || lastCexStr.trim.isEmpty) "<empty>" else lastCexStr.trim
        println(s"[ConsLearning] Stuck: no NEW subset counterexamples after $verifierRetries calls. lastCEX='$show'")
        val tEnd = System.nanoTime()
        val pp = Try(Printer.pretty(candidate)).getOrElse("")
        val stats = ConsStats(
          ok = false,
          rounds = round + 1,
          localSearchMs = localSearchNs / 1000000L,
          verifyMs = verifyNs / 1000000L,
          totalMs = (tEnd - tStart) / 1000000L,
          programSize = pp.length,
          savedPath = outFile.getPath,
          lastCex = show,
          timeout = false
        )
        return (bestProgram, stats)
      }

      round += 1
    }

    println("[ConsLearning] Max rounds reached.")
    val tEnd = System.nanoTime()
    val pp = bestProgram.map(p => Try(Printer.pretty(p)).getOrElse("")).getOrElse("")
    val stats = ConsStats(
      ok = false,
      rounds = maxRounds,
      localSearchMs = localSearchNs / 1000000L,
      verifyMs = verifyNs / 1000000L,
      totalMs = (tEnd - tStart) / 1000000L,
      programSize = pp.length,
      savedPath = outFile.getPath,
      lastCex = lastCex,
      timeout = false
    )
    (bestProgram, stats)
  }

  // ============================================================================================
  // MAIN: run constraint learning on many targets + write CSV (5 min timeout per target)
  // ============================================================================================
  @main def cl(args: String*): Unit = {
    val examplesDir = getStr(args, "--examplesDir", "examples")
    val targetsStr  = getStr(
      args, "--targets",
      "d2,d3,d4,dyck,asbs,anbncn,aastar,majority,exist,parity,tomita-1,tomita-2,tomita-3,tomita-4,tomita-5,tomita-6,tomita-7,ababstar,piecewise-testtable-2,piecewise-testtable-3,piecewise-testtable-4,piecewise-testtable-5,piecewise-testtable-6,piecewise-testtable-7,piecewise-testtable-8,piecewise-testtable-9,piecewise-testtable-10,starabstar"
    )
    val targets = parseTargets(targetsStr)

    // dataset knobs (initial data to fit)
    val minLen = getInt(args, "--minLen", 1)
    val maxLen = getInt(args, "--maxLen", 30)
    val nPos   = getInt(args, "--nPos", 20)
    val nNeg   = getInt(args, "--nNeg", 20)
    val seed   = getInt(args, "--seed", 42)

    // learner knobs
    val iters     = getInt(args, "--iters", 80_000)
    val maxRounds = getInt(args, "--maxRounds", 30)

    val restartsPerShape = getInt(args, "--restartsPerShape", 2)
    val startK           = getInt(args, "--startK", 1)
    val tryBoundary      = getBool(args, "--tryBoundary", default = true)
    val tryInterior      = getBool(args, "--tryInterior", default = false)

    // verifier knobs
    val verifierRetries  = getInt(args, "--verifierRetries", 3)
    val numCexs          = getInt(args, "--numCexs", 1)

    // timeout per target (default 5 minutes)
    val timeoutMs: Long = getInt(args, "--timeoutMs", 5 * 60 * 1000).toLong

    // acceptance semantics knobs
    val outName   = getStr(args, "--outName", "Out") // candidate output
    val flag      = getStr(args, "--flag", "Out")    // oracle output
    val allowSpaces = getBool(args, "--allowSpaces", default = true)

    // output
    val outCsv    = getStr(args, "--out", "cons_learning_results.csv")
    val saveProgs = getBool(args, "--saveProgs", default = true)
    val progDir   = getStr(args, "--progDir", "learned_cons_learning")
    if (saveProgs) new File(progDir).mkdirs()

    val cfg = UniversalLearnerConfig(sa = LocalSearchSA.SAConfig(iters = iters))

    val pw = new PrintWriter(new File(outCsv))
    try {
      pw.println("target,ok,rounds,ls_ms,verify_ms,total_ms,program_size,last_cex,saved_path,timeout")

      for (t <- targets) {
        val filename = s"$examplesDir/$t.crasp"
        val f = new File(filename)
        if (!f.exists()) {
          println(s"[cl] SKIP: file not found: $filename")
          pw.println(s"$t,false,0,0,0,0,0,, ,false")
          pw.flush()
        } else {
          val (trainPos, trainNeg) = genByTarget(t, nPos, nNeg, minLen, maxLen, seed)
          val saveName = s"${t}_cl"

          val runRes: Either[String, (Option[CRASP], ConsStats)] =
            withTimeout(timeoutMs) {
              consLearningInstrumented(
                referenceFile = filename,
                config = cfg,
                maxRounds = maxRounds,
                save_name = saveName,
                flag = flag,
                outNameExact = outName,
                allowSpaceSeparatedCex = allowSpaces,
                positives0 = trainPos,
                negatives0 = trainNeg,
                restartsPerShape = restartsPerShape,
                startK = startK,
                tryBoundary = tryBoundary,
                tryInterior = tryInterior,
                numCexs = numCexs,
                verifierRetries = verifierRetries
              )
            }

          runRes match {
            case Left(toMsg) =>
              println(s"[cl] TIMEOUT: $t ($toMsg)")
              pw.println(s"$t,false,0,0,0,${timeoutMs / 1000},0,timeout,,true")
              pw.flush()

            case Right((progOpt, stats0)) =>
              // save final program
              val savedPath =
                if (saveProgs && progOpt.nonEmpty) {
                  val outFile = new File(s"$progDir/$t.crasp")
                  val pp = new PrintWriter(outFile)
                  try pp.println(Printer.pretty(progOpt.get))
                  finally pp.close()
                  outFile.getPath
                } else ""

              val saved = if (savedPath.nonEmpty) savedPath else stats0.savedPath
              val stats = stats0.copy(savedPath = saved)

              println(
                s"[cl] ${t}: ok=${stats.ok} rounds=${stats.rounds} " +
                s"ls=${stats.localSearchMs}ms verify=${stats.verifyMs}ms total=${stats.totalMs}ms"
              )

              // escape commas/newlines in last_cex for CSV
              val lastCexCsv = stats.lastCex.replace("\n", " ").replace(",", ";")

              pw.println(
                s"$t,${stats.ok},${stats.rounds},${stats.localSearchMs},${stats.verifyMs},${stats.totalMs},${stats.programSize},$lastCexCsv,${stats.savedPath},${stats.timeout}"
              )
              pw.flush()
          }
        }
      }

      println(s"[cl] wrote: $outCsv")
      if (saveProgs) println(s"[cl] programs in: $progDir")
    } finally pw.close()
  }
}
