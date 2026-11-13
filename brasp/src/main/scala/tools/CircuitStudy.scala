package brasp

import java.nio.file.{Files, Path}

/** Explicit routes for experiments. Production auto routing is unchanged. */
object CircuitStudy:
  /** Runs on a large-stack worker thread, as `Translator.main` does: the
    * one-variable routes walk formulas nested thousands of levels deep, which
    * overflows the JVM's default main-thread stack.
    */
  def main(args: Array[String]): Unit =
    var failure: Option[Throwable] = None
    val worker = new Thread(null, () => try run(args) catch case error: Throwable => failure = Some(error),
      "circuit-study-worker", 256L * 1024 * 1024)
    worker.start()
    worker.join()
    failure.foreach(error => throw error)

  private def run(args: Array[String]): Unit =
    if args.length == 4 && args(2) == "direct-equivalent" then
      runDirectEquivalent(args(0), args(1), args(3))
      return
    require(args.length == 3, "CircuitStudy INPUT ROUTE OUTPUT.aig  |  CircuitStudy SPEC LEARNED direct-equivalent OUTPUT.aig")
    val start = System.nanoTime()
    val input = Path.of(args(0))
    val text = Files.readString(input)
    val parsed = if args(0).endsWith(".ltl") then LtlText.parse(text)
      else BraspToLtl.translateProgram(BraspText.parse(text))
    val frontendSeconds = (System.nanoTime() - start) / 1e9
    val route = args(1)
    if route == "one-variable-shared" || route == "one-variable-realizable" then
      val past = if parsed.logic == Logic.PastStrict then parsed else Ltl.mirrorToPast(parsed)
      val eliminationStart = System.nanoTime()
      val translated = SharedOneVariableLtl.translate(past, reduceRealizable = route == "one-variable-realizable")
      val eliminationSeconds = (System.nanoTime() - eliminationStart) / 1e9
      val compileSeconds = (System.nanoTime() - start) / 1e9
      // On ordinary LTL this backend is just the standard temporal recurrence:
      // every temporal row has one cell, with no conditional query coordinates.
      val circuitStart = System.nanoTime()
      val result = DirectSummary.generate(translated.dag, reduceRealizable = false)
      val circuitSeconds = (System.nanoTime() - circuitStart) / 1e9
      require(result.maxSupport == 0, "variable elimination left a query-dependent temporal operand")
      Files.write(Path.of(args(2)), result.model)
      val header = new String(result.model.takeWhile(_ != '\n'.toByte), java.nio.charset.StandardCharsets.US_ASCII)
      val seconds = (System.nanoTime() - start) / 1e9
      println(s"route=$route rows=${result.rows} max_support=${result.maxSupport} support_cells=${translated.supportCases} realizable_cells=${translated.retainedCases} compile_seconds=$compileSeconds compile_encode_seconds=$seconds header=$header")
      println(s"frontend_seconds=$frontendSeconds elimination_seconds=$eliminationSeconds circuit_seconds=$circuitSeconds")
      return
    if route == "direct-support" || route == "direct-realizable" then
      val past = if parsed.logic == Logic.PastStrict then parsed else Ltl.mirrorToPast(parsed)
      val circuitStart = System.nanoTime()
      val result = DirectSummary.generate(past, reduceRealizable = route == "direct-realizable")
      val circuitSeconds = (System.nanoTime() - circuitStart) / 1e9
      Files.write(Path.of(args(2)), result.model)
      val header = new String(result.model.takeWhile(_ != '\n'.toByte), java.nio.charset.StandardCharsets.US_ASCII)
      val seconds = (System.nanoTime() - start) / 1e9
      println(s"route=$route rows=${result.rows} max_support=${result.maxSupport} support_cells=${result.supportCells} realizable_cells=${result.retainedCells} compile_encode_seconds=$seconds header=$header")
      println(s"frontend_seconds=$frontendSeconds elimination_seconds=0.0 circuit_seconds=$circuitSeconds")
      return
    if route == "one-variable" then
      // The elimination baseline: 2LTL -> one-variable past LTL
      // (`TwoLtlToOneVariable`, with the realizability cover) -> circuit. With
      // no anchor reference left inside any temporal operand, every summary
      // row has an empty support, so `DirectSummary` emits the standard
      // one-latch-per-temporal-subformula circuit of a past-LTL formula.
      val past = if parsed.logic == Logic.PastStrict then parsed else Ltl.mirrorToPast(parsed)
      val eliminationStart = System.nanoTime()
      val one =
        try TwoLtlToOneVariable.translate(past)
        catch
          case TwoLtlToOneVariable.TranslationTooLarge(message) =>
            System.err.println(s"translator: $message")
            sys.exit(2)
      val eliminationSeconds = (System.nanoTime() - eliminationStart) / 1e9
      val circuitStart = System.nanoTime()
      val result = DirectSummary.generate(one, reduceRealizable = false)
      val circuitSeconds = (System.nanoTime() - circuitStart) / 1e9
      require(result.maxSupport == 0, "variable elimination left a query-dependent temporal operand")
      Files.write(Path.of(args(2)), result.model)
      val header = new String(result.model.takeWhile(_ != '\n'.toByte), java.nio.charset.StandardCharsets.US_ASCII)
      val seconds = (System.nanoTime() - start) / 1e9
      println(s"route=$route rows=${result.rows} states=${result.rows} goto=0 max_support=0 compile_encode_seconds=$seconds header=$header")
      println(s"frontend_seconds=$frontendSeconds elimination_seconds=$eliminationSeconds circuit_seconds=$circuitSeconds")
      return
    throw IllegalArgumentException(
      s"unknown route: $route (direct-realizable, direct-support, one-variable, one-variable-shared, one-variable-realizable)")

  /** Equivalence via the direct route: build `L(spec) xor L(learned)` as a single
    * B-RASP program (`Inclusion.equivalenceCounterexampleProgram`) and compile *that*
    * with `DirectSummary`, exactly as `direct-realizable` does for one program.
    * Its emptiness is spec/learned equivalence; ABC finding it nonempty exhibits a
    * word where the two disagree. Both inputs must be `.brasp` (the counterexample
    * reduction is defined over B-RASP programs, not raw `.ltl`).
    */
  private def runDirectEquivalent(specPath: String, learnedPath: String, outputPath: String): Unit =
    val start = System.nanoTime()
    val spec = BraspText.parse(Files.readString(Path.of(specPath)))
    val learned = BraspText.parse(Files.readString(Path.of(learnedPath)))
    val combined = Inclusion.equivalenceCounterexampleProgram(spec, learned)
    val translated = BraspToLtl.translateProgram(combined)
    val frontendSeconds = (System.nanoTime() - start) / 1e9
    val past = if translated.logic == Logic.PastStrict then translated else Ltl.mirrorToPast(translated)
    val circuitStart = System.nanoTime()
    val result = DirectSummary.generate(past, reduceRealizable = true)
    val circuitSeconds = (System.nanoTime() - circuitStart) / 1e9
    Files.write(Path.of(outputPath), result.model)
    val header = new String(result.model.takeWhile(_ != '\n'.toByte), java.nio.charset.StandardCharsets.US_ASCII)
    val seconds = (System.nanoTime() - start) / 1e9
    println(s"route=direct-equivalent rows=${result.rows} max_support=${result.maxSupport} support_cells=${result.supportCells} realizable_cells=${result.retainedCells} compile_encode_seconds=$seconds header=$header")
    println(s"frontend_seconds=$frontendSeconds elimination_seconds=0.0 circuit_seconds=$circuitSeconds")
