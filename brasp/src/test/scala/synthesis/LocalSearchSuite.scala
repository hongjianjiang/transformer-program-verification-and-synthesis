package brasp.synthesis

import brasp.*
import LocalSearch.*

class LocalSearchSuite extends munit.FunSuite:
  private val alphabet = Vector("a", "b")
  private def words(max: Int): Vector[Vector[String]] =
    (0 to max).toVector.flatMap { n =>
      (0 until n).foldLeft(Vector(Vector.empty[String])) { (prefixes, _) =>
        for p <- prefixes; a <- alphabet yield p :+ a
      }
    }

  test("learns a past-dependent language and generalizes to longer words") {
    val data = words(3).map(w => Example(w, w.contains("a")))
    val result = learn(alphabet, data, Config(seed = 42, slots = 3, iterations = 10000))
    assert(result.fitsSamples, s"remaining errors: ${result.errors}")
    result.program.validate()
    assertEquals(BraspText.parse(BraspText.render(result.program)), result.program)
    words(6).foreach(w => assertEquals(Brasp.accepts(result.program, w), w.contains("a"), w.mkString))
  }

  test("search is reproducible and respects its evaluation budget") {
    val data = words(2).map(w => Example(w, w == Vector("a", "b")))
    val cfg = Config(seed = 5, iterations = 20, restarts = 2, slots = 2)
    val result = learn(alphabet, data, cfg)
    assertEquals(learn(alphabet, data, cfg), result)
    assert(result.evaluations <= cfg.restarts.toLong * (cfg.iterations + 1))
    assertEquals(result.errors, data.count(e => Brasp.accepts(result.program, e.word) != e.accepted))
  }

  test("balanced objective reports raw errors, is reproducible, and respects its budget") {
    // (ab)* up to length 4: 3 positives against 28 negatives.
    val data = words(4).map(w => Example(w, w.grouped(2).forall(_ == Vector("a", "b"))))
    val cfg = Config(seed = 3, slots = 4, iterations = 2000, restarts = 2, balanced = true)
    val result = learn(alphabet, data, cfg)
    assertEquals(result.errors, data.count(e => Brasp.accepts(result.program, e.word) != e.accepted))
    assertEquals(learn(alphabet, data, cfg), result)
    assert(result.evaluations <= cfg.restarts.toLong * (cfg.iterations + 1))
  }

  test("empty word is evaluated at BOS; budget exhaustion is not success") {
    val data = Vector(Example(Vector.empty, true), Example(Vector("a"), false))
    val result = learn(alphabet, data, Config(iterations = 0, restarts = 1))
    assertEquals(result.evaluations, 1L)
    assertEquals(result.errors, data.count(e => Brasp.accepts(result.program, e.word) != e.accepted))
  }

  test("rejects inconsistent labels, unknown symbols, empty data, and invalid configuration") {
    intercept[IllegalArgumentException](learn(alphabet, Vector(Example(Vector.empty, true), Example(Vector.empty, false))))
    intercept[IllegalArgumentException](learn(alphabet, Vector(Example(Vector("c"), true))))
    intercept[IllegalArgumentException](learn(alphabet, Vector.empty))
    intercept[IllegalArgumentException](Config(slots = 0))
    intercept[IllegalArgumentException](Config(cooling = Double.NaN))
  }

  test("dataset parsing preserves empty words and supports token alphabets") {
    assertEquals(RunSynthesis.parseDataset("+,\n-,ab\n", false),
      Vector(Example(Vector.empty, true), Example(Vector("a", "b"), false)))
    assertEquals(RunSynthesis.parseDataset("+,red blue\n", true),
      Vector(Example(Vector("red", "blue"), true)))
    intercept[IllegalArgumentException](RunSynthesis.parseDataset("oops", false))
  }

  test("CLI saves a parseable candidate and reports its actual fit") {
    val directory = java.nio.file.Files.createTempDirectory("brasp-synthesis-test")
    val input = directory.resolve("samples.csv")
    val output = directory.resolve("candidate.brasp")
    try
      java.nio.file.Files.writeString(input, "+,\n-,a\n-,b\n")
      val code = RunSynthesis.run(Array("--data", input.toString, "--out", output.toString,
        "--alphabet", "a,b", "--iters", "0", "--restarts", "1"))
      val candidate = BraspText.parse(java.nio.file.Files.readString(output))
      val fits = Brasp.accepts(candidate, Vector.empty) &&
        !Brasp.accepts(candidate, Vector("a")) && !Brasp.accepts(candidate, Vector("b"))
      assertEquals(code, if fits then 0 else 2)
    finally
      java.nio.file.Files.deleteIfExists(output)
      java.nio.file.Files.deleteIfExists(input)
      java.nio.file.Files.deleteIfExists(directory)
  }

  test("pruning preserves both attention directions, query/witness references, and BOS semantics") {
    val p = BraspText.parse("""alphabet a b
      |start = bos
      |a = symbol a
      |unused = symbol b
      |first = leftmost(!start@j, a@j)
      |last = rightmost(a@i | a@j, !start@j)
      |out = first & last
      |output out
      |""".stripMargin)
    val pruned = prune(p)
    assert(!pruned.subprograms.exists(_.name == "unused"))
    words(4).foreach(w => assertEquals(Brasp.accepts(pruned, w), Brasp.accepts(p, w)))
  }
