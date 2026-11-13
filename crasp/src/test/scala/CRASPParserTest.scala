package Crasp

import munit.FunSuite

final class CatsParseCRASPParserTest extends FunSuite {

  private val progText =
    """ALPHABET: a b
      |
      |Qa = a
      |Qb = b
      |
      |V  = Qa & (0 < Cb)
      |Y  = CV = 0
      |Out = Y
      |
      |Cb = #Qb
      |CV = #V
      |""".stripMargin

  test("cats-parse CRASPParser parses sample program") {
    val res = CRASPParser.parse(progText, enforceOrder = false)
    assert(res.isRight, res.left.getOrElse(""))

    val p = res.toOption.get
    assertEquals(p.alphabet, Set("a", "b"))

    val boolNames  = p.boolRules.map(_.name).toSet
    val countNames = p.countRules.map(_.name).toSet

    assertEquals(boolNames, Set("Qa", "Qb", "V", "Y", "Out"))
    assertEquals(countNames, Set("Cb", "CV"))
  }
}
