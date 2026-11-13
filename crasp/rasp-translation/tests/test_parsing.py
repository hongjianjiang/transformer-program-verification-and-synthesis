
import pytest

from rasptranslation.rasp.parsers import parse_crasp

class TestCRASPParser:
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
        prog = parse_crasp(prog)
        assert prog.alphabet == set(['a', 'b'])
        assert len(prog.bool_rules) == 5
        assert len(prog.count_rules) == 8

    def test_bad_bool_order(self):
        prog = """
            ALPHABET: b
            P1 = P2
            P2 = b
        """
        self._expect_syntax_error(prog)

    def test_bad_count(self):
        prog = """
            ALPHABET: a
            P0 = a
            P1 = C1 <= C2
            C1 = #P0
        """
        self._expect_syntax_error(prog)

    def test_bad_count_bool_rule(self):
        prog = """
            ALPHABET: a b
            P0 = a
            P1 = b
            C1 = #P1
            C2 = #P2
        """
        self._expect_syntax_error(prog)

    def test_bad_count_conditional(self):
        prog = """
            ALPHABET: a
            P0 = a
            C0 = #P0
            C1 = P1 ? C0 : C2
        """
        self._expect_syntax_error(prog)

    def test_bad_count_add(self):
        prog = """
            ALPHABET: a
            P0 = a
            C0 = #P0
            C1 = C0 + C1
        """
        self._expect_syntax_error(prog)

    def test_bad_count_subtract(self):
        prog = """
            ALPHABET: a
            P0 = a
            C0 = #P0
            C1 = C0 - C1
        """
        self._expect_syntax_error(prog)

    def test_bad_count_min(self):
        prog = """
            ALPHABET: a
            P0 = a
            C0 = #P0
            C1 = min(C0, C1)
        """
        self._expect_syntax_error(prog)

    def test_bad_count_max(self):
        prog = """
            ALPHABET: a
            P0 = a
            C0 = #P0
            C1 = max(C0, C1)
        """
        self._expect_syntax_error(prog)

    def test_nested_count_expr(self):
        prog = """
            ALPHABET: a
            P0 = a
            P1 = b
            P2 = #P0 <= #P1
        """
        parse_crasp(prog)

    def test_nested_bool_expr(self):
        prog = """
            ALPHABET: a
            P0 = a
            P1 = b
            P2 = #(P0&P1) <= #P1
        """
        parse_crasp(prog)

    def test_position_periods(self):
        prog = """
            ALPHABET: a
            P0 = % 5 = 1
        """
        parse_crasp(prog)

    def test_count_at_position(self):
        prog = """
            ALPHABET: a
            P0 = % 5 = 1
            C1 = #<(1) P0
        """
        parse_crasp(prog)

    def test_count_range(self):
        prog = """
            ALPHABET: a
            P0 = % 5 = 1
            C1 = #<(1,3) P0
        """
        parse_crasp(prog)

    def _expect_syntax_error(self, prog):
        try:
            prog = parse_crasp(prog)
            assert False, f"{prog} should not parse"
        except SyntaxError:
            pass

if __name__ == "__main__":
    pytest.main([__file__, "-v"])
