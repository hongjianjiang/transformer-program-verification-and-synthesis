// File: src/main/scala/RunOptimize.scala
//
// Experiment runner for Optimize-by-Equality-Refinement.
//
// Records per target:
//   - ok (proved equal?)
//   - rounds used
//   - local search time (ms)   [time spent inside learner]
//   - verification time (ms)   [time spent inside Verify.equal]
//   - total time (ms)
//   - final program size (pretty-printed length)
//   - saved_path
//   - timeout (true/false)
//
// Usage (example):
//   sbt "runMain RunOptimize.optimize --examplesDir examples --targets dyck,tomita-2 --maxRounds 50 --iters 80000 --out optimize_results.csv"
//
// Timeout per target (default 5 minutes):
//   sbt "runMain RunOptimize.optimize --targets dyck,tomita-2 --timeoutMs 300000"
//
import Crasp._
import utils.Dataset
import utils.Verify
import LS._
import java.io.{File, PrintWriter}
import scala.collection.mutable
import scala.util.Try

object RunOptimize {

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
  // dataset router (same targets you used in RunPL.scala)
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
    case "D2"          => Dataset.generateD2(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "D3"          => Dataset.generateD3(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "D4"          => Dataset.generateD4(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "D12"         => Dataset.generateD12(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
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
  // timeout helper (per target)
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
    } finally {
      ex.shutdownNow()
    }
  }

  // -------------------------
  // reuse adaptSA from LsCegis (copy-pasted minimal)
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
  // result struct for optimize run
  // -------------------------
  final case class OptimizeStats(
    ok: Boolean,
    rounds: Int,
    localSearchMs: Long,
    verifyMs: Long,
    totalMs: Long,
    programSize: Int,
    savedPath: String,
    timeout: Boolean
  )

  // ============================================================================================
  // Instrumented Optimize-by-Equality-Refinement
  // ============================================================================================
  private def optimizeInstrumented(
    referenceFile: String,
    config: UniversalLearnerConfig,
    maxRounds: Int,
    save_name: String,
    flag: String,
    outNameExact: String,
    allowSpaceSeparatedCex: Boolean,

    // learner knobs
    restartsPerShape: Int,
    startK: Int,
    tryBoundary: Boolean,
    tryInterior: Boolean,

    // verifier knobs
    verifierRetries: Int
  ): (Option[CRASP], OptimizeStats) = {

    val tStart = System.nanoTime()

    // ---- Parse oracle once ----
    val oracleProg: CRASP =
      CRASPParser.parseFile(referenceFile, enforceOrder = false) match {
        case Left(err) =>
          println(s"[Optimize] Failed to parse oracle '$referenceFile': $err")
          val stats = OptimizeStats(
            ok = false, rounds = 0, localSearchMs = 0L, verifyMs = 0L,
            totalMs = 0L, programSize = 0, savedPath = "", timeout = false
          )
          return (None, stats)
        case Right(p) => p
      }

    val oracleEval = new CRASPEvaluator(oracleProg)
    val alphabet = oracleProg.alphabet
    if (alphabet.isEmpty) {
      println("[Optimize] Oracle alphabet is empty; abort.")
      val stats = OptimizeStats(
        ok = false, rounds = 0, localSearchMs = 0L, verifyMs = 0L,
        totalMs = 0L, programSize = 0, savedPath = "", timeout = false
      )
      return (None, stats)
    }

    val posSet = mutable.LinkedHashSet.empty[String]
    val negSet = mutable.LinkedHashSet.empty[String]
    val seen   = mutable.HashSet.empty[String]

    val outFile = new File(s"cegis/$save_name.crasp")
    if (outFile.getParentFile != null) outFile.getParentFile.mkdirs()

    var round = 0
    var bestProgram: Option[CRASP] = None

    var localSearchNs: Long = 0L
    var verifyNs: Long = 0L

    val tautologySeed = acceptAllSeed(alphabet, outNameExact)

    while (round < maxRounds) {
      println(s"--- Round $round language $save_name ---")
      println(s"[Total] Dataset Size: ${posSet.size} pos, ${negSet.size} neg")
      println(s"Positives: " + posSet.mkString(", "))
      println(s"Negatives: " + negSet.mkString(", "))
      val positives = posSet.toList
      val negatives = negSet.toList

      // ------------------------------------------------------------
      // (1) Learn candidate (same algorithm as passive_learning)
      // ------------------------------------------------------------
      val tLS0 = System.nanoTime()
      val candidate: CRASP =
        if (positives.isEmpty && negatives.isEmpty) {
          tautologySeed
        } else {
          val totalSamples = positives.size + negatives.size
          val tuned = config.copy(
            sa = adaptSA(
              base = config.sa,
              totalSamples = totalSamples,
              round = round,
              outName = outNameExact
            )
          )

          val res = Learners.learnByLevelJumpSearch(
            positives = positives,
            negatives = negatives,
            baseConfig = tuned,
            cfg = Learners.LevelSearchCfg(
              maxBool = math.max(1, oracleProg.boolRules.size),
              maxCount = math.max(0, oracleProg.countRules.size),
              startK = startK,
              tryBoundary = tryBoundary,
              tryInterior = tryInterior,
              targetAccuracy = 1.0,
              restartsPerShape = restartsPerShape,
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
            println(f"[Optimize] Failed to learn perfect fit. Best acc=$acc%.2f%%")
            bestProgram = bestProgram.orElse(Some(res.program))

            val tEnd = System.nanoTime()
            val pp = Try(Printer.pretty(res.program)).getOrElse("")
            val stats = OptimizeStats(
              ok = false,
              rounds = round,
              localSearchMs = (localSearchNs + (tEnd - tLS0)) / 1000000L,
              verifyMs = verifyNs / 1000000L,
              totalMs = (tEnd - tStart) / 1000000L,
              programSize = pp.length,
              savedPath = outFile.getPath,
              timeout = false
            )
            return (bestProgram, stats)
          }

          res.program
        }
      val tLS1 = System.nanoTime()
      localSearchNs += (tLS1 - tLS0)

      bestProgram = Some(candidate)

      // save candidate for verifier
      val pw = new PrintWriter(outFile)
      try {
        pw.write(
          Printer.pretty(candidate, Printer.PrintCfg(outName = outNameExact, mapDigitsToLetters = false))
        )
      } finally pw.close()

      // ------------------------------------------------------------
      // (2) Equality check: retry a few times until we get NEW CEXs
      // ------------------------------------------------------------
      var tries = 0
      var eq = false
      var addedAny = false
      var lastCexStr: String = ""

      while (tries < verifierRetries && !eq && !addedAny) {
        val tV0 = System.nanoTime()
        val (eq0, cexStr0) =
          Verify.equal(outFile.getAbsolutePath, outNameExact, referenceFile, flag)
        val tV1 = System.nanoTime()
        verifyNs += (tV1 - tV0)

        eq = eq0
        lastCexStr = cexStr0

        if (!eq) {
          val cexs = cexStr0.split("\n").map(_.trim).filter(_.nonEmpty)

          for (raw <- cexs) {
            val w = parseWord(raw, allowSpaceSeparatedCex)
            val key = canonStringOf(w)

            if (seen.add(key)) {
              addedAny = true
              val oracleAccepts = oracleEval.classifyWith(flag, w)

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
        val tEnd = System.nanoTime()
        val pp = Try(Printer.pretty(candidate)).getOrElse("")
        val stats = OptimizeStats(
          ok = true,
          rounds = round + 1,
          localSearchMs = localSearchNs / 1000000L,
          verifyMs = verifyNs / 1000000L,
          totalMs = (tEnd - tStart) / 1000000L,
          programSize = pp.length,
          savedPath = outFile.getPath,
          timeout = false
        )
        return (bestProgram, stats)
      }

      if (!addedAny) {
        val show = if (lastCexStr == null || lastCexStr.trim.isEmpty) "<empty>" else lastCexStr.trim
        println(s"[Stuck] No NEW counterexamples after $verifierRetries Verify.equal calls. lastCEX='$show'")
        val tEnd = System.nanoTime()
        val pp = Try(Printer.pretty(candidate)).getOrElse("")
        val stats = OptimizeStats(
          ok = false,
          rounds = round + 1,
          localSearchMs = localSearchNs / 1000000L,
          verifyMs = verifyNs / 1000000L,
          totalMs = (tEnd - tStart) / 1000000L,
          programSize = pp.length,
          savedPath = outFile.getPath,
          timeout = false
        )
        return (bestProgram, stats)
      }

      round += 1
    }

    println("[Optimize] Max rounds reached without proving equality.")
    val tEnd = System.nanoTime()
    val pp = bestProgram.map(p => Try(Printer.pretty(p)).getOrElse("")).getOrElse("")
    val stats = OptimizeStats(
      ok = false,
      rounds = maxRounds,
      localSearchMs = localSearchNs / 1000000L,
      verifyMs = verifyNs / 1000000L,
      totalMs = (tEnd - tStart) / 1000000L,
      programSize = pp.length,
      savedPath = outFile.getPath,
      timeout = false
    )
    (bestProgram, stats)
  }

  // ============================================================================================
  // MAIN: run optimize on many targets + write CSV (5 min timeout per target by default)
  // ============================================================================================
  @main def optimize(args: String*): Unit = {
    val examplesDir = getStr(args, "--examplesDir", "examples")
    val targetsStr  = getStr(
      args, "--targets",
      "dyck,asbs,anbncn,aastar,majority,exist,parity,tomita-1,tomita-2,tomita-3,tomita-4,tomita-5,tomita-6,tomita-7,ababstar,piecewise-testtable-2,piecewise-testtable-3,piecewise-testtable-4,piecewise-testtable-5,piecewise-testtable-6,piecewise-testtable-7,piecewise-testtable-8,piecewise-testtable-9,piecewise-testtable-10,starabstar"
    )
    val targets = parseTargets(targetsStr)

    // learner config knobs
    val iters     = getInt(args, "--iters", 80_000)
    val maxRounds = getInt(args, "--maxRounds", 50)

    val restartsPerShape = getInt(args, "--restartsPerShape", 1)
    val startK           = getInt(args, "--startK", 1)
    val tryBoundary      = getBool(args, "--tryBoundary", default = true)
    val tryInterior      = getBool(args, "--tryInterior", default = false)

    // verifier knobs
    val verifierRetries  = getInt(args, "--verifierRetries", 5)

    // output naming
    val outCsv    = getStr(args, "--out", "optimize_results.csv")
    val saveProgs = getBool(args, "--saveProgs", default = true)
    val progDir   = getStr(args, "--progDir", "learned_optimize")

    val outName   = getStr(args, "--outName", "Out")    // candidate output name
    val flag      = getStr(args, "--flag", "Out")       // oracle output name

    val allowSpaces = getBool(args, "--allowSpaces", default = true)

    // NEW: per-target timeout (default 5 minutes)
    val timeoutMs: Long = getInt(args, "--timeoutMs", 5 * 60 * 1000).toLong

    if (saveProgs) new File(progDir).mkdirs()

    val cfg = UniversalLearnerConfig(sa = LocalSearchSA.SAConfig(iters = iters))

    val pw = new PrintWriter(new File(outCsv))
    try {
      pw.println("target,ok,rounds,ls_ms,verify_ms,total_ms,program_size,saved_path,timeout")

      for (t <- targets) {
        val filename = s"$examplesDir/$t.crasp"
        val f = new File(filename)

        if (!f.exists()) {
          println(s"[train_optimize_all] SKIP: file not found: $filename")
          pw.println(s"$t,false,0,0,0,0,0,,false")
          pw.flush()
        } else {
          val saveName = s"${t}_optimize"

          val runRes: Either[String, (Option[CRASP], OptimizeStats)] =
            withTimeout(timeoutMs) {
              optimizeInstrumented(
                referenceFile = filename,
                config = cfg,
                maxRounds = maxRounds,
                save_name = saveName,
                flag = flag,
                outNameExact = outName,
                allowSpaceSeparatedCex = allowSpaces,
                restartsPerShape = restartsPerShape,
                startK = startK,
                tryBoundary = tryBoundary,
                tryInterior = tryInterior,
                verifierRetries = verifierRetries
              )
            }

          runRes match {
            case Left(toMsg) =>
              println(s"[train_optimize_all] TIMEOUT: $t ($toMsg)")
              pw.println(s"$t,false,0,0,0,${timeoutMs / 1000},0,,true")
              pw.flush()

            case Right((progOpt, stats0)) =>
              // save final program to progDir as a convenience
              val savedPath =
                if (saveProgs && progOpt.nonEmpty) {
                  val outFile = new File(s"$progDir/$t.crasp")
                  val pp = new PrintWriter(outFile)
                  try pp.println(Printer.pretty(progOpt.get))
                  finally pp.close()
                  outFile.getPath
                } else ""

              val saved = if (savedPath.nonEmpty) savedPath else stats0.savedPath

              val stats = stats0.copy(savedPath = saved, timeout = false)

              println(
                s"[train_optimize_all] ${t}: ok=${stats.ok} rounds=${stats.rounds} " +
                s"ls=${stats.localSearchMs}ms verify=${stats.verifyMs}ms total=${stats.totalMs}ms"
              )

              pw.println(
                s"$t,${stats.ok},${stats.rounds},${stats.localSearchMs},${stats.verifyMs},${stats.totalMs},${stats.programSize},${stats.savedPath},${stats.timeout}"
              )
              pw.flush()
          }
        }
      }

      println(s"[train_optimize_all] wrote: $outCsv")
      if (saveProgs) println(s"[train_optimize_all] programs in: $progDir")
    } finally {
      pw.close()
    }
  }
}
