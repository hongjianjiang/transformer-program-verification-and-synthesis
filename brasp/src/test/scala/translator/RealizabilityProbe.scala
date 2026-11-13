package brasp

import java.nio.file.{Files, Path}
import scala.util.Random

/** How many summary cells does each temporal row really need?
  *
  * For every temporal subformula `t` of a program's 2LTL translation, the
  * direct circuit keeps one cell per anchor valuation of `t`'s query support
  * `A_t`: `2^|A_t|` without reduction, `patterns x 2^r` with the
  * realizability cover (symbol-determined coordinates pinned to the patterns
  * the letters and BOS induce, the other `r` coordinates left free). This
  * probe counts the valuations of `A_t` that actually occur at some position
  * of some sampled word -- exhaustively up to a length bound, then on random
  * longer words. The count is a lower bound on the realizable set, so
  * `cover / realized` is a lower bound on what an exact realizability
  * analysis could save.
  *
  * Usage: `Test/runMain brasp.RealizabilityProbe OUT.tsv FILE.brasp...`
  */
object RealizabilityProbe:
  import Formula.*

  def main(args: Array[String]): Unit =
    val worker = new Thread(null, () => run(args), "probe", 1024L * 1024 * 1024)
    worker.start()
    worker.join()

  private def words(length: Int, alphabet: IndexedSeq[String]): Iterator[IndexedSeq[String]] =
    if length == 0 then Iterator.single(IndexedSeq.empty)
    else words(length - 1, alphabet).flatMap(prefix => alphabet.iterator.map(prefix :+ _))

  private def temporal(f: Formula): Boolean = f match
    case Previous(_, _, _) | Once(_, _, _) | Historically(_, _, _) | Since(_, _, _, _) => true
    case _ => false

  private def operands(f: Formula): List[Formula] = f match
    case Previous(_, _, p) => List(p)
    case Once(_, _, p) => List(p)
    case Historically(_, _, p) => List(p)
    case Since(_, _, l, r) => List(l, r)
    case _ => Nil

  private def queryKeys(f: Formula): List[Formula] = f match
    case r @ Reference(_, Position.I) => List(r)
    case a @ Atom(_, Position.I, _) => List(a)
    case Negation(p) => queryKeys(p)
    case Conjunction(ps) => ps.flatMap(queryKeys)
    case Disjunction(ps) => ps.flatMap(queryKeys)
    case _ => Nil

  /** Every temporal subformula reachable from the definitions, as `DirectSummary` collects rows. */
  private def rows(dag: FormulaDag): Vector[Formula] =
    val found = scala.collection.mutable.LinkedHashSet.empty[Formula]
    def visit(f: Formula): Unit = f match
      case Negation(p) => visit(p)
      case Conjunction(ps) => ps.foreach(visit)
      case Disjunction(ps) => ps.foreach(visit)
      case t if temporal(t) => found += t
      case _ => ()
    dag.definitions.values.foreach(visit)
    found.toVector

  private def run(args: Array[String]): Unit =
    val out = new StringBuilder("file\trow\tsupport\tfixed\tfree\tfull_cells\tcover_cells\trealized\twords\n")
    for file <- args.tail do
      val program0 = BraspText.parse(Files.readString(Path.of(file)))
      val program = BraspNormalize.leftmostToRightmost(program0)
      val dag = BraspToLtl.translateProgram(program)
      val alphabet = dag.alphabet.getOrElse(Nil).toIndexedSeq
      val staticMemo = scala.collection.mutable.Map.empty[Formula, Boolean]
      def isStatic(f: Formula): Boolean = staticMemo.getOrElseUpdate(f, f match
        case Constant(_) | Atom(_, _, _) => true
        case Reference(name, _) => dag.definitions.get(name).exists(isStatic)
        case Negation(p) => isStatic(p)
        case Conjunction(ps) => ps.forall(isStatic)
        case Disjunction(ps) => ps.forall(isStatic)
        case _ => false)
      def staticAt(f: Formula, letter: Option[String]): Boolean = f match
        case Constant(v) => v
        case Atom(AtomKind.BosAtom, _, _) => letter.isEmpty
        case Atom(kind, _, symbol) => letter.exists(Ltl.symbolMatches(kind, symbol, _))
        case Reference(name, _) => staticAt(dag.definitions(name), letter)
        case Negation(p) => !staticAt(p, letter)
        case Conjunction(ps) => ps.forall(staticAt(_, letter))
        case Disjunction(ps) => ps.exists(staticAt(_, letter))
        case _ => throw IllegalStateException("not static")
      val rowList = rows(dag)
      val supports = rowList.map(t => operands(t).flatMap(queryKeys).distinct.toVector)
      // Sample words: exhaustive while cheap, then random.
      val rng = new Random(20260924)
      val sample = scala.collection.mutable.ArrayBuffer.empty[IndexedSeq[String]]
      var length = 1
      while length <= 12 && math.pow(alphabet.size, length) * (sample.size + 1) < 4e5 do
        sample ++= words(length, alphabet)
        length += 1
      for _ <- 0 until 3000 do
        val n = 1 + rng.nextInt(40)
        sample += IndexedSeq.fill(n)(alphabet(rng.nextInt(alphabet.size)))
      val realized = Array.fill(rowList.size)(scala.collection.mutable.HashSet.empty[Vector[Boolean]])
      for word <- sample do
        val values = Brasp.evaluate(program, word).values
        for h <- 0 to word.length do
          def at(key: Formula): Boolean = key match
            case Reference(name, _) => values(name)(h)
            case Atom(AtomKind.BosAtom, _, _) => h == 0
            case Atom(kind, _, symbol) => h > 0 && Ltl.symbolMatches(kind, symbol, word(h - 1))
            case other => throw IllegalStateException(s"unexpected query key $other")
          supports.indices.foreach(r => realized(r) += supports(r).map(at))
      val name = Path.of(file).getFileName.toString
      for r <- rowList.indices do
        val keys = supports(r)
        val fixed = keys.indices.filter(i => isStatic(keys(i)))
        val free = keys.size - fixed.size
        val patterns = (None +: alphabet.map(Some(_))).map(s => fixed.map(i => staticAt(keys(i), s))).distinct.size
        val full = BigInt(2).pow(keys.size)
        val cover = BigInt(patterns) * BigInt(2).pow(free)
        out ++= s"$name\t$r\t${keys.size}\t${fixed.size}\t$free\t$full\t$cover\t${realized(r).size}\t${sample.size}\n"
      System.err.println(s"$name: ${rowList.size} rows")
    Files.writeString(Path.of(args.head), out.toString)
