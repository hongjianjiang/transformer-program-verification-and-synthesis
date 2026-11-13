package utils

import Crasp._

object TestProgram {
  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      println("Usage: runMain utils.TestProgram <crasp-file> <string1> [string2] ...")
      println("Example: sbt \"runMain utils.TestProgram examples/aastar.crasp aa aaa aaaa\"")
      sys.exit(1)
    }

    val filename = args(0)
    val testStrings = args.drop(1)

    CRASPParser.parseFile(filename, enforceOrder = false) match {
      case Left(error) =>
        println(s"Parse error: $error")
        sys.exit(1)

      case Right(program) =>
        println(s"Loaded program from $filename:")
        println(Printer.pretty(program))
        println()
        println("Bool rules: " + program.boolRules.map(_.name).mkString(", "))
        println("Count rules: " + program.countRules.map(_.name).mkString(", "))


        val evaluator = new CRASPEvaluator(program)
        // println(program.countRules)
        // println(program.boolRules)
        println("Testing strings:")
        testStrings.foreach { str =>
          val tokens = str.split("").filter(_.nonEmpty).toVector
          val accepted = evaluator.classifyWordAcceptanceWith("Out", tokens, "__eos__")
          val scoreVal = evaluator.scoreWith("Out", tokens)
          val status = if (accepted) "✓ ACCEPT" else "✗ REJECT"
          println(f"  $str%-20s $status  (score: $scoreVal%.2f)")
        }
    }
  }
}
