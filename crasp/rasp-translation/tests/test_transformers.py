
import pytest

from rasptranslation.rasp.parsers import parse_crasp
from rasptranslation.rasp.transformers import RuleRenamer
from rasptranslation.rasp.walkers import RuleNameCollector

class TestTranslation:
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
        renamed = RuleRenamer(
            lambda bn : "bool_" + bn,
            lambda cn : "count_" + cn,
        ).transform_crasp(prog)
        collector = RuleNameCollector()
        collector.walk_crasp(renamed)
        assert collector.defined_bool_rule_names == set([
            'bool_P1',
            'bool_P2',
            'bool_P3',
            'bool_P4',
            'bool_P5',
        ])
        assert collector.used_bool_rule_names == set([
            'bool_P1',
            'bool_P2',
            'bool_P3',
            'bool_P4',
        ])
        assert collector.defined_count_rule_names == set([
            'count_C1',
            'count_C2',
            'count_C3',
            'count_C4',
            'count_C5',
            'count_C6',
            'count_C7',
            'count_C8',
        ])
        assert collector.used_count_rule_names == set([
            'count_C1',
            'count_C2',
            'count_C3',
            'count_C4',
        ])

if __name__ == "__main__":
    pytest.main([__file__, "-v"])

