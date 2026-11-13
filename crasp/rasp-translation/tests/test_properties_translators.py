
import pytest

from rasptranslation.translation.properties_translators import (
    Comparison,
    DistanceMeasure,
    translate_check_string,
    translate_compare_strings,
    translate_robust_string,
)

class TestCheckTranslation:
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
        # Just check no crashes :/
        translate_check_string(prog, "P5")

class TestEqualityTranslation:
    def test_basic(self):
        prog1 = """
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
        prog2 = """
            ALPHABET: a b
            P1 = b
            P2 = a
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
        # Just check no crashes :/
        translate_compare_strings(prog1, "P5", prog2, "P5", Comparison.EQUAL)

class TestRobustTranslation:
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
        # Just check no crashes :/
        translate_robust_string(
            prog,
            "P5",
            DistanceMeasure.HAMMING,
            5,
            ["a", "b", "a"],
        )

if __name__ == "__main__":
    pytest.main([__file__, "-v"])

