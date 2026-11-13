package Crasp

// ==============================================================================================
// AST (verifier-compatible)
// ==============================================================================================

// ---------- Boolean expressions ----------
sealed trait BoolExpr
final case class BoolExprAnd(left: BoolExpr, right: BoolExpr) extends BoolExpr
final case class BoolExprOr(left: BoolExpr, right: BoolExpr) extends BoolExpr
final case class BoolExprNot(expr: BoolExpr) extends BoolExpr

sealed trait BoolAtom extends BoolExpr
final case class BoolAtomBoolRule(boolRuleName: String) extends BoolAtom
final case class BoolAtomInitial(char: String) extends BoolAtom
final case class BoolAtomComparison(strict: Boolean, left: CountExpr, right: CountExpr) extends BoolAtom
final case class BoolAtomEqual(left: CountExpr, right: CountExpr) extends BoolAtom
final case class BoolAtomPositionPeriod(period: Int, offset: Int) extends BoolAtom
/** True iff w[pos] == char AND the maximal run of char ending at pos has odd length. */
final case class BoolAtomRunParity(char: String) extends BoolAtom

final case class BoolRule(name: String, body: BoolExpr)

// ---------- Count expressions ----------
sealed trait CountExpr
sealed trait CountAtom extends CountExpr

final case class CountAtomCounting(
  boolExpr: BoolExpr,
  rangeStart: Option[Int] = None,
  rangeEnd: Option[Int] = None
) extends CountAtom

final case class CountExprConditional(
  boolRuleName: String,
  exprThen: CountExpr,
  exprElse: CountExpr
) extends CountExpr

final case class CountExprAdd(left: CountExpr, right: CountExpr) extends CountExpr
final case class CountExprSubtract(left: CountExpr, right: CountExpr) extends CountExpr
final case class CountExprMin(left: CountExpr, right: CountExpr) extends CountExpr
final case class CountExprMax(left: CountExpr, right: CountExpr) extends CountExpr

final case class CountAtomConst(value: Int) extends CountAtom
final case class CountAtomCountRule(countRuleName: String) extends CountAtom

final case class CountRule(name: String, body: CountExpr)

// ---------- Program ----------
final case class CRASP(
  alphabet: Set[String],
  boolRules: List[BoolRule],
  countRules: List[CountRule]
)

// ==============================================================================================
// Interpreter (paper/translator-aligned acceptance)
// ==============================================================================================

/**
 * CRASP Evaluator
 *
 * Two notions of "acceptance":
 *
 * (A) Legacy "finite-word" semantics (debug):
 *     - classify/score evaluate the output rule at the LAST position of the given finite word.
 *
 * (B) Paper/translator semantics (what Kind2 translator uses for accept(word)):
 *     - build stream = word ++ ["__eos__"]
 *     - accepted(word)  <=>  pre(Out) holds when I="__eos__"
 *     - equivalently: Out holds at position (stream.length - 2)
 *
 * Use:
 *   - classifyWordAcceptance(word)   // paper/translator acceptance
 *
 * Optimization: memo/state arrays are pre-allocated at instance level and reused across
 * word evaluations (cleared cheaply with Arrays.fill on the byte state arrays only).
 * This eliminates the dominant per-word GC allocation hotspot.
 */
final class CRASPEvaluator(program: CRASP) {

  // -----------------------
  // Rule name indices (case-insensitive)
  // -----------------------
  private val boolIndex: Map[String, Int] =
    program.boolRules.map(_.name.toLowerCase).zipWithIndex.toMap

  private val countIndex: Map[String, Int] =
    program.countRules.map(_.name.toLowerCase).zipWithIndex.toMap

  private val defaultOutName: String =
    program.boolRules.find(_.name == "Out") match {
      case Some(_) => "Out"
      case None =>
        throw new IllegalArgumentException("CRASP program has no BoolRule named 'Out'")
    }

  // -----------------------
  // Pre-allocated memo/state tables (resized lazily on demand)
  // -----------------------
  private val _B = program.boolRules.length
  private val _C = program.countRules.length

  private var _cap       = 0
  private var _boolMemo  = Array.ofDim[Boolean](0, 0)
  private var _countMemo = Array.ofDim[Int](0, 0)
  private var _boolState = Array.ofDim[Byte](0, 0)
  private var _countState= Array.ofDim[Byte](0, 0)

  /** Grow backing arrays if the word is longer than current capacity. */
  private def ensureCapacity(len: Int): Unit =
    if (len > _cap) {
      val c = math.max(len, _cap * 2 + 16)
      _boolMemo   = Array.ofDim[Boolean](_B, c)
      _countMemo  = Array.ofDim[Int](_C, c)
      _boolState  = Array.ofDim[Byte](_B, c)
      _countState = Array.ofDim[Byte](_C, c)
      _cap = c
    }

  /**
   * Reset only the state byte arrays for positions [0, len).
   * Value arrays (_boolMemo / _countMemo) need not be cleared: they are only read
   * when the corresponding state entry == 2 (done), which cannot be set without also
   * writing the value.
   */
  private def clearState(len: Int): Unit = {
    var i = 0
    while (i < _B) { java.util.Arrays.fill(_boolState(i),  0, len, 0.toByte); i += 1 }
    i = 0
    while (i < _C) { java.util.Arrays.fill(_countState(i), 0, len, 0.toByte); i += 1 }
  }

  // -----------------------
  // Index helpers (class-level, used inside evalBoolRuleAt closures)
  // -----------------------
  private def boolIdx(name: String): Int  = boolIndex.getOrElse(name.toLowerCase, -1)
  private def countIdx(name: String): Int = countIndex.getOrElse(name.toLowerCase, -1)

  // ============================================================================================
  // Public API (legacy finite-word semantics)
  // ============================================================================================

  /** Legacy: evaluate "Out" at last position of the given word (NO EOS added). */
  def classify(word: Vector[String]): Boolean =
    classifyWith(defaultOutName, word)

  /** Legacy: evaluate ruleName at last position of the given word (NO EOS added). */
  def classifyWith(ruleName: String, word: Vector[String]): Boolean =
    scoreWith(ruleName, word) >= 0.0

  /** Legacy: score at last position of the given word (NO EOS added). */
  def score(word: Vector[String]): Double =
    scoreWith(defaultOutName, word)

  /** Legacy: score at last position of the given word (NO EOS added). */
  def scoreWith(ruleName: String, word: Vector[String]): Double = {
    if (word.isEmpty) -1.0
    else {
      val ok = evalBoolRuleAt(ruleName, word, word.length - 1)
      if (ok) 1.0 else -1.0
    }
  }

  // ============================================================================================
  // Paper/translator acceptance (Pre(Out) at EOS)
  // ============================================================================================

  /** Evaluate a bool rule at a specific position (0-based) in the given word. */
  def evalBoolAt(ruleName: String, word: Vector[String], pos: Int): Boolean = {
    if (word.isEmpty) false
    else {
      val p = math.max(0, math.min(pos, word.length - 1))
      evalBoolRuleAt(ruleName, word, p)
    }
  }

  /**
   * Paper-style acceptance used by your translator:
   *
   *   stream = word ++ [__eos__]
   *   accepted(word) iff pre(Out) holds at EOS
   *                iff Out holds at position (eosPos - 1)
   */
  def classifyWordAcceptanceWith(ruleName: String, word: Vector[String], eosToken: String): Boolean = {
    val stream = word :+ eosToken
    val eosPos = stream.length - 1
    val prePos = eosPos - 1
    if (prePos < 0) false
    else evalBoolAt(ruleName, stream, prePos)
  }

  /** Convenience: acceptance with default Out rule, explicit eosToken. */
  def classifyWordAcceptance(word: Vector[String], eosToken: String): Boolean =
    classifyWordAcceptanceWith(defaultOutName, word, eosToken)

  /** Convenience: acceptance with eosToken="__eos__". */
  def classifyWordAcceptance(word: Vector[String]): Boolean =
    classifyWordAcceptance(word, "__eos__")

  // ============================================================================================
  // Core evaluation (reuses pre-allocated memo tables across calls)
  // ============================================================================================

  /**
   * Evaluate a bool rule (by name) at position i on word w.
   *
   * Uses instance-level memo tables sized by (#rules x word length) with cycle detection.
   * Tables are grown lazily if needed, and only the state byte arrays are cleared between
   * calls (O(B*len + C*len) bytes, very fast via Arrays.fill).
   */
  private def evalBoolRuleAt(ruleName: String, w: Vector[String], i: Int): Boolean = {
    if (w.isEmpty) return false

    ensureCapacity(w.length)
    clearState(w.length)

    // -----------------------
    // Evaluate a bool RULE by index with memo + cycle detection
    // -----------------------
    def evalBoolRuleByIdx(idx: Int, pos: Int): Boolean = {
      if (idx < 0 || idx >= _B) throw new IllegalArgumentException(s"Invalid bool rule index: $idx")
      if (pos < 0 || pos >= w.length) return false

      _boolState(idx)(pos) match {
        case 2 => _boolMemo(idx)(pos)
        case 1 =>
          throw new IllegalArgumentException(
            s"Cyclic bool rule dependency: '${program.boolRules(idx).name}' at pos=$pos"
          )
        case _ =>
          _boolState(idx)(pos) = 1
          val v = evalBool(program.boolRules(idx).body, pos)
          _boolMemo(idx)(pos) = v
          _boolState(idx)(pos) = 2
          v
      }
    }

    // -----------------------
    // Evaluate a count RULE by index with memo + cycle detection
    // -----------------------
    def evalCountRuleByIdx(idx: Int, pos: Int): Int = {
      if (idx < 0 || idx >= _C) throw new IllegalArgumentException(s"Invalid count rule index: $idx")
      if (pos < 0 || pos >= w.length) return 0

      _countState(idx)(pos) match {
        case 2 => _countMemo(idx)(pos)
        case 1 =>
          throw new IllegalArgumentException(
            s"Cyclic count rule dependency: '${program.countRules(idx).name}' at pos=$pos"
          )
        case _ =>
          _countState(idx)(pos) = 1
          val v = evalCount(program.countRules(idx).body, pos)
          _countMemo(idx)(pos) = v
          _countState(idx)(pos) = 2
          v
      }
    }

    // -----------------------
    // Boolean expression evaluation
    // -----------------------
    def evalBool(expr: BoolExpr, pos: Int): Boolean = expr match {
      case BoolExprAnd(l, r) => evalBool(l, pos) && evalBool(r, pos)
      case BoolExprOr(l, r)  => evalBool(l, pos) || evalBool(r, pos)
      case BoolExprNot(e)    => !evalBool(e, pos)

      case a: BoolAtom => a match {
        case BoolAtomInitial(ch) =>
          if (pos < 0 || pos >= w.length) false else w(pos) == ch

        case BoolAtomPositionPeriod(period, offset) =>
          if (period <= 0) false
          else {
            // Translator/Kind2-aligned: DO NOT normalize offset.
            // Interpret as: (pos mod period) == offset  (0-based pos).
            val p = ((pos % period) + period) % period
            p == offset
          }

        case BoolAtomRunParity(ch) =>
          if (pos < 0 || pos >= w.length || w(pos) != ch) false
          else {
            // Walk backwards to find the start of the current run of ch.
            var k = pos
            while (k > 0 && w(k - 1) == ch) k -= 1
            ((pos - k + 1) & 1) == 1  // odd length
          }

        case BoolAtomBoolRule(nm) =>
          val idx = boolIdx(nm)
          if (idx < 0) throw new IllegalArgumentException(s"Unknown bool rule: $nm")
          evalBoolRuleByIdx(idx, pos)

        case BoolAtomComparison(strict, left, right) =>
          val l = evalCount(left, pos)
          val r = evalCount(right, pos)
          if (strict) l < r else l <= r

        case BoolAtomEqual(left, right) =>
          evalCount(left, pos) == evalCount(right, pos)
      }
    }

    // -----------------------
    // Count expression evaluation
    // -----------------------
    def evalCount(expr: CountExpr, pos: Int): Int = expr match {
      // ---- Atoms ----
      case a: CountAtom => a match {
        case CountAtomConst(v) => v

        case CountAtomCountRule(nm) =>
          val idx = countIdx(nm)
          if (idx < 0) throw new IllegalArgumentException(s"Unknown count rule: $nm")
          evalCountRuleByIdx(idx, pos)

        /**
         * Counting: count positions j in a range [L..H] such that bexpr(j) is true.
         *
         * Range interpretation:
         *   - (None, None):          prefix [0..pos]
         *   - (Some(s), None):       SINGLE point [pos-s .. pos-s]   (used for "#<(s) ...")
         *   - (Some(s), Some(e)):    window [pos-e .. pos-s]
         *   - (None, Some(e)):       suffix [pos-e .. pos]
         *
         * Then clipped to valid indices, and clipped to <= pos (prefix semantics).
         */
        case CountAtomCounting(bexpr, rangeStart, rangeEnd) =>
          if (w.isEmpty) 0
          else {
            val (lo0, hi0) = (rangeStart, rangeEnd) match {
              case (None, None)       => (0, pos)
              case (Some(s), None)    => (pos - s, pos - s)
              case (Some(s), Some(e)) => (pos - e, pos - s)
              case (None, Some(e))    => (pos - e, pos)
            }

            val lo = math.min(lo0, hi0)
            val hi = math.max(lo0, hi0)

            // clip to word boundaries and to <= pos
            val L = math.max(0, lo)
            val H = math.min(pos, hi)

            if (H < 0 || L >= w.length || L > H) 0
            else {
              var c = 0
              var j = L
              val end = math.min(w.length - 1, H)
              while (j <= end) {
                if (evalBool(bexpr, j)) c += 1
                j += 1
              }
              c
            }
          }
      }

      // ---- Conditionals ----
      // Guard is a bool rule name (identifier), per your parser constraint.
      case CountExprConditional(bn, t, e) =>
        val idx = boolIdx(bn)
        if (idx < 0) throw new IllegalArgumentException(s"Unknown bool guard rule: $bn")
        val cond = evalBoolRuleByIdx(idx, pos)
        if (cond) evalCount(t, pos) else evalCount(e, pos)

      // ---- Arithmetic ----
      case CountExprAdd(l, r)      => evalCount(l, pos) + evalCount(r, pos)
      case CountExprSubtract(l, r) => evalCount(l, pos) - evalCount(r, pos)
      case CountExprMin(l, r)      => math.min(evalCount(l, pos), evalCount(r, pos))
      case CountExprMax(l, r)      => math.max(evalCount(l, pos), evalCount(r, pos))
    }

    // Evaluate requested rule at position i
    val outIdx = boolIdx(ruleName)
    if (outIdx < 0) throw new IllegalArgumentException(s"Unknown output bool rule: $ruleName")
    evalBoolRuleByIdx(outIdx, i)
  }
}
