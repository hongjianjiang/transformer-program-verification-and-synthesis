package brasp.synthesis

import brasp.*
import java.nio.file.{Files, Path}

/** Emit the bad-language circuit consumed by the shared CLI's ABC backend. */
object VerificationModel:
  def main(args: Array[String]): Unit =
    require(args.length == 4, "expected RELATION LEFT RIGHT_OR_DASH OUTPUT.aig")
    def read(path: String): Program =
      val text = Files.readString(Path.of(path))
      if path.endsWith(".json") then Brasp.fromJson(Json.parse(text)) else BraspText.parse(text)
    val left = read(args(1))
    val bad = args(0) match
      case "empty" => left
      case "equivalent" => Inclusion.equivalenceCounterexampleProgram(left, read(args(2)))
      case "subset" => Inclusion.counterexampleProgram(left, read(args(2)))
      case other => throw IllegalArgumentException(s"unsupported relation: $other")
    val model = DirectSummary.generate(BraspToLtl.translateProgram(bad), reduceRealizable = true)
    Files.write(Path.of(args(3)), model.model)
