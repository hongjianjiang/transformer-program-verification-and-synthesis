import Crasp._
import LS._
import utils.Dataset
import java.io.File

@main def run(args: String*): Unit = {

  // -------------------------
  // tiny arg helpers
  // -------------------------
  def getOpt(name: String): Option[String] = {
    val i = args.indexOf(name)
    if (i >= 0 && i + 1 < args.length) Some(args(i + 1)) else None
  }
  def getInt(name: String, default: Int): Int =
    getOpt(name).map(_.toInt).getOrElse(default)

  def getStr(name: String, default: String): String =
    getOpt(name).getOrElse(default)

  // first positional (non --) is mode, second positional is target
  val positionals = args.filterNot(_.startsWith("--"))
  val mode   = positionals.headOption.getOrElse("optimize") // "optimize" | "cl" | "passive"
  val target = positionals.drop(1).headOption.getOrElse("dyck")

  // map "dyck" -> examples/dyck.crasp unless user passes a .crasp path
  val filename =
    if (target.endsWith(".crasp")) target else s"examples/$target.crasp"

  if (!new File(filename).exists()) {
    System.err.println(s"[run] File not found: $filename")
    System.err.println("""Usage:
        |  run optimize <name|path> [--rounds N] [--save NAME] [--flag Out]
        |  run cl       <name|path> [--rounds N] [--save NAME] [--flag Out]
        |                      [--iters N]
        |                      [--nPos N] [--nNeg N]
        |                      [--minLen N] [--maxLen N] [--seed N]
        |  run passive  <name|path> [--save NAME]
        |                      [--iters N]
        |                      [--nPos N] [--nNeg N]
        |                      [--minLen N] [--maxLen N] [--seed N]
        |  (compat) you can still pass --n N which sets both nPos and nNeg
        |
        |Examples:
        |  run optimize dyck --rounds 100
        |  run cl dyck --nPos 200 --nNeg 50 --maxLen 60 --iters 120000
        |  run passive dyck --n 200 --maxLen 60 --iters 120000
        |""".stripMargin)

    System.exit(1)
  }

  val flag   = getStr("--flag", "Out")
  val rounds = getInt("--rounds", if (mode == "cl") 40 else 100)
  val save0  = getStr("--save", s"${target}_$mode")

  mode match {

    case "optimize" | "eq" =>
      val lsResult = LsCegis.optimizeByEqualityRefinement(
        referenceFile = filename,
        maxRounds = rounds,
        save_name = save0,
        flag = flag
      )

      lsResult match {
        case Some(prog) =>
          println(s"LS CEGIS synthesized program:\n${Printer.pretty(prog)}")
        case None =>
          println("LS CEGIS failed to synthesize a program.")
      }

    case "cl" | "constraint" =>
      val minLen = getInt("--minLen", 1)
      val maxLen = getInt("--maxLen", 30)
      val seed   = getInt("--seed", 42)
      val iters  = getInt("--iters", 80_000)

      // Backwards-compat: --n sets both.
      val nCompat = getOpt("--n").map(_.toInt)
      val nPos = getOpt("--nPos").map(_.toInt).orElse(nCompat).getOrElse(10)
      val nNeg = getOpt("--nNeg").map(_.toInt).orElse(nCompat).getOrElse(10)

      // Use generator based on target name (now uses nPos/nNeg)
      val (genPos, genNeg) = target match {
        case "dyck"        => Dataset.generateDyck1(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "asbs"        => Dataset.generateAsBs(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "anbncn"      => Dataset.generateAnBnCn(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "aastar"      => Dataset.generateAAStar(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "containsab"  => Dataset.generateContainsAB(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "majority"    => Dataset.generateMajority(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "existential" => Dataset.generateExistential(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "parity"      => Dataset.generateParity(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "lastb"       => Dataset.generateLastB(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-1"    => Dataset.generateTomita1(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-2"    => Dataset.generateTomita2(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-3"    => Dataset.generateTomita3(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-4"    => Dataset.generateTomita4(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-5"    => Dataset.generateTomita5(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-6"    => Dataset.generateTomita6(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-7"    => Dataset.generateTomita7(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "D2"          => Dataset.generateD2(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
        case "D3"          => Dataset.generateD3(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
        case "D4"          => Dataset.generateD4(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
        case "D12"         => Dataset.generateD12(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
        case "astarbstar"  => Dataset.generateAStarBStar(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "ababstar"    => Dataset.generateABABStar(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "exist"       => Dataset.generateExistsB(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-2"  => Dataset.generatePiecewiseAB(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-3"  => Dataset.generatePiecewiseABC(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-4"  => Dataset.generatePiecewise(4, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-5"  => Dataset.generatePiecewiseABCDE(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-6"  => Dataset.generatePiecewise(6, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-7"  => Dataset.generatePiecewise(7, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-8"  => Dataset.generatePiecewise(8, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-9"  => Dataset.generatePiecewise(9, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-10" => Dataset.generatePiecewise(10, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case _             => Dataset.generateDyck1(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
      }

      val lsResult = LsCegis.constraint_learning(
        referenceFile = filename,
        positives0 = genPos,
        negatives0 = genNeg,
        config = UniversalLearnerConfig(sa = LocalSearchSA.SAConfig(iters = iters)),
        maxRounds = rounds,
        save_name = save0,
        flag = flag,
        outNameExact = "Out"
      )

      lsResult match {
        case Some(prog) =>
          println(s"LS CEGIS synthesized program:\n${Printer.pretty(prog)}")
        case None =>
          println("LS CEGIS failed to synthesize a program.")
      }
    case "passive" | "pl" =>
      val minLen = getInt("--minLen", 1)
      val maxLen = getInt("--maxLen", 30)
      val seed   = getInt("--seed", 42)
      val iters  = getInt("--iters", 80_000)

      // parse file only to get alphabet (no verification)
      val oracleProg: CRASP =
        CRASPParser.parseFile(filename, enforceOrder = false) match {
          case Left(err) =>
            println(s"[passive] Failed to parse file '$filename': $err")
            System.exit(1); return
          case Right(p) => p
        }
      val alphabet = oracleProg.alphabet
      if (alphabet.isEmpty) {
        println("[passive] Alphabet is empty; abort.")
        System.exit(1)
      }

      // Backwards-compat: --n sets both.
      val nCompat = getOpt("--n").map(_.toInt)
      val nPos = getOpt("--nPos").map(_.toInt).orElse(nCompat).getOrElse(10)
      val nNeg = getOpt("--nNeg").map(_.toInt).orElse(nCompat).getOrElse(10)

      val (genPos, genNeg) = target match {
        case "dyck"        => Dataset.generateDyck1(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "asbs"        => Dataset.generateAsBs(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "anbncn"      => Dataset.generateAnBnCn(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "aastar"      => Dataset.generateAAStar(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        // case "containsab"  => Dataset.generateContainsAB(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "majority"    => Dataset.generateMajority(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "existential" => Dataset.generateExistential(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "parity"      => Dataset.generateParity(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        // case "lastb"       => Dataset.generateLastB(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "D2"          => Dataset.generateD2(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "D3"          => Dataset.generateD3(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "D4"          => Dataset.generateD4(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "D12"         => Dataset.generateD12(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "next_max"    => Dataset.generateNextMax(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "next_all"    => Dataset.generateNextAll(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-1"    => Dataset.generateTomita1(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-2"    => Dataset.generateTomita2(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-3"    => Dataset.generateTomita3(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-4"    => Dataset.generateTomita4(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-5"    => Dataset.generateTomita5(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-6"    => Dataset.generateTomita6(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "tomita-7"    => Dataset.generateTomita7(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "astarbstar"  => Dataset.generateAStarBStar(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "ababstar"    => Dataset.generateABABStar(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "exist"       => Dataset.generateExistsB(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-2"  => Dataset.generatePiecewiseAB(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-3"  => Dataset.generatePiecewiseABC(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-4"  => Dataset.generatePiecewise(4, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-5"  => Dataset.generatePiecewiseABCDE(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-6"  => Dataset.generatePiecewise(6, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-7"  => Dataset.generatePiecewise(7, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-8"  => Dataset.generatePiecewise(8, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-9"  => Dataset.generatePiecewise(9, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "piecewise-testtable-10" => Dataset.generatePiecewise(10, nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case "starabstar" => Dataset.generateSigmaStarABSigmaStar(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
        case _             => Dataset.generateDyck1(nPos = nPos, nNeg = nNeg, minLen = minLen, maxLen = maxLen, seed = seed)
      }

      // Derive search bounds from oracle structure (same logic as RunPL).
      val oracleBool   = oracleProg.boolRules.size
      val oracleCount  = oracleProg.countRules.size
      val maxBoolMain  = oracleBool  + 4
      val maxCountMain = math.max(2, oracleCount + 2)

      val plResult = LsCegis.passive_learning(
        positives0 = genPos,
        negatives0 = genNeg,
        alphabet = alphabet,
        config = UniversalLearnerConfig(sa = LocalSearchSA.SAConfig(iters = iters)),
        save_name = save0,
        outNameExact = "Out",
        seed = seed,
        verbose = true,
        requirePerfectFit = true,
        maxBool = maxBoolMain,
        maxCount = maxCountMain
      )

      plResult match {
        case Some(prog) =>
          println(s"Passive learning synthesized program:\n${Printer.pretty(prog)}")
        case None =>
          println("Passive learning failed to synthesize a perfect-fit program.")
      }

    case other =>
      System.err.println(s"[run] Unknown mode: $other (expected optimize|cl)")
      System.exit(1)
  }
}
