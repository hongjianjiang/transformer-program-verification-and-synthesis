// File: src/main/scala/LS/GlobalOrder.scala
package LS

import Crasp.*

object GlobalOrder {

  // ---- dependency extraction ----

  private def depsBool(e: BoolExpr): Set[String] = e match {
    case BoolExprAnd(l, r) => depsBool(l) ++ depsBool(r)
    case BoolExprOr(l, r)  => depsBool(l) ++ depsBool(r) // harmless even if you never generate OR
    case BoolExprNot(x)    => depsBool(x)

    case BoolAtomBoolRule(nm) => Set(nm)

    case BoolAtomComparison(_, l, r) => depsCount(l) ++ depsCount(r)
    case BoolAtomEqual(l, r)         => depsCount(l) ++ depsCount(r)

    case _ => Set.empty
  }

  private def depsCount(e: CountExpr): Set[String] = e match {
    case CountExprConditional(bn, t, el) =>
      Set(bn) ++ depsCount(t) ++ depsCount(el)

    case CountExprAdd(l, r)      => depsCount(l) ++ depsCount(r)
    case CountExprSubtract(l, r) => depsCount(l) ++ depsCount(r)
    case CountExprMin(l, r)      => depsCount(l) ++ depsCount(r)
    case CountExprMax(l, r)      => depsCount(l) ++ depsCount(r)

    case a: CountAtom => a match {
      case CountAtomConst(_) => Set.empty
      case CountAtomCountRule(nm) => Set(nm)
      case CountAtomCounting(b, _, _) => depsBool(b)
    }
  }

  /**
   * Stable topo order across ALL rules (bool+count interleaved).
   * Forces outName last if present.
   */
  def makeRuleOrder(
    boolRules: List[BoolRule],
    countRules: List[CountRule],
    outName: String = "Out"
  ): Vector[String] = {

    val stableAll: Vector[String] =
      (boolRules.map(_.name) ++ countRules.map(_.name)).distinct.toVector

    val allNames = stableAll.toSet

    val depsOf: Map[String, Set[String]] = {
      val b = boolRules.map(r => r.name -> depsBool(r.body).intersect(allNames)).toMap
      val c = countRules.map(r => r.name -> depsCount(r.body).intersect(allNames)).toMap
      b ++ c
    }

    val need = scala.collection.mutable.Map.empty[String, Int]
    stableAll.foreach(n => need(n) = depsOf.getOrElse(n, Set.empty).size)

    val dependees =
      scala.collection.mutable.Map.empty[String, scala.collection.mutable.ArrayBuffer[String]]

    stableAll.foreach { n =>
      depsOf.getOrElse(n, Set.empty).foreach { d =>
        dependees.getOrElseUpdate(d, scala.collection.mutable.ArrayBuffer.empty) += n
      }
    }

    val q = scala.collection.mutable.Queue.empty[String]
    stableAll.foreach { n => if (need(n) == 0) q.enqueue(n) }

    val out = scala.collection.mutable.ArrayBuffer.empty[String]
    val seen = scala.collection.mutable.Set.empty[String]

    while (q.nonEmpty) {
      val n = q.dequeue()
      if (!seen(n)) {
        seen += n
        out += n
        dependees.getOrElse(n, scala.collection.mutable.ArrayBuffer.empty).foreach { m =>
          need(m) = need(m) - 1
          if (need(m) == 0) q.enqueue(m)
        }
      }
    }

    val outSet = out.toSet
    val rest = stableAll.filterNot(outSet.contains)
    val base = (out.toVector ++ rest).distinct

    if (base.exists(_.equalsIgnoreCase(outName))) {
      val (o, others) = base.partition(_.equalsIgnoreCase(outName))
      (others ++ o).toVector
    } else base.toVector
  }
}
