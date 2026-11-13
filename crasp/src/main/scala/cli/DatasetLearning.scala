package cli

import Crasp.*
import LS.*
import java.nio.file.{Files, Path}

/** Dataset adapter for the shared CLI; uses the existing C-RASP learner. */
object DatasetLearning:
  def main(args: Array[String]): Unit =
    try
      require(args.length == 8, "expected DATA ALPHABET OUT SEED ITERS RESTARTS BOOL COUNT")
      val alphabet = args(1).split(",", -1).toVector
      require(alphabet.nonEmpty && alphabet.distinct == alphabet && alphabet.forall(s => s.length == 1 && !s.head.isWhitespace),
        "C-RASP dataset learning requires distinct single-character symbols")
      val rows = Files.readString(Path.of(args(0))).linesIterator.filter(_.trim.nonEmpty).map { raw =>
        val line = raw.trim
        require(line.startsWith("+,") || line.startsWith("-,"), "expected +,word or -,word")
        val word = line.drop(2).trim
        require(word.forall(c => alphabet.contains(c.toString)), "sample contains a symbol outside the alphabet")
        (word, line.head == '+')
      }.toList.distinct
      require(rows.nonEmpty, "at least one labelled example is required")
      require(rows.groupBy(_._1).values.forall(_.map(_._2).distinct.size == 1), "conflicting labels for the same word")
      val seed = args(3).toInt
      val iterations = args(4).toInt
      val restarts = args(5).toInt
      val bools = args(6).toInt
      val counts = args(7).toInt
      require(iterations >= 0 && restarts > 0 && bools >= 0 && counts >= 0, "invalid search budget")
      val config = UniversalLearnerConfig(numBoolVars = bools, numCountVars = counts, allowRunParity = false,
        sa = LocalSearchSA.SAConfig(iters = iterations, continueAfterPerfect = false, logEvery = Int.MaxValue))
      var best: Option[LearningResult] = None
      for restart <- 0 until restarts if best.forall(_.accuracy < 1.0) do
        val result = UniversalLearner.learn(rows.collect { case (w, true) => w },
          rows.collect { case (w, false) => w }, config, seed + restart,
          alphabetOverride = Some(alphabet.toSet))
        if best.forall(_.accuracy < result.accuracy) then best = Some(result)
      val program = best.get.program
      val evaluator = new CRASPEvaluator(program)
      val errors = rows.count { (w, label) => evaluator.classifyWith("Out", w.toVector.map(_.toString)) != label }
      val output = Path.of(args(2))
      Option(output.getParent).foreach(Files.createDirectories(_))
      val rendered = Printer.pretty(program, Printer.PrintCfg(outName = "Out", mapDigitsToLetters = false))
      val parsed = CRASPParser.parse(rendered, enforceOrder = false).fold(
        error => throw new IllegalArgumentException(s"cannot serialize learned program: $error"), identity)
      val savedEvaluator = new CRASPEvaluator(parsed)
      require(rows.forall { (w, _) =>
        val tokens = w.toVector.map(_.toString)
        savedEvaluator.classifyWith("Out", tokens) == evaluator.classifyWith("Out", tokens)
      }, "serialization changed learned sample predictions")
      Files.writeString(output, rendered + "\n")
      System.err.println(s"${if errors == 0 then "FITS SAMPLES" else "SEARCH EXHAUSTED"}: $errors/${rows.size} errors; saved $output")
      sys.exit(if errors == 0 then 0 else 2)
    catch
      case e: IllegalArgumentException => System.err.println(e.getMessage); sys.exit(1)
      case e: java.io.IOException => System.err.println(e.getMessage); sys.exit(1)

object Evaluate:
  def main(args: Array[String]): Unit =
    require(args.length == 3, "expected FILE OUTPUT WORD")
    val program = CRASPParser.parseFile(args(0), enforceOrder = false).fold(
      error => throw new IllegalArgumentException(error.toString), identity)
    val word = if args(2).isEmpty then Vector.empty else args(2).toVector.map(_.toString)
    println(new CRASPEvaluator(program).classifyWith(args(1), word))
