import Crasp.*
import LS.*

class DatasetLearningTest extends munit.FunSuite:
  test("printer orders shared Boolean and count dependencies before use") {
    val p = CRASP(Set("a", "b"), List(
      BoolRule("B1", BoolAtomComparison(true, CountAtomCountRule("C1"), CountAtomConst(2))),
      BoolRule("B2", BoolAtomInitial("a")),
      BoolRule("Out", BoolAtomBoolRule("B1"))),
      List(CountRule("C1", CountAtomCounting(BoolAtomBoolRule("B2"), None, None))))
    val order = GlobalOrder.makeRuleOrder(p.boolRules, p.countRules)
    assert(order.indexOf("B2") < order.indexOf("C1"))
    assert(order.indexOf("C1") < order.indexOf("B1"))
    assert(CRASPParser.parse(Printer.pretty(p), enforceOrder = false).isRight)
  }

  test("shared CLI search grammar produces parseable programs with the same sample labels") {
    val pos = List("a", "ab", "ba")
    val neg = List("", "b", "bb")
    val result = UniversalLearner.learn(pos, neg, UniversalLearnerConfig(
      numBoolVars = 2, numCountVars = 2, allowRunParity = false,
      sa = LocalSearchSA.SAConfig(iters = 200, continueAfterPerfect = false)), seed = 42,
      alphabetOverride = Some(Set("a", "b")))
    val text = Printer.pretty(result.program)
    assert(!text.contains("rp("))
    val parsed = CRASPParser.parse(text, enforceOrder = false).fold(fail(_), identity)
    val before = new CRASPEvaluator(result.program)
    val after = new CRASPEvaluator(parsed)
    (pos ++ neg).foreach { w =>
      assertEquals(after.classifyWith("Out", w.toVector.map(_.toString)), before.classifyWith("Out", w.toVector.map(_.toString)))
    }
  }
