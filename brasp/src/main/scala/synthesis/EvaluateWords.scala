package brasp.synthesis

import brasp.{Brasp, BraspText}
import java.nio.file.{Files, Path}

/** Batch membership with the learner's own semantics (`Brasp.accepts`).
  *
  * One word per line (an empty line is the empty word), one `true`/`false`
  * per line. Words are split into Unicode characters, or on whitespace with
  * `--tokens`, matching `RunSynthesis`. This direct evaluator is
  * O(|program| * n^2) per word, unlike the LTL route in `Translator --words`,
  * whose naive recursion grows with formula nesting depth.
  */
object EvaluateWords:
  def run(args: Array[String]): Int =
    val tokens = args.contains("--tokens")
    val rest = args.filterNot(_ == "--tokens")
    if rest.length != 2 then
      System.err.println("Usage: brasp.synthesis.EvaluateWords PROGRAM.brasp WORDS_FILE [--tokens]")
      return 1
    try
      val program = BraspText.parse(Files.readString(Path.of(rest(0))))
      val body = Files.readString(Path.of(rest(1)))
      val lines = if body.isEmpty then Nil else body.stripSuffix("\n").split("\n", -1).toList
      val out = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.OutputStreamWriter(System.out)))
      for line <- lines do
        val word = if line.isEmpty then Vector.empty
          else if tokens then line.trim.split("\\s+").toVector
          else line.codePoints().toArray.toVector.map(cp => new String(Character.toChars(cp)))
        out.println(Brasp.accepts(program, word))
      out.flush()
      0
    catch
      case e: java.io.IOException => System.err.println(e.getMessage); 1
      case e: brasp.BraspError   => System.err.println(e.getMessage); 1

  def main(args: Array[String]): Unit = System.exit(run(args))
