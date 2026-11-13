import pytest

from pathlib import Path

from rasptranslation.api import (
    Property,
    accept_to_lustre,
    compare_to_lustre,
    crasp_to_lustre,
    crasp_to_lustre_property,
    parse,
    parse_word,
    robust_to_lustre,
)
from rasptranslation.rasp.parsers import parse_crasp
from rasptranslation.translation.properties_translators import Comparison

MAJORITY = """
    ALPHABET: a b
    Pa = a
    Pb = b
    M = Ca <= Cb
    Ca = #Pa
    Cb = #Pb
"""

TOMITA_2 = """
    ALPHABET: a b
    Good = (a & %2=0) | (b & %2=1)
    Bad = #(!Good)
    Out = (%2=1) & (Bad = 0)
"""

class TestSources:
    def test_from_text(self):
        assert "node MAIN" in crasp_to_lustre(MAJORITY, "M")

    def test_from_path(self, tmp_path):
        crasp_file = tmp_path / "majority.crasp"
        crasp_file.write_text(MAJORITY)
        assert crasp_to_lustre(crasp_file, "M") == crasp_to_lustre(MAJORITY, "M")

    def test_from_parsed_crasp(self):
        crasp = parse_crasp(MAJORITY)
        assert crasp_to_lustre(crasp, "M") == crasp_to_lustre(MAJORITY, "M")

    def test_parse_passes_through_crasp(self):
        crasp = parse_crasp(MAJORITY)
        assert parse(crasp) is crasp

class TestCraspToLustre:
    def test_check_includes_check_node(self):
        lustre = crasp_to_lustre(MAJORITY, "M", Property.CHECK)
        assert "node __CHECK__" in lustre
        assert "check __P__;" in lustre

    def test_check_is_the_default(self):
        assert crasp_to_lustre(MAJORITY, "M") == crasp_to_lustre(
            MAJORITY,
            "M",
            Property.CHECK,
        )

    def test_raw_has_no_check_node(self):
        lustre = crasp_to_lustre(MAJORITY, "M", Property.RAW)
        assert "node MAIN" in lustre
        assert "__CHECK__" not in lustre
        assert "check" not in lustre

    def test_raw_encodes_alphabet_like_check(self):
        """The eos/eternity chars are reserved even without a check node,
        so a letter has the same int in both translations."""
        raw = crasp_to_lustre_property(MAJORITY, "M", Property.RAW)
        check = crasp_to_lustre_property(MAJORITY, "M", Property.CHECK)
        assert raw.alphabet_map == check.alphabet_map
        assert (raw.eos, raw.eternity) == (check.eos, check.eternity)

    def test_property_exposes_kind2_metadata(self):
        prop = crasp_to_lustre_property(MAJORITY, "M")
        assert prop.check_node == "__CHECK__"
        assert prop.input_var == "__I__"
        assert prop.alphabet_map[prop.eos] == "__eos__"
        assert set(prop.alphabet_map.values()) == {
            "a",
            "b",
            "__eos__",
            "__eternity__",
        }

    def test_position_period_adds_position_var(self):
        assert "__POS__" in crasp_to_lustre(TOMITA_2, "Out")
        assert "__POS__" not in crasp_to_lustre(MAJORITY, "M")

    def test_unknown_output_rule_rejected(self):
        with pytest.raises(ValueError):
            crasp_to_lustre(MAJORITY, "NotARule")

class TestOtherProperties:
    def test_compare_has_both_programs(self):
        lustre = compare_to_lustre(MAJORITY, "M", MAJORITY, "M", Comparison.EQUAL)
        assert "node left_MAIN" in lustre
        assert "node right_MAIN" in lustre
        assert "node __COMPARE__" in lustre

    def test_compare_mismatched_alphabets_rejected(self):
        other = "ALPHABET: a c\nPa = a\nM = Ca <= Ca\nCa = #Pa\n"
        with pytest.raises(ValueError):
            compare_to_lustre(MAJORITY, "M", other, "M")

    def test_accept_fixes_the_input_stream(self):
        lustre = accept_to_lustre(MAJORITY, "M", "ab")
        # no input parameter: the word is the stream
        assert "node __CHECK__ ()" in lustre
        assert "__I__ = (2 -> pre((3 -> pre((0 -> 1)))));" in lustre

    def test_robust_has_distance_node(self):
        lustre = robust_to_lustre(MAJORITY, "M", 1, "a b")
        assert "node __DISTANCE_CHECK" in lustre

class TestParseWord:
    @pytest.mark.parametrize(
        "word,expected",
        [
            ("a b c", ["a", "b", "c"]),
            ("abba", ["a", "b", "b", "a"]),
            ("  a  b ", ["a", "b"]),
            ("", []),
            (["a", "b"], ["a", "b"]),
        ],
    )
    def test_parse_word(self, word, expected):
        assert parse_word(word) == expected
