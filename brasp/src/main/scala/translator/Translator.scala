package brasp

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** Compile Section 5 Boolean B-RASP JSON programs into shared 2LTL DAGs. */

/** The requested B-RASP construct has no direct compiler implementation. */
final case class TranslationError(message: String) extends RuntimeException(message)

private final case class CliParseError(message: String) extends RuntimeException(message)

final case class CliArgs(
    input: File,
    future: Boolean = false,
    oneVariable: Boolean = false,
    timing: Boolean = false,
    json: Boolean = false,
    ltl: Boolean = false,
    ltlf: Boolean = false,
    brasp: Boolean = false,
    word: Option[String] = None,
    words: Option[File] = None,
)

object Translator:

  /** Return the strict-future 2LTL formula for the reverse language. */
  def mirrorToFuture(past: FormulaDag): FormulaDag =
    try Ltl.mirrorDag(past)
    catch case LtlError(message) => throw TranslationError(message)

  def renderProgram(dag: FormulaDag): String =
    val lines = dag.definitions.toVector.map { case (name, formula) => s"$name(i) := ${Ltl.render(formula)}" }
    (lines :+ "" :+ s"output := ${Ltl.render(dag.output)}" :+ s"evaluate at ${dag.evaluationPoint}").mkString("\n")

  private def parseArgs(args: Array[String]): CliArgs =
    var input: Option[File] = None
    var future = false
    var oneVariable = false
    var timing = false
    var jsonOut = false
    var ltlOut = false
    var ltlfOut = false
    var braspOut = false
    var word: Option[String] = None
    var words: Option[File] = None

    def fail(message: String): Nothing = throw CliParseError(message)
    def takeValue(flag: String, it: Iterator[String]): String =
      if !it.hasNext then fail(s"argument $flag: expected one argument") else it.next()

    val it = args.iterator
    while it.hasNext do
      it.next() match
        case "--future"       => future = true
        case "--one-variable" => oneVariable = true
        case "--timing"       => timing = true
        case "--json"         => jsonOut = true
        case "--ltl"          => ltlOut = true
        case "--ltlf"         => ltlfOut = true
        case "--brasp"        => braspOut = true
        case "--word"         => word = Some(takeValue("--word", it))
        case "--words"        => words = Some(File(takeValue("--words", it)))
        case other if other.startsWith("-") && other != "-" => fail(s"unrecognized arguments: $other")
        case other =>
          if input.isDefined then fail(s"unrecognized arguments: $other")
          input = Some(File(other))

    val resolvedInput = input.getOrElse(fail("the following arguments are required: input"))
    CliArgs(
      input = resolvedInput,
      future = future,
      oneVariable = oneVariable,
      timing = timing,
      json = jsonOut,
      ltl = ltlOut,
      ltlf = ltlfOut,
      brasp = braspOut,
      word = word,
      words = words,
    )

  private def readFile(file: File): String =
    Files.readString(file.toPath, StandardCharsets.UTF_8)

  private val graphSourceExtensions = List(".brasp", ".json", ".ltl")

  private def inputStem(input: File): String =
    val name = input.getName
    graphSourceExtensions.find(name.endsWith).fold(name)(ext => name.dropRight(ext.length))

  /** Where the compiled 2LTL formula auto-saves: `ltl/<input stem>[_suffix].ltl`.
    * `suffix` disambiguates the strict-future mirror from the strict-past
    * original for the same input stem.
    */
  private def ltlOutputPath(input: File, suffix: String = ""): File =
    val tag = if suffix.isEmpty then "" else s"_$suffix"
    new File("ltl", s"${inputStem(input)}$tag.ltl")

  /** Where `--brasp` auto-saves a copy: `examples/brasp/<input stem>.brasp`.
    * Note this can share a path with a hand-written example source of the
    * same input stem (e.g. `at_least_two_a`), in which case the round-trip
    * output overwrites it.
    */
  private def braspOutputPath(input: File): File =
    new File("examples/brasp", s"${inputStem(input)}.brasp")

  private def saveArtifact(outFile: File, content: String): Unit =
    Option(outFile.getParentFile).foreach(_.mkdirs())
    Files.writeString(outFile.toPath, content)
    System.err.println(s"Saved $outFile")

  /** `saveArtifact`, but reporting an `IOException` as a CLI exit code
    * instead of raising: `Some(2)` on failure (already printed to stderr),
    * `None` on success.
    */
  private def trySaveArtifact(outFile: File, content: String): Option[Int] =
    try
      saveArtifact(outFile, content)
      None
    catch
      case error: java.io.IOException =>
        System.err.println(s"translator: could not save ${outFile.getName}: ${error.getMessage}")
        Some(2)

  /** Load a B-RASP program, dispatching on file extension: `.brasp` files
    * use the textual syntax (`BraspText`), everything else is parsed as
    * JSON (`Brasp.fromJson`).
    */
  private def isLtlFile(file: File): Boolean = file.getName.endsWith(".ltl")

  private def loadProgram(file: File): Program =
    if isLtlFile(file) then
      throw TranslationError(s"$file: '.ltl' is a compiled-formula file, not a B-RASP program; use .brasp or JSON here")
    val text = readFile(file)
    if file.getName.endsWith(".brasp") then BraspText.parse(text)
    else Brasp.fromJson(Json.parse(text))

  /** Mirror to strict-future 2LTL only if not already there: a `.ltl` file
    * may be loaded directly at either logic, unlike a freshly compiled
    * B-RASP program (always strict-past).
    */
  private def toFuture(dag: FormulaDag): FormulaDag =
    if dag.logic == Logic.FutureStrict then dag else mirrorToFuture(dag)

  /** Split `--word` text into symbols: whitespace-separated if it contains
    * whitespace (for multi-character symbols), otherwise one symbol per
    * character (for the common single-character-alphabet case) — unless
    * the whole trimmed text is itself exactly one of `alphabet`'s symbols,
    * in which case it's a length-1 word of that symbol. Without that
    * check, a length-1 word could never be written for a multi-character
    * alphabet: e.g. alphabet `{111, 000}` and `--word 111` would
    * char-split into `1`, `1`, `1` — none of them valid symbols — since
    * there's no whitespace to signal "don't split per character", and
    * trimming means padding with a stray space doesn't help either.
    */
  private def splitWord(text: String, alphabet: List[String]): IndexedSeq[String] =
    val trimmed = text.trim
    if trimmed.isEmpty then IndexedSeq.empty
    else if alphabet.contains(trimmed) then IndexedSeq(trimmed)
    else if trimmed.exists(_.isWhitespace) then trimmed.split("\\s+").toIndexedSeq
    else trimmed.map(_.toString).toIndexedSeq

  /** Wall-clock compile time for `--timing`, in nanoseconds: in-process,
    * so it excludes JVM startup and class loading. Reported once, by `run`,
    * as one line on stderr so it never mixes into a redirected result.
    */
  private object Phases:
    var enabled = false
    var compileNs = 0L

    // Locale.ROOT: a decimal point regardless of the host locale, so the
    // line stays machine-readable when a benchmark script parses it.
    private def seconds(ns: Long): String = String.format(java.util.Locale.ROOT, "%.2f", ns / 1e9)

    def report(enabled: Boolean): Unit =
      if enabled then System.err.println(s"timing: compile ${seconds(compileNs)}s  total ${seconds(compileNs)}s")

  def run(args: Array[String]): Int =
    try runStages(args)
    finally Phases.report(Phases.enabled)

  private def runStages(args: Array[String]): Int =
    val parsed =
      try parseArgs(args)
      catch
        case CliParseError(message) =>
          System.err.println(s"translator: error: $message")
          return 2
    Phases.enabled = parsed.timing

    val compileStart = System.nanoTime()
    val dag: FormulaDag =
      try
        val translated: FormulaDag =
          if isLtlFile(parsed.input) then LtlText.parse(readFile(parsed.input))
          else BraspToLtl.translateProgram(loadProgram(parsed.input))

        // The classical baseline: compile the second variable away first,
        // paying the exponential up front, and hand the backends an ordinary
        // one-variable formula.
        val compiled0 =
          if parsed.oneVariable then
            try TwoLtlToOneVariable.translate(translated)
            catch
              case TwoLtlToOneVariable.TranslationTooLarge(message) =>
                System.err.println(s"translator: $message")
                return 2
          else translated

        if parsed.future || parsed.ltlf then toFuture(compiled0) else compiled0
      catch
        case error: java.io.IOException =>
          System.err.println(s"translator: ${error.getMessage}")
          return 2
        case JsonError(message) =>
          System.err.println(s"translator: $message")
          return 2
        case BraspError(message) =>
          System.err.println(s"translator: $message")
          return 2
        case TranslationError(message) =>
          System.err.println(s"translator: $message")
          return 2
        case LtlError(message) =>
          System.err.println(s"translator: $message")
          return 2
    Phases.compileNs = System.nanoTime() - compileStart

    if parsed.word.isDefined || parsed.words.isDefined then
      def accepts(text: String): Boolean =
        val word = splitWord(text, dag.alphabet.getOrElse(Nil))
        Ltl.evaluate(dag, if dag.logic == Logic.FutureStrict then word.reverse else word)
      // `--words FILE`: one word per line (an empty line is the empty
      // word), one answer per line, so a batch costs one JVM start.
      val texts = parsed.words match
        case Some(file) =>
          val body = readFile(file)
          if body.isEmpty then Nil
          else body.stripSuffix("\n").split("\n", -1).toList
        case None => List(parsed.word.get)
      texts.foreach(text => println(accepts(text)))
      return 0

    if parsed.ltlf then
      // One line on stdout and no saved artifact: this output exists to be
      // piped straight into an LTLf satisfiability checker.
      try println(LtlfExport.render(dag))
      catch
        case LtlfExportError(message) =>
          System.err.println(s"translator: $message")
          return 2
    else if parsed.brasp then
      try
        val rendered = BraspText.render(LtlToBrasp.translate(dag))
        trySaveArtifact(braspOutputPath(parsed.input), rendered) match
          case Some(code) => return code
          case None       => ()
        println(rendered)
      catch
        case LtlToBrasp.ReverseTranslationError(message) =>
          System.err.println(s"translator: $message")
          return 2
        case BraspError(message) =>
          System.err.println(s"translator: $message")
          return 2
    else
      val ltlSuffix = if dag.logic == Logic.FutureStrict then "future" else ""
      trySaveArtifact(ltlOutputPath(parsed.input, suffix = ltlSuffix), LtlText.render(dag)) match
        case Some(code) => return code
        case None       => ()
      println(
        if parsed.ltl then LtlText.render(dag)
        else if parsed.json then Json.render(Ltl.dagToJson(dag))
        else renderProgram(dag)
      )
    0

  /** `run`'s work happens on a dedicated thread with a much larger stack
    * than the JVM default (~512KB-1MB): some benchmark formulas are
    * legitimately deep, not just wide — the `two_var/monotone_past` family
    * builds one `Formula` subtree nesting `!(... & pairwise-combination
    * ...)` to `O(sigma^2)` levels (~860 for `sigma=42`), deep enough to
    * overflow the default stack in `Ltl.mirror`'s ordinary recursive
    * descent well before any backend's own size/complexity limit would
    * ever reject it. `-Xss` on the `java` command line would also fix
    * this, but only if the caller remembers to pass it; doing it here
    * means the packaged jar just works without one.
    */
  def main(args: Array[String]): Unit =
    var exitCode = 1
    val worker = new Thread(
      null,
      () =>
        exitCode =
          try run(args)
          catch
            case _: StackOverflowError =>
              System.err.println(
                "translator: error: formula is too deeply nested for this process's stack (even after " +
                  "raising it well past the JVM default) — this is a recursion-depth limit in the " +
                  "compiler's own AST walk, not a rejection from any backend's own size check"
              )
              1
      ,
      "brasp-worker",
      256L * 1024 * 1024,
    )
    worker.start()
    worker.join()
    System.exit(exitCode)
