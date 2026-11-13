package utils

import scala.sys.process._
import java.io.File

object Verify {

  // ----------------------------
  // small helpers
  // ----------------------------
  private def absPath(path: String): String = new File(path).getAbsolutePath

  /** If word is like "abba", turn into "a b b a". If already space-separated, keep it. */
  private def normalizeWord(word: String): String = {
    val w = word.trim
    if (w.isEmpty) w
    else if (w.contains(" ")) w
    else w.toCharArray.map(_.toString).mkString(" ")
  }

  // ----------------------------
  // Generic verifier runner (for check/equal/subset/disjoint/robust)
  // Expects "No counter example" or "Counter example: ..."
  // ----------------------------
  private def runCommand(cmd: Seq[String]): (Boolean, String) = {
    val dir = new File("rasp-translation")
    try {
      val out = Process(cmd, dir).!!.trim

      if (out.contains("No counter example")) {
        (true, "")
      } else {
        val prefix = "Counter example: "
        val cexLines = out.linesIterator.filter(_.startsWith(prefix)).toList
        if (cexLines.nonEmpty) {
          val cleanCexs = cexLines.map { l =>
            val raw = l.stripPrefix(prefix).trim
            raw.replace("__eos__", "").trim
          }
          (false, cleanCexs.mkString("\n"))
        } else {
          throw new RuntimeException(s"Unexpected output from verification tool:\n$out")
        }
      }
    } catch {
      case e: Exception =>
        throw new RuntimeException(s"Verification tool failed to run: ${e.getMessage}")
    }
  }

  // ----------------------------
  // Word acceptance (special: accept prints "Accepted"/"Rejected" in your Python)
  // ----------------------------
  /**
   * Check if the given word is accepted by the C-RASP program (output rule true).
   *
   * This calls:
   *   poetry run main --config=config_example.toml accept <crasp> <output> "<a b c>"
   *
   * Supported Python outputs:
   *   - "Accepted" / "Rejected"
   *   - "True" / "False"
   *   - (fallback) "No counter example" / "Counter example: ..." (if you change it later)
   */
  def accept(crasp: String, output: String, word: String): Boolean = {
    val spaced = normalizeWord(word)

    val cmd = Seq(
      "poetry", "run", "main",
      "--config=config_example.toml",
      "accept",
      absPath(crasp),
      output,
      spaced
    )

    val dir = new File("rasp-translation")
    try {
      val out = Process(cmd, dir).!!.trim.toLowerCase

      // Preferred: explicit accept/reject
      if (out.contains("accepted")) return true
      if (out.contains("rejected")) return false

      // Alternative: boolean print
      if (out == "true") return true
      if (out == "false") return false

      // Fallback: if accept was implemented via property checking output
      if (out.contains("no counter example")) return false
      if (out.contains("counter example:")) return true

      throw new RuntimeException(s"Unexpected output from accept:\n$out")
    } catch {
      case e: Exception =>
        throw new RuntimeException(s"Verification tool failed to run: ${e.getMessage}")
    }
  }

  // ----------------------------
  // Other checks (unchanged)
  // ----------------------------
  def check(crasp: String, output: String): (Boolean, String) = {
    val cmd = Seq("poetry", "run", "main", "--config=config_example.toml", "check", absPath(crasp), output)
    runCommand(cmd)
  }

  def equal(crasp1: String, output1: String, crasp2: String, output2: String): (Boolean, String) = {
    val cmd = Seq(
      "poetry", "run", "main", "--config=config_example.toml",
      "equal",
      absPath(crasp1), output1,
      absPath(crasp2), output2,
      "--num_cexs", "1"
    )
    runCommand(cmd)
  }

  def subset(crasp1: String, output1: String, crasp2: String, output2: String): (Boolean, String) = {
    val cmd = Seq(
      "poetry", "run", "main", "--config=config_example.toml",
      "subset",
      absPath(crasp1), output1,
      absPath(crasp2), output2,
      "--num_cexs", "1"
    )
    runCommand(cmd)
  }

  def disjoint(crasp1: String, output1: String, crasp2: String, output2: String): (Boolean, String) = {
    val cmd = Seq(
      "poetry", "run", "main", "--config=config_example.toml",
      "disjoint",
      absPath(crasp1), output1,
      absPath(crasp2), output2,
      "--num_cexs", "10"
    )
    runCommand(cmd)
  }

  def robustHamming(crasp: String, output: String, distance: Int, word: String): (Boolean, String) = {
    val cmd = Seq(
      "poetry", "run", "main", "--config=config_example.toml",
      "robust",
      absPath(crasp), output, "hamming", distance.toString,
      normalizeWord(word)
    )
    runCommand(cmd)
  }

  // ----------------------------
  // Lustre program generation (no verification)
  // ----------------------------
  /**
   * Return the Lustre program that the `equal`/`subset`/`disjoint` checks
   * verify, without running Kind2. `comparison` is one of "equal",
   * "subset", "disjoint".
   */
  def translateCompare(
      crasp1: String,
      output1: String,
      crasp2: String,
      output2: String,
      comparison: String = "equal",
      outFile: Option[String] = None
  ): String = {
    val base = Seq(
      "poetry", "run", "main",
      "translate_compare",
      absPath(crasp1), output1,
      absPath(crasp2), output2,
      s"--comparison=$comparison"
    )
    val cmd = outFile match {
      case Some(f) => base ++ Seq(s"--out=${absPath(f)}")
      case None    => base
    }

    val dir = new File("rasp-translation")
    try {
      Process(cmd, dir).!!
    } catch {
      case e: Exception =>
        throw new RuntimeException(s"Verification tool failed to run: ${e.getMessage}")
    }
  }

  // ----------------------------
  // CLI wrapper
  // ----------------------------
  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      println("Usage: Verify <command> <args...>")
      println("Commands: check, equal, subset, disjoint, robust, accept, translate_equal")
      System.exit(1)
    }

    val command = args(0)
    val rest = args.drop(1)

    try {
      command match {
        case "check" if rest.length == 2 =>
          val (success, cex) = check(rest(0), rest(1))
          if (success) println("Verification successful: Property holds.")
          else println(s"Verification failed. Counter example: $cex")

        case "equal" if rest.length == 4 =>
          val (success, cex) = equal(rest(0), rest(1), rest(2), rest(3))
          if (success) println("Verification successful: Property holds.")
          else println(s"Verification failed. Counter example: $cex")

        case "subset" if rest.length == 4 =>
          val (success, cex) = subset(rest(0), rest(1), rest(2), rest(3))
          if (success) println("Verification successful: Property holds.")
          else println(s"Verification failed. Counter example: $cex")

        case "disjoint" if rest.length == 4 =>
          val (success, cex) = disjoint(rest(0), rest(1), rest(2), rest(3))
          if (success) println("Verification successful: Property holds.")
          else println(s"Verification failed. Counter example: $cex")

        case "robust" if rest.length == 4 =>
          val (success, cex) = robustHamming(rest(0), rest(1), rest(2).toInt, rest(3))
          if (success) println("Verification successful: Property holds.")
          else println(s"Verification failed. Counter example: $cex")

        case "accept" if rest.length == 3 =>
          val accepted = accept(rest(0), rest(1), rest(2))
          println(accepted)

        case "translate_equal" if rest.length == 4 || rest.length == 5 =>
          val out = if (rest.length == 5) Some(rest(4)) else None
          val lustre = translateCompare(rest(0), rest(1), rest(2), rest(3), "equal", out)
          out match {
            case Some(f) => println(s"Lustre program written to $f")
            case None    => println(lustre)
          }

        case _ =>
          println(s"Invalid command or arguments for '$command'")
          System.exit(1)
      }
    } catch {
      case e: Exception =>
        println(s"Error: ${e.getMessage}")
        System.exit(1)
    }
  }
}
