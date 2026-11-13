package brasp.synthesis

import brasp.BraspText
import java.nio.file.{Files, Path}
import LocalSearch.*

/** Separate entry point so existing verification commands remain compatible. */
object RunSynthesis:
  private val usage = """Usage: brasp.synthesis.RunSynthesis --data FILE --alphabet a,b --out FILE
    |  [--seed N] [--iters N] [--restarts N] [--slots N] [--depth N] [--tokens] [--no-balanced]
    |Dataset: one +,word or -,word per line; +, and -, denote the empty word.
    |--tokens interprets words as whitespace-separated symbols instead of characters.
    |Search weights errors by class size (FN/#pos + FP/#neg); --no-balanced counts raw errors.
    |Exit codes: 0 = fits samples, 2 = search exhausted, 1 = invalid input.
    |The best candidate is saved even when the search exhausts its budget.
    |Sample consistency does not establish language equivalence.
    |""".stripMargin

  def parseDataset(source: String, tokens: Boolean): Vector[Example] =
    source.linesIterator.zipWithIndex.filter(_._1.trim.nonEmpty).map { (raw, index) =>
      val line = raw.trim
      require(line.startsWith("+,") || line.startsWith("-,"), s"line ${index + 1}: expected +,word or -,word")
      val word = line.drop(2).trim
      val symbols = if word.isEmpty then Vector.empty
        else if tokens then word.split("\\s+").toVector
        else word.codePoints().toArray.toVector.map(cp => new String(Character.toChars(cp)))
      Example(symbols, line.head == '+')
    }.toVector

  def run(args: Array[String]): Int =
    if args.contains("--help") then
      println(usage)
      return 0
    try
      var options = Map.empty[String, String]
      var tokens = false
      var balanced = true
      var i = 0
      val keys = Set("--data", "--alphabet", "--out", "--seed", "--iters", "--restarts", "--slots", "--depth")
      while i < args.length do
        if args(i) == "--tokens" then
          tokens = true
          i += 1
        else if args(i) == "--balanced" || args(i) == "--no-balanced" then
          balanced = args(i) == "--balanced"
          i += 1
        else
          require(keys(args(i)) && i + 1 < args.length, s"unknown option or missing value: ${args(i)}")
          require(!options.contains(args(i)), s"duplicate option: ${args(i)}")
          options += args(i) -> args(i + 1)
          i += 2
      def required(key: String): String = options.getOrElse(key, throw new IllegalArgumentException(s"missing $key"))
      val alphabet = required("--alphabet").split(",", -1).toVector.map(_.trim)
      val data = parseDataset(Files.readString(Path.of(required("--data"))), tokens)
      val output = Path.of(required("--out"))
      val defaults = Config()
      def int(key: String, default: Int): Int = options.get(key).fold(default)(_.toInt)
      val result = learn(alphabet, data, Config(
        seed = options.get("--seed").fold(defaults.seed)(_.toLong),
        iterations = int("--iters", defaults.iterations),
        restarts = int("--restarts", defaults.restarts),
        slots = int("--slots", defaults.slots),
        expressionDepth = int("--depth", defaults.expressionDepth),
        balanced = balanced,
      ))
      // Check that the requested symbols survive the existing textual front end.
      val rendered = BraspText.render(result.program)
      require(BraspText.parse(rendered) == result.program, "alphabet cannot be represented in .brasp text")
      Option(output.getParent).foreach(Files.createDirectories(_))
      Files.writeString(output, rendered + "\n")
      val status = if result.fitsSamples then "FITS SAMPLES" else "SEARCH EXHAUSTED"
      System.err.println(s"$status: ${result.errors}/${result.samples} errors, ${result.evaluations} evaluations; saved $output")
      if result.fitsSamples then 0 else 2
    catch
      case e: IllegalArgumentException => System.err.println(e.getMessage + "\n" + usage); 1
      case e: java.io.IOException => System.err.println(e.getMessage); 1
      case e: brasp.BraspError => System.err.println(e.getMessage); 1

  def main(args: Array[String]): Unit = System.exit(run(args))
