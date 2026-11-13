
import pytest

from rasptranslation.rasp.parsers import parse_crasp
from rasptranslation.rasp.walkers import HasPositionPeriods

class TestCRASPWalkers:
    def test_position_periods(self):
        prog = """
            ALPHABET: a
            P0 = % 5 = 1
            P1 = (% 3 = 2) & P0
        """
        prog = parse_crasp(prog)
        assert HasPositionPeriods().walk_crasp(prog).has_position_periods

if __name__ == "__main__":
    pytest.main([__file__, "-v"])
