
import pytest

from rasptranslation.rasp.parsers import parse_crasp
from rasptranslation.translation.crasp_translator import (
    translate_crasp_to_lustre,
)

class TestCRASPTranslation:
    def test_basic(self):
        prog = """
            ALPHABET: a b
            P1 = a
            P2 = b
            P3 = !P1 | P2
            P4 = P1 & P2 | P3
            P5 = C1 <= C2
            C1 = #P2
            C2 = #P4
            C3 = P3 ? C1 : C2
            C4 = C2 - C3
            C5 = C4 + C1
            C6 = min(C3, C1)
            C7 = max(C4, C3)
            C8 = 1
        """
        crasp_prog = parse_crasp(prog)
        translate_crasp_to_lustre(
            crasp_prog,
            "MAIN",
            "I",
            "P5",
        )

    def test_nested_count_expr(self):
        prog = """
            ALPHABET: a b
            P0 = a
            P1 = b
            P2 = #P0 <= #P1
        """
        prog = parse_crasp(prog)
        lustre = translate_crasp_to_lustre(prog, "MAIN", "I", "P2")
        assert "__COUNT_TRUE__(P0) <= __COUNT_TRUE__(P1)" in str(lustre)

    def test_nested_bool_expr(self):
        prog = """
            ALPHABET: a b
            P0 = a
            P1 = b
            P2 = #(P0&P1) <= #P1
        """
        prog = parse_crasp(prog)
        lustre = translate_crasp_to_lustre(prog, "MAIN", "I", "P2")
        assert "__COUNT_TRUE__((P0 and P1)) <= __COUNT_TRUE__(P1)" \
            in str(lustre)

    def test_position_periods(self):
        prog = """
            ALPHABET: a b
            P0 = % 5 = 3
            P1 = P0 & (% 3 = 1)
        """
        prog = parse_crasp(prog)
        lustre = translate_crasp_to_lustre(prog, "MAIN", "I", "P1")
        lustre_str = str(lustre)
        assert "__POS__ = (0 -> (pre(__POS__) + 1))" in lustre_str
        assert "3 = (__POS__ mod 5)" in lustre_str
        assert "1 = (__POS__ mod 3)" in lustre_str

    def test_local_count(self):
        prog = """
            ALPHABET: a b c
            P1 = C0 + C1 <= C2
            C0 = #a
            C1 = #<(3) b
            C2 = #<(1,2) c
        """
        prog = parse_crasp(prog)
        lustre = translate_crasp_to_lustre(prog, "MAIN", "I", "P1")
        lustre_str = str(lustre)
        assert "__COUNT_TRUE__((I = 0))" in lustre_str
        assert "__ONE_IF__(pre(pre(pre((I = 1)))))" in lustre_str
        assert "(__ONE_IF__(pre((I = 2))) + __ONE_IF__(pre(pre((I = 2)))))" \
            in lustre_str


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
