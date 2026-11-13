package LS

import Crasp.*

object ProgramSize {

  // Tune these weights to bias toward oracle-like solutions
  final case class Weights(
    // base node weights
    boolNode: Int = 1,
    countNode: Int = 1,

    // penalties for "dangerous expressivity"
    orPenalty: Int = 8,
    windowCountPenalty: Int = 12,     // #<(s) or #<(s,e)
    periodAtomPenalty: Int = 6,       // %k=o
    conditionalPenalty: Int = 10,     // bn ? t : e
    minMaxPenalty: Int = 4,
    subtractPenalty: Int = 4
  )

  def programSize(p: CRASP, w: Weights = Weights()): Int = {
    val b = p.boolRules.map(r => boolSize(r.body, w)).sum
    val c = p.countRules.map(r => countSize(r.body, w)).sum
    b + c
  }

  private def boolSize(e: BoolExpr, w: Weights): Int = e match {
    case BoolExprAnd(l, r) => w.boolNode + boolSize(l, w) + boolSize(r, w)
    case BoolExprOr(l, r)  => w.boolNode + w.orPenalty + boolSize(l, w) + boolSize(r, w)
    case BoolExprNot(x)    => w.boolNode + boolSize(x, w)

    case a: BoolAtom => a match {
      case BoolAtomBoolRule(_)           => w.boolNode
      case BoolAtomInitial(_)            => w.boolNode
      case BoolAtomComparison(_, l, r)   => w.boolNode + countSize(l, w) + countSize(r, w)
      case BoolAtomEqual(l, r)           => w.boolNode + countSize(l, w) + countSize(r, w)
      case BoolAtomPositionPeriod(_, _)  => w.boolNode + w.periodAtomPenalty
      case _: BoolAtomRunParity          => w.boolNode
    }
  }

  private def countSize(e: CountExpr, w: Weights): Int = e match {
    case CountExprConditional(_, t, el) =>
      w.countNode + w.conditionalPenalty + countSize(t, w) + countSize(el, w)

    case CountExprAdd(l, r)      => w.countNode + countSize(l, w) + countSize(r, w)
    case CountExprSubtract(l, r) => w.countNode + w.subtractPenalty + countSize(l, w) + countSize(r, w)
    case CountExprMin(l, r)      => w.countNode + w.minMaxPenalty + countSize(l, w) + countSize(r, w)
    case CountExprMax(l, r)      => w.countNode + w.minMaxPenalty + countSize(l, w) + countSize(r, w)

    case a: CountAtom => a match {
      case CountAtomConst(_)         => w.countNode
      case CountAtomCountRule(_)     => w.countNode
      case CountAtomCounting(b, start, end) =>
        val winPenalty =
          (start, end) match {
            case (None, None) => 0
            case _            => w.windowCountPenalty
          }
        w.countNode + winPenalty + boolSize(b, w)
    }
  }
}
