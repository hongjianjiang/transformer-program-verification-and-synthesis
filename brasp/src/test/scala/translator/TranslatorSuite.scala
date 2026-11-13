package brasp

import java.io.File
import java.nio.file.Files
import scala.collection.immutable.VectorMap
import JsonValue.*

class TranslatorSuite extends munit.FunSuite:

  private def readExample(path: String): String =
    new String(Files.readAllBytes(new File(path).toPath), java.nio.charset.StandardCharsets.UTF_8)

  private def wordsOfLength(n: Int, alphabet: List[String]): List[List[String]] =
    if n == 0 then List(Nil)
    else for first <- alphabet; rest <- wordsOfLength(n - 1, alphabet) yield first :: rest

  /** A tiny binary-AIGER interpreter covering exactly what `Aiger` emits
    * (text header/latch-next-literals/output, then delta-encoded AND gates,
    * then a `c` comment section it ignores), used to check the encoder —
    * including its exact byte-level format, not just its node graph —
    * against the formula semantics without a real AIGER solver. Every
    * latch is reset-to-0 by construction (see `Aiger`'s doc-comment), so
    * there's no separate init value to read. Returns, for a run over
    * `symbolIndices`, whether the output holds after consuming each
    * prefix.
    */
  private def runAiger(model: Array[Byte], symbolIndices: IndexedSeq[Int]): List[Boolean] =
    var pos = 0
    def readLine(): String =
      val start = pos
      while model(pos) != '\n'.toByte do pos += 1
      val line = new String(model, start, pos - start, java.nio.charset.StandardCharsets.US_ASCII)
      pos += 1
      line

    val Array(_, i, l, o, a) = readLine().split("\\s+").tail.map(_.toInt)
    val latchNextLits = (0 until l).map(_ => readLine().trim.toInt)
    val outputLits = (0 until o).map(_ => readLine().trim.toInt)

    def decodeDelta(): Int =
      var value = 0
      var shift = 0
      var more = true
      while more do
        val byte = model(pos) & 0xff
        pos += 1
        value |= (byte & 0x7f) << shift
        shift += 7
        more = (byte & 0x80) != 0
      value

    val andOf = (0 until a).map { k =>
      val outLit = 2 * (i + l + k + 1)
      val rhsHigh = outLit - decodeDelta()
      val rhsLow = rhsHigh - decodeDelta()
      outLit -> (rhsHigh, rhsLow)
    }.toMap

    var latchState = (0 until l).map(k => 2 * (i + k + 1) -> 0).toMap // every latch resets to 0

    def valueOf(literal: Int, inputVals: Map[Int, Int], memo: scala.collection.mutable.Map[Int, Int]): Int =
      if literal == 0 then 0
      else if literal == 1 then 1
      else
        val v = literal & ~1
        val negated = literal & 1
        val raw = memo.getOrElseUpdate(
          v,
          if latchState.contains(v) then latchState(v)
          else if inputVals.contains(v) then inputVals(v)
          else
            andOf.get(v) match
              case Some((aLit, bLit)) => valueOf(aLit, inputVals, memo) & valueOf(bLit, inputVals, memo)
              case None                => throw new RuntimeException(s"runAiger: unknown variable $v"),
        )
        raw ^ negated

    symbolIndices.map { symbol =>
      val inputVals = (0 until i).map(bitPos => 2 * (bitPos + 1) -> ((symbol >> bitPos) & 1)).toMap
      val memo = scala.collection.mutable.Map.empty[Int, Int]
      val bad = valueOf(outputLits.head, inputVals, memo) == 1
      latchState = latchNextLits.zipWithIndex.map { case (nextLit, k) => 2 * (i + k + 1) -> valueOf(nextLit, inputVals, memo) }.toMap
      bad
    }.toList

  test("brasp past LTL and future mirror agree") {
    val specification = obj(
      "alphabet" -> arr(str("a"), str("b")),
      "program" -> arr(
        obj("name" -> str("bos"), "op" -> str("bos")),
        obj("name" -> str("a"), "op" -> str("symbol"), "symbol" -> str("a")),
        obj("name" -> str("b"), "op" -> str("symbol"), "symbol" -> str("b")),
        obj(
          "name" -> str("chosen"),
          "op" -> str("rightmost"),
          "score" -> obj(
            "op" -> str("or"),
            "args" -> arr(
              obj("op" -> str("ref"), "name" -> str("a"), "at" -> str("j")),
              obj(
                "op" -> str("and"),
                "args" -> arr(
                  obj("op" -> str("ref"), "name" -> str("b"), "at" -> str("i")),
                  obj("op" -> str("ref"), "name" -> str("b"), "at" -> str("j")),
                ),
              ),
            ),
          ),
          "value" -> obj("op" -> str("not"), "arg" -> obj("op" -> str("ref"), "name" -> str("b"), "at" -> str("j"))),
        ),
        obj(
          "name" -> str("after_bos"),
          "op" -> str("rightmost"),
          "score" -> obj("op" -> str("ref"), "name" -> str("bos"), "at" -> str("j")),
          "value" -> obj("op" -> str("ref"), "name" -> str("b"), "at" -> str("i")),
        ),
        obj(
          "name" -> str("output"),
          "op" -> str("or"),
          "args" -> arr(
            obj("op" -> str("ref"), "name" -> str("chosen")),
            obj("op" -> str("ref"), "name" -> str("after_bos")),
          ),
        ),
      ),
      "output" -> str("output"),
    )

    val program = Brasp.fromJson(specification)
    val past = BraspToLtl.translate(specification)
    val future = Translator.mirrorToFuture(past)

    for
      length <- 0 until 5
      word <- wordsOfLength(length, List("a", "b"))
    do
      val pastValue = Ltl.evaluate(past, word.toIndexedSeq)
      val braspValue = Brasp.accepts(program, word.toIndexedSeq)
      assertEquals(braspValue, pastValue, s"word=$word")
      assertEquals(Brasp.evaluate(program, word.toIndexedSeq).at(program.output, length), braspValue, s"word=$word")
      assertEquals(pastValue, Ltl.evaluate(future, word.reverse.toIndexedSeq), s"word=$word")
  }

  test("brasp evaluator handles leftmost attention") {
    val spec = obj(
      "alphabet" -> arr(str("a"), str("b")),
      "program" -> arr(
        obj("name" -> str("a"), "op" -> str("symbol"), "symbol" -> str("a")),
        obj(
          "name" -> str("has_a"),
          "op" -> str("leftmost"),
          "score" -> obj("op" -> str("ref"), "name" -> str("a"), "at" -> str("j")),
          "value" -> obj("op" -> str("const"), "value" -> bool(true)),
        ),
      ),
      "output" -> str("has_a"),
    )
    val program = Brasp.fromJson(spec)
    assert(!Brasp.accepts(program, IndexedSeq.empty))
    assert(!Brasp.accepts(program, IndexedSeq("b", "b")))
    assert(Brasp.accepts(program, IndexedSeq("b", "a", "a")))
  }

  /** Every shape `BraspNormalize` has to get right, each paired with what
    * makes it interesting. The suite below checks all of them two ways: the
    * rewritten program agrees with the original subprogram-by-subprogram, and
    * the compiled 2LTL agrees with the B-RASP evaluator.
    */
  private val leftmostPrograms: List[(String, String)] = List(
    "witness-only score (no case split needed)" ->
      """alphabet a b
        |is_bos = bos
        |is_a = symbol a
        |is_b = symbol b
        |h = leftmost(!is_bos@j & is_a@j, is_b@i | is_a@j)
        |output h""".stripMargin,
    "query-dependent score (the case split)" ->
      """alphabet a b
        |is_bos = bos
        |is_a = symbol a
        |is_b = symbol b
        |h = leftmost(is_a@i & !is_bos@j | is_b@i & is_b@j, is_a@j)
        |output h""".stripMargin,
    "score that is constantly true (the witness is BOS)" ->
      """alphabet a b
        |is_bos = bos
        |is_a = symbol a
        |h = leftmost(true, is_bos@j)
        |output h""".stripMargin,
    "score that is constantly false (no candidate ever)" ->
      """alphabet a b
        |is_bos = bos
        |is_a = symbol a
        |h = leftmost(false, true)
        |output h""".stripMargin,
    "score with a residual that drops out as false" ->
      """alphabet a b
        |is_bos = bos
        |is_a = symbol a
        |is_b = symbol b
        |h = leftmost(is_a@i & is_b@i & is_a@j, !is_b@j)
        |output h""".stripMargin,
    "two leftmost nodes sharing one residual (the marker cache)" ->
      """alphabet a b
        |is_bos = bos
        |is_a = symbol a
        |is_b = symbol b
        |h1 = leftmost(!is_bos@j, is_a@j)
        |h2 = leftmost(!is_bos@j, is_b@j)
        |accept = h1 & !h2 | h2 & !h1
        |output accept""".stripMargin,
    "leftmost attending to an earlier leftmost" ->
      """alphabet a b c
        |is_bos = bos
        |is_a = symbol a
        |is_b = symbol b
        |h1 = leftmost(!is_bos@j, is_a@j)
        |h2 = leftmost(h1@j & !is_bos@j, is_b@i | is_b@j)
        |accept = h1 | h2
        |output accept""".stripMargin,
    "a name the generated subprograms would otherwise collide with" ->
      """alphabet a b
        |is_bos = bos
        |h_seen = symbol a
        |h_first = symbol b
        |h = leftmost(!is_bos@j, h_seen@j | h_first@i)
        |output h""".stripMargin,
  )

  test("leftmost normalization preserves every subprogram at every position") {
    for (label, source) <- leftmostPrograms do
      val program = BraspText.parse(source)
      val normalized = BraspNormalize.leftmostToRightmost(program)
      assert(BraspNormalize.hasLeftmost(program), s"$label: fixture has no leftmost node to normalize")
      assert(!BraspNormalize.hasLeftmost(normalized), s"$label: normalization left a leftmost node behind")
      assertEquals(normalized.output, program.output, label)
      // Subprogram-by-subprogram, not just at the output: the rewrite adds
      // definitions but must not disturb any name the input already had,
      // since later subprograms (and callers) still read them.
      val alphabet = program.alphabet.get
      for
        length <- 0 until 6
        word <- wordsOfLength(length, alphabet)
      do
        val original = Brasp.evaluate(program, word.toIndexedSeq)
        val rewritten = Brasp.evaluate(normalized, word.toIndexedSeq)
        for
          name <- program.subprograms.map(_.name)
          position <- 0 to length
        do assertEquals(rewritten.at(name, position), original.at(name, position), s"$label: $name@$position word=$word")
  }

  test("leftmost programs compile to 2LTL that agrees with the B-RASP evaluator") {
    for (label, source) <- leftmostPrograms do
      val program = BraspText.parse(source)
      val past = BraspToLtl.translateProgram(program)
      val future = Translator.mirrorToFuture(past)
      for
        length <- 0 until 5
        word <- wordsOfLength(length, program.alphabet.get)
      do
        val expected = Brasp.accepts(program, word.toIndexedSeq)
        assertEquals(Ltl.evaluate(past, word.toIndexedSeq), expected, s"$label: past, word=$word")
        assertEquals(Ltl.evaluate(future, word.reverse.toIndexedSeq), expected, s"$label: future, word=$word")
  }

  test("a leftmost score too tangled to normalize is refused with an explanation, not a bare 'normalize first'") {
    val program = BraspText.parse(
      """alphabet a b
        |is_bos = bos
        |is_a = symbol a
        |is_b = symbol b
        |p = rightmost(is_a@j, true)
        |q = rightmost(is_b@j, true)
        |h = leftmost(is_a@i & is_a@j | is_b@i & is_b@j | p@i & p@j | q@i & q@j, true)
        |output h""".stripMargin
    )
    // Four genuinely relevant query-position variables, none of which the
    // split can fold away: 16 cases against a budget of 4.
    val error = intercept[TranslationError](BraspNormalize.leftmostToRightmost(program, maxScoreCases = 4))
    assert(error.message.startsWith("h:"), error.message)
    assert(error.message.contains("query position"), error.message)
    assert(error.message.contains("rightmost attention"), error.message)
    // ... and the real budget is nowhere near that, so the same program
    // compiles on the ordinary path.
    assert(!BraspNormalize.hasLeftmost(BraspNormalize.leftmostToRightmost(program)))
  }

  test("inclusion reduces to counterexample emptiness") {
    val endsInA = Brasp.fromJson(
      obj(
        "alphabet" -> arr(str("a"), str("b")),
        "program" -> arr(obj("name" -> str("a"), "op" -> str("symbol"), "symbol" -> str("a"))),
        "output" -> str("a"),
      )
    )
    val allWords = Brasp.fromJson(
      obj(
        "alphabet" -> arr(str("a"), str("b")),
        "program" -> arr(obj("name" -> str("yes"), "op" -> str("const"), "value" -> bool(true))),
        "output" -> str("yes"),
      )
    )
    val noCounterexample = Inclusion.counterexampleProgram(endsInA, allWords)
    val hasCounterexample = Inclusion.counterexampleProgram(allWords, endsInA)
    for
      length <- 0 until 4
      word <- wordsOfLength(length, List("a", "b"))
    do assert(!Brasp.accepts(noCounterexample, word.toIndexedSeq), s"word=$word")
    assert(Brasp.accepts(hasCounterexample, IndexedSeq("b")))
    assert(!Brasp.accepts(Inclusion.equivalenceCounterexampleProgram(endsInA, endsInA), IndexedSeq("a")))
    assert(Brasp.accepts(Inclusion.equivalenceCounterexampleProgram(endsInA, allWords), IndexedSeq("b")))
  }

  private val sameLetterBeforeLtl =
    """logic past-strict
      |alphabet a b
      |
      |f_0 := sym(a)@i
      |f_1 := sym(b)@i
      |f_2 := P(!((!((f_0@i & f_0@j)) & !((f_1@i & f_1@j)))))
      |
      |output := f_2@i
      |evaluate at i = |w| (the final input position)
      |""".stripMargin

  /** The classical baseline of `TwoLtlToOneVariable`: compile the second
    * variable away by case-splitting on the anchor's type, then check that
    * the result (a) means the same thing and (b) is genuinely one-variable,
    * i.e. leaves no anchor reference inside any temporal operand. Both halves
    * matter: a translation that preserved the language but left anchor
    * references behind would still be using the pebble, and would not be the
    * baseline it claims to be.
    */
  private def assertOneVariableTranslation(text: String, maxLength: Int): Unit =
    val dag = LtlText.parse(text)
    val one = TwoLtlToOneVariable.translate(dag)
    val alphabet = dag.alphabet.getOrElse(Nil)
    assert(alphabet.nonEmpty)
    for
      length <- 0 to maxLength
      word <- wordsOfLength(length, alphabet)
    do
      assertEquals(
        Ltl.evaluate(one, word.toIndexedSeq),
        Ltl.evaluate(dag, word.toIndexedSeq),
        s"word=$word",
      )
    assert(DirectSummary.generate(one, reduceRealizable = false).maxSupport == 0,
      "the one-variable translation must leave no anchor reference inside a temporal operand")

  test("TwoLtlToOneVariable preserves the language and removes the pebble") {
    assertOneVariableTranslation(sameLetterBeforeLtl, maxLength = 5)
  }

  test("TwoLtlToOneVariable handles a formula already in the one-variable fragment") {
    // dot_depth uses its witness variable in the ordinary one-variable way,
    // so the case split is vacuous and the translation should shrink rather
    // than blow up.
    val text = readExample("examples/ltl/dot_depth__k-5__sigma-2.ltl")
    assertOneVariableTranslation(text, maxLength = 5)
  }

  test("TwoLtlToOneVariable reports the blow-up instead of exhausting memory") {
    // Without the realizability cover the split is over all sigma symbol
    // predicates at once: the one-variable form is exponential in sigma.
    val text = readExample("examples/ltl/two_var__monotone_past__sigma-16.ltl")
    val error = intercept[TwoLtlToOneVariable.TranslationTooLarge](
      TwoLtlToOneVariable.translate(LtlText.parse(text), cap = 1 << 16, cover = false))
    assert(error.getMessage.contains("disjuncts") || error.getMessage.contains("nodes"), error.getMessage)
  }

  test("TwoLtlToOneVariable with the realizability cover splits over sigma + 1 patterns") {
    // The symbol predicates are symbol-determined, so the cover leaves one
    // disjunct per symbol and BOS instead of 2^sigma.
    val text = readExample("examples/ltl/two_var__same_letter_before__sigma-32.ltl")
    TwoLtlToOneVariable.translate(LtlText.parse(text), cap = 1 << 16, cover = true)
    assertOneVariableTranslation(readExample("examples/ltl/two_var__same_letter_before__sigma-4.ltl"), maxLength = 5)
  }

  test("ltl evaluator supports unary past and future operators") {
    val pastPrevious = FormulaDag(
      Logic.PastStrict,
      VectorMap.empty,
      Formula.Previous(Position.I, Position.J, Formula.Atom(AtomKind.SymbolAtom, Position.J, Some("a"))),
      "i = |w|",
    )
    val pastOnce = FormulaDag(
      Logic.PastStrict,
      VectorMap.empty,
      Formula.Once(Position.I, Position.J, Formula.Atom(AtomKind.SymbolAtom, Position.J, Some("a"))),
      "i = |w|",
    )
    val futureNext = FormulaDag(
      Logic.FutureStrict,
      VectorMap.empty,
      Formula.Next(Position.I, Position.J, Formula.Atom(AtomKind.SymbolAtom, Position.J, Some("b"))),
      "i = 0",
    )
    val futureEventually = FormulaDag(
      Logic.FutureStrict,
      VectorMap.empty,
      Formula.Eventually(Position.I, Position.J, Formula.Atom(AtomKind.EosAtom, Position.J)),
      "i = 0",
    )
    assert(Ltl.evaluate(pastPrevious, IndexedSeq("a", "b")))
    assert(Ltl.evaluate(pastOnce, IndexedSeq("a", "b")))
    assert(Ltl.evaluate(futureNext, IndexedSeq("a", "b")))
    assert(Ltl.evaluate(futureEventually, IndexedSeq("a", "b")))
  }

  test("rejects a forward reference, but compiles leftmost attention") {
    intercept[TranslationError] {
      BraspToLtl.translate(
        obj(
          "alphabet" -> arr(str("a")),
          "program" -> arr(obj("name" -> str("p"), "op" -> str("not"), "arg" -> obj("op" -> str("ref"), "name" -> str("q")))),
        )
      )
    }
    // `leftmost` used to be rejected here; `BraspNormalize` now rewrites it
    // into rightmost attention on the way in, so the JSON front end compiles
    // it like anything else.
    val dag = BraspToLtl.translate(
      obj(
        "alphabet" -> arr(str("a")),
        "program" -> arr(
          obj(
            "name" -> str("p"),
            "op" -> str("leftmost"),
            "score" -> obj("op" -> str("const"), "value" -> bool(true)),
            "value" -> obj("op" -> str("const"), "value" -> bool(true)),
          )
        ),
      )
    )
    assertEquals(dag.output, Formula.Reference("p", Position.I))
    // `leftmost(true, true)` is "some position precedes i", so it holds
    // exactly on the nonempty words.
    assert(!Ltl.evaluate(dag, IndexedSeq.empty))
    assert(Ltl.evaluate(dag, IndexedSeq("a")))
  }

  test("Ltl.mirror is stack-safe on a formula nested far deeper than any JVM default thread stack") {
    // Built and checked *iteratively*, not recursively -- constructing the
    // formula, or verifying the result with a recursive walk (or even
    // `==`, whose case-class-generated `equals` recurses into nested
    // fields), would just reintroduce the same stack-depth problem
    // `mirror` itself used to have. Real benchmark formulas
    // (`two_var/monotone_past`) reach ~860 levels and already overflowed
    // the default ~512KB-1MB stack (see `Ltl.mirror`'s own doc-comment);
    // this goes three orders of magnitude deeper to prove the fix isn't
    // just "the JVM happened to have enough stack for the formulas we
    // tried."
    val depth = 500_000
    var deep: Formula = Formula.Constant(true)
    for _ <- 0 until depth do deep = Formula.Negation(deep)

    def negationDepth(root: Formula): (Int, Formula) =
      var node = root
      var count = 0
      while node.isInstanceOf[Formula.Negation] do
        node = node.asInstanceOf[Formula.Negation].operand
        count += 1
      (count, node)

    val (mirroredDepth, mirroredLeaf) = negationDepth(Ltl.mirror(deep))
    assertEquals(mirroredDepth, depth)
    assertEquals(mirroredLeaf, Formula.Constant(true))

    // `mirror` is its own inverse (see its own doc-comment) -- mirroring
    // back should exactly reproduce the original depth too.
    val (roundTrippedDepth, roundTrippedLeaf) = negationDepth(Ltl.mirror(Ltl.mirror(deep)))
    assertEquals(roundTrippedDepth, depth)
    assertEquals(roundTrippedLeaf, Formula.Constant(true))
  }

  test("the direct and elimination circuits agree with the formula on benchmark inputs") {
    val files = List(
      "examples/ltl/two_var__same_letter_before__sigma-4.ltl" -> 4,
      "examples/ltl/two_var__since_same_letter__sigma-4.ltl" -> 4,
      "examples/ltl/two_var__monotone_past__sigma-4.ltl" -> 4,
      "examples/ltl/markey_agreement__n-2.ltl" -> 3,
      "examples/ltl/dot_depth__k-5__sigma-2.ltl" -> 7,
    )
    for (path, maxLength) <- files do
      val dag = LtlText.parse(readExample(path))
      val alphabet = dag.alphabet.get
      val circuit = DirectSummary.generate(TwoLtlToOneVariable.translate(dag), reduceRealizable = false)
      assertEquals(circuit.maxSupport, 0, path)
      val direct = DirectSummary.generate(dag)
      for n <- 1 to maxLength; w <- wordsOfLength(n, alphabet) do
        val expected = (1 to n).map(k => Ltl.evaluate(dag, w.take(k).toIndexedSeq)).toList
        assertEquals(runAiger(circuit.model, w.map(alphabet.indexOf).toIndexedSeq), expected, s"$path word=$w")
        assertEquals(runAiger(direct.model, w.map(alphabet.indexOf).toIndexedSeq), expected, s"direct $path word=$w")
  }

  test("direct summary circuits agree with the formula on random DAGs") {
    import Formula.*
    val rng = new scala.util.Random(7419)
    for trial <- 0 until 40 do
      val alphabet = if trial % 2 == 0 then List("a", "b") else List("a", "b", "c")
      var defs = VectorMap[String, Formula]("a" -> Atom(AtomKind.SymbolAtom, Position.I, Some("a")),
        "bos" -> Atom(AtomKind.BosAtom, Position.I, None))
      def predicate(depth: Int): Formula =
        if depth == 0 then
          if rng.nextInt(4) == 0 then Constant(rng.nextBoolean())
          else Reference(defs.keys.toVector(rng.nextInt(defs.size)), if rng.nextBoolean() then Position.I else Position.J)
        else rng.nextInt(3) match
          case 0 => Negation(predicate(depth - 1))
          case 1 => Conjunction(List(predicate(depth - 1), predicate(depth - 1)))
          case _ => Disjunction(List(predicate(depth - 1), predicate(depth - 1)))
      for n <- 0 until 4 do
        val p = predicate(2)
        val t = rng.nextInt(4) match
          case 0 => Previous(Position.I, Position.J, p)
          case 1 => Once(Position.I, Position.J, p)
          case 2 => Historically(Position.I, Position.J, p)
          case _ => Since(Position.I, Position.J, p, predicate(2))
        defs = defs.updated(s"t$n", if rng.nextBoolean() then Negation(t) else t)
      val dag = FormulaDag(Logic.PastStrict, defs, Reference("t3", Position.I), "final", alphabet = Some(alphabet))
      val ltlModels = List(false, true).map { reduced =>
        val translated = SharedOneVariableLtl.translate(dag, reduced)
        val compiled = DirectSummary.generate(translated.dag, false)
        assertEquals(compiled.maxSupport, 0)
        compiled.model
      }
      // The elimination baseline: one-variable formula, then the same circuit backend.
      val eliminated = DirectSummary.generate(TwoLtlToOneVariable.translate(dag), false)
      assertEquals(eliminated.maxSupport, 0)
      val models = List(DirectSummary.generate(dag, false).model, DirectSummary.generate(dag).model,
        eliminated.model) ++ ltlModels
      for n <- 1 to 4; w <- wordsOfLength(n, alphabet) do
        val expected = (1 to n).map(k => Ltl.evaluate(dag, w.take(k).toIndexedSeq)).toList
        models.foreach(m => assertEquals(runAiger(m, w.map(alphabet.indexOf).toIndexedSeq), expected, s"trial=$trial word=$w"))
  }

  test("direct boundary, inline atoms, constants, invalid symbols and shared temporal subformulas") {
    import Formula.*
    val alphabet = List("00", "10", "11")
    val a = Atom(AtomKind.BitAtom, Position.J, Some("0"))
    val h = Historically(Position.I, Position.J, a)
    val formulas = List(Constant(true), Constant(false), h, Negation(h),
      Previous(Position.I, Position.J, Atom(AtomKind.BosAtom, Position.J, None)),
      Once(Position.I, Position.J, Conjunction(List(Atom(AtomKind.BitAtom, Position.I, Some("0")), a))),
      Disjunction(List(h, Negation(h))))
    for f <- formulas do
      val dag = FormulaDag(Logic.PastStrict, VectorMap.empty, f, "final", alphabet = Some(alphabet))
      for reduced <- List(false, true) do
        val translated = SharedOneVariableLtl.translate(dag, reduced)
        val compiled = DirectSummary.generate(translated.dag, false)
        assertEquals(compiled.maxSupport, 0)
        for model <- List(compiled.model, DirectSummary.generate(dag, reduced).model) do
          for n <- 1 to 4; w <- wordsOfLength(n, alphabet) do
            val expected = (1 to n).map(k => Ltl.evaluate(dag, w.take(k).toIndexedSeq)).toList
            assertEquals(runAiger(model, w.map(alphabet.indexOf).toIndexedSeq), expected)
            assertEquals(Ltl.evaluate(translated.dag, w.toIndexedSeq), expected.last)
          assert(runAiger(model, IndexedSeq(3, 0, 1)).forall(!_))
  }

  test("direct realizability avoids exponential enumeration and invalid DAGs fail explicitly") {
    val dag = LtlText.parse(readExample("examples/ltl/two_var__same_letter_before__sigma-32.ltl"))
    val reduced = DirectSummary.generate(dag)
    assert(reduced.supportCells >= BigInt(2).pow(32))
    assert(reduced.retainedCells <= 33)
    intercept[AigerError](DirectSummary.generate(dag, false))
    val cycle = dag.copy(definitions = VectorMap("x" -> Formula.Reference("x", Position.I)),
      output = Formula.Reference("x", Position.I))
    intercept[AigerError](DirectSummary.generate(cycle))
    intercept[AigerError](DirectSummary.generate(dag.copy(output = Formula.Atom(AtomKind.EosAtom, Position.I, None))))
  }

  test("shared LTL eliminates only realizable cases and preserves named DAG sharing") {
    import Formula.*
    val dag = LtlText.parse(readExample("examples/ltl/two_var__same_letter_before__sigma-32.ltl"))
    val translated = SharedOneVariableLtl.translate(dag)
    assert(translated.supportCases >= BigInt(2).pow(32))
    assert(translated.retainedCases <= 33)
    val circuit = DirectSummary.generate(translated.dag, false)
    assertEquals(circuit.maxSupport, 0)
    intercept[TwoLtlToOneVariable.TranslationTooLarge](SharedOneVariableLtl.translate(dag, false))
    // Recursive inlining would make this diamond exponentially large.
    var defs = VectorMap[String, Formula]("p0" -> Atom(AtomKind.SymbolAtom, Position.I, Some("a")))
    for i <- 1 to 80 do
      val r = Reference(s"p${i-1}", Position.I)
      defs = defs.updated(s"p$i", Disjunction(List(r, Negation(r))))
    val shared = dag.copy(definitions = defs, output = Reference("p80", Position.I), alphabet = Some(List("a", "b")))
    val result = SharedOneVariableLtl.translate(shared)
    assertEquals(result.dag.definitions.size, 81)
    assertEquals(DirectSummary.generate(result.dag).rows, 0)
    val cycle = shared.copy(definitions = VectorMap("x" -> Reference("x", Position.I)), output = Reference("x", Position.I))
    intercept[TwoLtlToOneVariable.TranslationTooLarge](SharedOneVariableLtl.translate(cycle))
  }

  test("shared LTL keeps temporal coordinates free in the realizability cover") {
    import Formula.*
    val defs = VectorMap[String, Formula](
      "a" -> Atom(AtomKind.SymbolAtom, Position.I, Some("a")),
      "not_a" -> Negation(Reference("a", Position.I)),
      "past" -> Once(Position.I, Position.J, Reference("a", Position.J)))
    val output = Since(Position.I, Position.J,
      Disjunction(List(Reference("a", Position.I), Reference("past", Position.I))),
      Conjunction(List(Reference("not_a", Position.I), Reference("a", Position.J))))
    val dag = FormulaDag(Logic.PastStrict, defs, output, "final", alphabet = Some(List("a", "b")))
    val result = SharedOneVariableLtl.translate(dag)
    assertEquals(result.supportCases, BigInt(9)) // one lower row, eight top cases
    assertEquals(result.retainedCases, BigInt(5)) // two static patterns x two free values
    for n <- 1 to 4; w <- wordsOfLength(n, List("a", "b")) do
      assertEquals(Ltl.evaluate(result.dag, w.toIndexedSeq), Ltl.evaluate(dag, w.toIndexedSeq))
  }
