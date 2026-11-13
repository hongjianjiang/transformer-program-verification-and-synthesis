package brasp

import scala.collection.immutable.VectorMap

/** The classical route from \TLTL{} down to *one-variable* past-LTL, kept as a
  * baseline for the automaton constructions that avoid it.
  *
  * A two-variable formula may consult the anchor from inside a temporal
  * operand: `f S_j^i (g(i) & h(j))` scans for a witness `j` while still
  * reading `g` at the fixed anchor `i`. One-variable LTL has no way to say
  * that, and the standard remedy is to case-split on the anchor's *type* --
  * the vector of truth values of the anchor-referenced subformulas. Each
  * valuation turns the operand into a one-variable formula, and the anchor's
  * actual type is pinned outside the operator, where it is evaluable:
  *
  * {{{
  *   T(psi(i, j))  ==  OR over b of  ( AND_n [n(i) <-> b(n)]  AND  T(psi[b]) )
  * }}}
  *
  * The disjunction ranges over `2^|A|` valuations, `A` being the anchor
  * references inside the operand -- this is the exponential the pebble exists
  * to avoid, and it is the point of keeping this translation around: it makes
  * the cost concrete rather than asymptotic. `monotone_past` at alphabet size
  * `sigma` splits on all `sigma` symbol predicates at once, so its single
  * `H` operator alone becomes `2^sigma` disjuncts.
  *
  * The result is one-variable in the sense that matters downstream: no
  * `Reference(_, Position.I)` survives anywhere, so every temporal operand
  * reads only its witness position. Getting there needs a second step after the split ---
  * the guards it introduces are themselves anchor references --- so every
  * remaining `@i` reference is inlined. Inlining is sound precisely because
  * those references sit at the anchor level already, the same position the
  * enclosing formula is evaluated at; `@j` references are left alone, since
  * substituting one would silently move its body to a different position.
  */
object TwoLtlToOneVariable:

  final case class TranslationTooLarge(message: String) extends RuntimeException(message)

  import Formula.*

  /** Names referenced at the anchor inside a temporal operand -- the
    * variables the case split ranges over. Operands are Boolean combinations
    * of references, so this is
    * the whole of the operand's anchor dependence.
    */
  private def anchorReferences(formula: Formula): Set[String] = formula match
    case Reference(name, Position.I) => Set(name)
    case Reference(_, _)             => Set.empty
    case Negation(operand)           => anchorReferences(operand)
    case Conjunction(operands)       => operands.flatMap(anchorReferences).toSet
    case Disjunction(operands)       => operands.flatMap(anchorReferences).toSet
    case _                           => Set.empty

  private def substitute(formula: Formula, valuation: Map[String, Boolean]): Formula = formula match
    case Reference(name, Position.I) if valuation.contains(name) => Constant(valuation(name))
    case Negation(operand)                                       => Negation(substitute(operand, valuation))
    case Conjunction(operands)                                   => Conjunction(operands.map(substitute(_, valuation)))
    case Disjunction(operands)                                   => Disjunction(operands.map(substitute(_, valuation)))
    case other                                                   => other

  private def mapOperands(formula: Formula, f: Formula => Formula): Formula = formula match
    case Previous(a, w, operand)     => Previous(a, w, f(operand))
    case Once(a, w, operand)         => Once(a, w, f(operand))
    case Historically(a, w, operand) => Historically(a, w, f(operand))
    case Next(a, w, operand)         => Next(a, w, f(operand))
    case Eventually(a, w, operand)   => Eventually(a, w, f(operand))
    case Always(a, w, operand)       => Always(a, w, f(operand))
    case Since(a, w, left, right)    => Since(a, w, f(left), f(right))
    case Until(a, w, left, right)    => Until(a, w, f(left), f(right))
    case other                       => other

  private def operandsOf(formula: Formula): List[Formula] = formula match
    case Previous(_, _, operand)     => List(operand)
    case Once(_, _, operand)         => List(operand)
    case Historically(_, _, operand) => List(operand)
    case Next(_, _, operand)         => List(operand)
    case Eventually(_, _, operand)   => List(operand)
    case Always(_, _, operand)       => List(operand)
    case Since(_, _, left, right)    => List(left, right)
    case Until(_, _, left, right)    => List(left, right)
    case _                           => Nil

  private def isTemporal(formula: Formula): Boolean = operandsOf(formula).nonEmpty

  /** A letter an anchor can carry: a word symbol, or the boundary position
    * (BOS for strict past, EOS for strict future).
    */
  private enum Letter:
    case Symbol(name: String)
    case Bos
    case Eos

  /** The realizability cover of `prop:realizable`, applied to the case split.
    *
    * A *symbol-determined* anchor reference (a Boolean combination of symbol
    * tests and constants) is fixed by the anchor's letter, so the references
    * of this kind jointly take at most `|alphabet| + 1` values, whichever
    * letters produce them. The split then ranges over those patterns --
    * each guarded by the disjunction of the letters producing it -- times the
    * `2^r` valuations of the remaining references, instead of over all
    * `2^|A|` valuations. The patterns partition the letters, so the guards
    * are mutually exclusive and exhaustive, and every actual anchor
    * valuation is covered. Substituted operands are constant-propagated, as
    * in the direct circuit.
    */
  private final class Cover(dag: FormulaDag):
    private val letters: Option[Vector[Letter]] = dag.alphabet.map { alphabet =>
      val boundary = if dag.logic == Logic.PastStrict then Letter.Bos else Letter.Eos
      alphabet.toVector.map(Letter.Symbol(_)) :+ boundary
    }
    private val staticMemo = scala.collection.mutable.Map.empty[String, Boolean]
    /** Every letter is a bit string of one length `m` and all `2^m` occur: a
      * letter test is then `m` bit literals, so guards may be written that way.
      */
    private val bitWidth: Option[Int] = dag.alphabet.flatMap { alphabet =>
      val widths = alphabet.map(_.length).distinct
      Option.when(
        widths.size == 1 && widths.head > 0 && widths.head < 31 && alphabet.forall(_.forall("01".contains(_))) &&
          alphabet.distinct.size == (1 << widths.head)
      )(widths.head)
    }
    private def static(formula: Formula): Boolean = formula match
      case Constant(_)                                        => true
      case Atom(AtomKind.SymbolAtom | AtomKind.BosAtom | AtomKind.EosAtom, Position.I, _) => true
      case Atom(AtomKind.BitAtom, Position.I, _)              => dag.alphabet.isDefined
      case Reference(name, Position.I) =>
        staticMemo.getOrElseUpdate(name, dag.definitions.get(name).exists(static))
      case Negation(operand)     => static(operand)
      case Conjunction(operands) => operands.forall(static)
      case Disjunction(operands) => operands.forall(static)
      case _                     => false
    private def value(formula: Formula, letter: Letter): Boolean = formula match
      case Constant(v)                                => v
      case Atom(AtomKind.SymbolAtom, _, symbol)       => letter == Letter.Symbol(symbol.get)
      case Atom(AtomKind.BitAtom, _, index) =>
        letter match
          case Letter.Symbol(s) => Ltl.symbolMatches(AtomKind.BitAtom, index, s)
          case _                => false
      case Atom(AtomKind.BosAtom, _, _)               => letter == Letter.Bos
      case Atom(AtomKind.EosAtom, _, _)               => letter == Letter.Eos
      case Reference(name, _)                         => value(dag.definitions(name), letter)
      case Negation(operand)                          => !value(operand, letter)
      case Conjunction(operands)                      => operands.forall(value(_, letter))
      case Disjunction(operands)                      => operands.exists(value(_, letter))
      case other => throw IllegalStateException(s"not symbol-determined: $other")
    private def test(letter: Letter): Formula = letter match
      case Letter.Symbol(s) =>
        bitWidth match
          case Some(_) =>
            Conjunction(s.indices.toList.map { k =>
              val bit = Atom(AtomKind.BitAtom, Position.I, Some(k.toString))
              if s(k) == '1' then bit else Negation(bit)
            })
          case None => Atom(AtomKind.SymbolAtom, Position.I, Some(s))
      case Letter.Bos       => Atom(AtomKind.BosAtom, Position.I)
      case Letter.Eos       => Atom(AtomKind.EosAtom, Position.I)
    private val letterCost = bitWidth.getOrElse(1)

    /** `(fixed names, [(guard, their values)])`; no fixed names without a declared alphabet. */
    def patterns(names: List[String]): (List[String], List[(Formula, Map[String, Boolean])]) =
      letters match
        case None => (Nil, List(Constant(true) -> Map.empty))
        case Some(all) =>
          val fixed = names.filter(name => static(Reference(name, Position.I)))
          if fixed.isEmpty then (Nil, List(Constant(true) -> Map.empty))
          else
            val grouped = all.groupBy(letter => fixed.map(name => value(Reference(name, Position.I), letter)))
            val index = all.zipWithIndex.toMap
            val ordered = grouped.toList.sortBy((_, ls) => index(ls.head))
            fixed -> ordered.map { (values, ls) =>
              // Either guard is exact on every actual anchor: the letters that
              // produce this pattern, or the pattern's own values. Take the smaller.
              val guard: Formula =
                if ls.size == all.size then Constant(true)
                else if ls.size.toLong * letterCost > fixed.size then
                  Conjunction(fixed.zip(values).map((name, v) =>
                    if v then Reference(name, Position.I) else Negation(Reference(name, Position.I))))
                else if ls.size == 1 then test(ls.head)
                else Disjunction(ls.map(test).toList)
              guard -> fixed.zip(values).toMap
            }

  private[brasp] def coverEnabled: Boolean = sys.props.getOrElse("elimination.cover", "true").toBoolean

  /** Constant propagation over Boolean connectives and trivially constant temporal operators. */
  private def fold(formula: Formula): Formula = formula match
    case Negation(operand) =>
      fold(operand) match
        case Constant(v) => Constant(!v)
        case Negation(p) => p
        case p           => Negation(p)
    case Conjunction(operands) =>
      val folded = operands.map(fold)
      if folded.contains(Constant(false)) then Constant(false)
      else folded.filterNot(_ == Constant(true)) match
        case Nil       => Constant(true)
        case List(one) => one
        case many      => Conjunction(many)
    case Disjunction(operands) =>
      val folded = operands.map(fold)
      if folded.contains(Constant(true)) then Constant(true)
      else folded.filterNot(_ == Constant(false)) match
        case Nil       => Constant(false)
        case List(one) => one
        case many      => Disjunction(many)
    case other if isTemporal(other) =>
      mapOperands(other, fold) match
        case Previous(_, _, Constant(false)) | Once(_, _, Constant(false)) |
            Next(_, _, Constant(false)) | Eventually(_, _, Constant(false)) => Constant(false)
        case Historically(_, _, Constant(true)) | Always(_, _, Constant(true)) => Constant(true)
        case Since(_, _, _, Constant(false)) | Until(_, _, _, Constant(false)) => Constant(false)
        case t => t
    case other => other

  /** The case split restricted to the realizability cover. */
  private def coveredSplit(formula: Formula, names: List[String], cover: Cover, cap: Int): Formula =
    val (fixed, patterns) = cover.patterns(names)
    val free = names.filterNot(fixed.contains)
    val width = BigInt(patterns.size) * BigInt(2).pow(free.length)
    if width > BigInt(cap) then
      throw TranslationTooLarge(
        s"the covered case split over ${patterns.size} symbol patterns and ${free.length} other anchor " +
          s"references would need $width disjuncts, over this translation's cap of $cap"
      )
    var nodes = 0L
    val disjuncts =
      for
        (guard, pinned) <- patterns
        bits <- (0 until (1 << free.length)).toList
        valuation = pinned ++ free.zipWithIndex.map((name, i) => name -> (((bits >> i) & 1) == 1))
        body = fold(mapOperands(formula, substitute(_, valuation)))
        if body != Constant(false)
      yield
        val guards = guard :: free.map(name => if valuation(name) then Reference(name, Position.I) else Negation(Reference(name, Position.I)))
        val disjunct = fold(Conjunction(guards :+ body))
        nodes += size(disjunct)
        if nodes > cap then
          throw TranslationTooLarge(s"the covered case split exceeds this translation's cap of $cap nodes")
        disjunct
    fold(Disjunction(disjuncts))

  /** One temporal operator, case-split on its operands' anchor references. */
  private def split(formula: Formula, cap: Int, cover: Option[Cover]): Formula =
    val names = operandsOf(formula).flatMap(anchorReferences).distinct
    if names.isEmpty then formula
    else if cover.isDefined then coveredSplit(formula, names, cover.get, cap)
    else
      // Checked *before* building: the whole point of this translation is
      // that the product below is exponential, so materializing it and then
      // measuring would be the one mistake that matters here.
      val width = if names.length > 30 then BigInt(2).pow(31) else BigInt(2).pow(names.length)
      val estimated = width * BigInt(names.length + 1 + size(formula).toInt)
      if estimated > BigInt(cap) then
        throw TranslationTooLarge(
          s"the case split over ${names.length} anchor references would need 2^${names.length} disjuncts " +
            s"and about $estimated nodes, over this translation's cap of $cap — this is the exponential blow-up " +
            "the two-variable form avoids, not a limitation of the implementation"
        )
      val disjuncts =
        for bits <- 0 until (1 << names.length) yield
          val valuation = names.zipWithIndex.map((name, i) => name -> (((bits >> i) & 1) == 1)).toMap
          val guards = names.map(name => if valuation(name) then Reference(name, Position.I) else Negation(Reference(name, Position.I)))
          Conjunction(guards :+ mapOperands(formula, substitute(_, valuation)))
      Disjunction(disjuncts.toList)

  /** Walk the anchor-level structure, splitting every temporal operator. */
  private def translateBody(formula: Formula, cap: Int, cover: Option[Cover]): Formula = formula match
    case Negation(operand)     => Negation(translateBody(operand, cap, cover))
    case Conjunction(operands) => Conjunction(operands.map(translateBody(_, cap, cover)))
    case Disjunction(operands) => Disjunction(operands.map(translateBody(_, cap, cover)))
    case other if isTemporal(other) => split(other, cap, cover)
    case other                      => other

  private def size(formula: Formula): Long = formula match
    case Negation(operand)     => 1 + size(operand)
    case Conjunction(operands) => 1 + operands.map(size).sum
    case Disjunction(operands) => 1 + operands.map(size).sum
    case other                 => 1 + operandsOf(other).map(size).sum

  /** Translate `dag` into an equivalent one-variable formula.
    *
    * `cap` bounds both the width of a single case split and the size of the
    * inlined result; exceeding it raises [[TranslationTooLarge]] rather than
    * running the machine out of memory, since on the two-variable families
    * the blow-up is the expected outcome.
    *
    * With `cover` (default: the `elimination.cover` system property, on
    * unless set to `false`) the split ranges over the realizability cover
    * only; see [[Cover]].
    */
  /** The default `cap`: 2^22 nodes, or the `elimination.cap` system property. */
  private[brasp] def defaultCap: Int = sys.props.get("elimination.cap").map(_.toInt).getOrElse(1 << 22)

  def translate(dag: FormulaDag, cap: Int = defaultCap, cover: Boolean = coverEnabled): FormulaDag =
    val covering = Option.when(cover)(Cover(dag))
    var translated = VectorMap.empty[String, Formula]
    for (name, body) <- dag.definitions do translated = translated.updated(name, translateBody(body, cap, covering))

    // Inline every anchor reference. Memoized, so shared subformulas stay
    // shared as objects even though the result is conceptually a tree.
    val inlined = scala.collection.mutable.Map.empty[String, Formula]
    def inlineIn(formula: Formula): Formula = formula match
      case Reference(name, Position.I) => bodyOf(name)
      case Negation(operand)           => Negation(inlineIn(operand))
      case Conjunction(operands)       => Conjunction(operands.map(inlineIn))
      case Disjunction(operands)       => Disjunction(operands.map(inlineIn))
      case other                       => mapOperands(other, inlineIn)
    def bodyOf(name: String): Formula =
      inlined.getOrElseUpdate(
        name, {
          val body = translated.getOrElse(name, throw TranslationTooLarge(s"unknown definition '$name'"))
          inlineIn(body)
        },
      )

    val output = inlineIn(dag.output)
    val total = size(output)
    if total > cap then
      throw TranslationTooLarge(
        s"the one-variable formula has $total nodes, over this translation's cap of $cap — " +
          "the two-variable source is exponentially more succinct"
      )

    // Only definitions still reached through a witness reference survive; the
    // anchor-level ones have been inlined away.
    def witnessReferences(formula: Formula): Set[String] = formula match
      case Reference(name, Position.J) => Set(name) ++ witnessReferences(bodyOf(name))
      case Reference(_, _)             => Set.empty
      case Negation(operand)           => witnessReferences(operand)
      case Conjunction(operands)       => operands.flatMap(witnessReferences).toSet
      case Disjunction(operands)       => operands.flatMap(witnessReferences).toSet
      case other                       => operandsOf(other).flatMap(witnessReferences).toSet
    val kept = witnessReferences(output)
    val definitions = dag.definitions.keys.filter(kept).foldLeft(VectorMap.empty[String, Formula]) { (acc, name) =>
      acc.updated(name, bodyOf(name))
    }
    dag.copy(definitions = definitions, output = output)
