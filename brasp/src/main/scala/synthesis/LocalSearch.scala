package brasp.synthesis

import brasp.*
import brasp.BooleanExpression.*
import brasp.Subprogram.*
import scala.util.Random

/** Sample-based synthesis in the Boolean, strictly past-masked B-RASP fragment. */
object LocalSearch:
  final case class Example(word: Vector[String], accepted: Boolean)

  final case class Config(
      seed: Long = 0,
      iterations: Int = 20000,
      restarts: Int = 4,
      slots: Int = 6,
      expressionDepth: Int = 2,
      startTemperature: Double = 1.0,
      cooling: Double = 0.9995,
      /** Weight each error by its class size (FN/#pos + FP/#neg) instead of
        * counting errors. Avoids the reject-everything trap when distinct
        * positives are rare (e.g. FLaRe repeat-01). */
      balanced: Boolean = false,
  ):
    require(iterations >= 0 && restarts > 0 && slots > 0 && expressionDepth >= 0)
    require(startTemperature.isFinite && startTemperature > 0)
    require(cooling.isFinite && cooling > 0 && cooling <= 1)

  final case class Result(program: Program, errors: Int, samples: Int, evaluations: Long):
    def fitsSamples: Boolean = errors == 0

  /** Remove definitions unreachable from the selected output, preserving order. */
  def prune(program: Program): Program =
    val needed = scala.collection.mutable.Set(program.output)
    program.subprograms.reverseIterator.foreach { node =>
      if needed(node.name) then needed ++= Brasp.references(node).map(_.name)
    }
    program.copy(subprograms = program.subprograms.filter(n => needed(n.name)))

  private def size(expression: BooleanExpression): Int = expression match
    case Const(_) | Ref(_, _) => 1
    case Not(e) => 1 + size(e)
    case And(es) => 1 + es.map(size).sum
    case Or(es) => 1 + es.map(size).sum

  private def complexity(program: Program): Int = prune(program).subprograms.map {
    case BooleanNode(_, e) => 1 + size(e)
    case RightmostAttention(_, s, v) => 1 + size(s) + size(v)
    case LeftmostAttention(_, s, v) => 1 + size(s) + size(v)
    case _ => 1
  }.sum

  /** Iterations are per restart. A zero-error result is sample consistency only. */
  def learn(alphabet: Vector[String], examples: Seq[Example], config: Config = Config()): Result =
    require(alphabet.nonEmpty && alphabet.distinct == alphabet && alphabet.forall(_.nonEmpty),
      "alphabet must contain distinct nonempty symbols")
    require(examples.nonEmpty, "at least one labelled example is required")
    require(examples.forall(_.word.forall(alphabet.contains)), "sample contains a symbol outside the alphabet")
    val data = examples.distinct.toVector
    require(data.groupBy(_.word).values.forall(_.map(_.accepted).distinct.size == 1),
      "conflicting labels for the same word")
    val rng = new Random(config.seed)
    val inputs: List[Subprogram] = Bos("bos_input") :: alphabet.zipWithIndex.map {
      case (symbol, i) => SymbolNode(s"symbol_$i", symbol)
    }.toList
    val names = inputs.map(_.name).toVector ++ Vector.tabulate(config.slots)(i => s"node_$i")

    def atom(available: Vector[String], predicate: Boolean): BooleanExpression =
      val choice = rng.nextInt(available.size + 2)
      if choice >= available.size then Const(choice == available.size)
      else Ref(available(choice), if predicate && rng.nextBoolean() then Position.J else Position.I)

    def expression(available: Vector[String], predicate: Boolean, depth: Int): BooleanExpression =
      if depth == 0 then atom(available, predicate)
      else rng.nextInt(6) match
        case 0 => Not(expression(available, predicate, depth - 1))
        case 1 => And(List.fill(2)(expression(available, predicate, depth - 1)))
        case 2 => Or(List.fill(2)(expression(available, predicate, depth - 1)))
        case _ => atom(available, predicate)

    def mutateExpr(e: BooleanExpression, available: Vector[String], predicate: Boolean, depth: Int): BooleanExpression =
      if depth == 0 || rng.nextBoolean() then expression(available, predicate, depth)
      else e match
        case Not(x) => Not(mutateExpr(x, available, predicate, depth - 1))
        case And(xs) =>
          val i = rng.nextInt(xs.size)
          And(xs.updated(i, mutateExpr(xs(i), available, predicate, depth - 1)))
        case Or(xs) =>
          val i = rng.nextInt(xs.size)
          Or(xs.updated(i, mutateExpr(xs(i), available, predicate, depth - 1)))
        case _ => expression(available, predicate, depth)

    def fresh(index: Int): Subprogram =
      val available = names.take(index)
      def expr(predicate: Boolean) = expression(available, predicate, config.expressionDepth)
      rng.nextInt(4) match
        case 0 => RightmostAttention(names(index), expr(true), expr(true))
        case 1 => LeftmostAttention(names(index), expr(true), expr(true))
        case _ => BooleanNode(names(index), expr(false))

    def initial(): Program = Program(
      inputs ++ (inputs.size until names.size).map(fresh), names.last, Some(alphabet.toList))

    def mutate(p: Program): Program =
      if rng.nextInt(5) == 0 then p.copy(output = names(rng.nextInt(names.size)))
      else
        val i = inputs.size + rng.nextInt(config.slots)
        val available = names.take(i)
        def edit(e: BooleanExpression, predicate: Boolean) =
          mutateExpr(e, available, predicate, config.expressionDepth)
        val node = if rng.nextInt(4) == 0 then fresh(i) else p.subprograms(i) match
          case BooleanNode(n, e) => BooleanNode(n, edit(e, false))
          case RightmostAttention(n, s, v) =>
            if rng.nextBoolean() then RightmostAttention(n, edit(s, true), v)
            else RightmostAttention(n, s, edit(v, true))
          case LeftmostAttention(n, s, v) =>
            if rng.nextBoolean() then LeftmostAttention(n, edit(s, true), v)
            else LeftmostAttention(n, s, edit(v, true))
          case _ => fresh(i)
        p.copy(subprograms = p.subprograms.updated(i, node))

    val positives = data.count(_.accepted)
    val negatives = data.size - positives
    // Error unit: 1 per error, or (balanced) |data|/(2*classSize) so the
    // total stays on the same scale as a raw count and the temperature
    // schedule keeps its meaning.
    val posWeight = if config.balanced && positives > 0 then data.size / (2.0 * positives) else 1.0
    val negWeight = if config.balanced && negatives > 0 then data.size / (2.0 * negatives) else 1.0

    /** (weighted error, raw error count, size); zero raw errors = fits. */
    type Score = (Double, Int, Int)
    var evaluations = 0L
    def score(p: Program): Score =
      evaluations += 1
      val live = prune(p)
      var weighted = 0.0
      var raw = 0
      for e <- data if Brasp.accepts(live, e.word) != e.accepted do
        raw += 1
        weighted += (if e.accepted then posWeight else negWeight)
      (weighted, raw, complexity(live))
    def better(a: Score, b: Score): Boolean =
      a._2 == 0 && b._2 != 0 || (a._2 == 0) == (b._2 == 0) && (a._1 < b._1 || a._1 == b._1 && a._3 < b._3)
    // The bounded size term is strictly below one (weighted) error unit.
    def energy(s: Score): Double = s._1 + 0.1 * math.min(posWeight, negWeight) * s._3 / (s._3 + 1.0)

    var best = initial()
    var bestScore = score(best)
    for restart <- 0 until config.restarts if bestScore._2 != 0 do
      var current = if restart == 0 then best else initial()
      var currentScore = if restart == 0 then bestScore else score(current)
      if better(currentScore, bestScore) then
        best = current
        bestScore = currentScore
      var temperature = config.startTemperature
      var iteration = 0
      while iteration < config.iterations && bestScore._2 != 0 do
        val candidate = mutate(current)
        val candidateScore = score(candidate)
        if better(candidateScore, bestScore) then
          best = candidate
          bestScore = candidateScore
        val delta = energy(candidateScore) - energy(currentScore)
        if delta <= 0 || rng.nextDouble() < math.exp(-delta / math.max(temperature, 1e-9)) then
          current = candidate
          currentScore = candidateScore
        temperature *= config.cooling
        iteration += 1
    Result(prune(best), bestScore._2, data.size, evaluations)
