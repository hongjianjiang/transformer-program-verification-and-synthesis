"""
Public API for turning C-RASP programs into Lustre.

The property translators build a `LustreProperty`, which bundles the Lustre
program with the data needed to talk to Kind2 (alphabet map, check node,
eos/eternity encodings). This module is a thin, stable front end over them
for callers that just want Lustre source text.

    from rasptranslation.api import crasp_to_lustre

    lustre = crasp_to_lustre(open("examples/dyck.crasp").read(), "D")

Every function accepts a C-RASP program as source text, a `Path` to a file,
or an already parsed `CRASP`.
"""

from enum import StrEnum
from pathlib import Path
from typing import List, Union

from rasptranslation.lustre.printer import print_lustre
from rasptranslation.rasp.ast_nodes import CRASP
from rasptranslation.rasp.parsers import parse_crasp
from rasptranslation.translation.crasp_translator import (
    translate_crasp_alphabet,
    translate_crasp_to_lustre,
)
from rasptranslation.translation.properties_translators import (
    END_OF_STREAM,
    ETERNITY,
    IN_VAR,
    MAIN_FUN,
    Comparison,
    DistanceMeasure,
    LustreProperty,
    reverse_map,
    translate_check,
    translate_compare,
    translate_robust,
    translate_word_acceptance,
)

CraspSource = Union[str, Path, CRASP]

class Property(StrEnum):
    """What to wrap the translated C-RASP program in.

    RAW   -- just the MAIN node and its helper nodes, no property to verify
    CHECK -- MAIN plus a check node asserting the output holds at end of stream
    """

    RAW = "raw"
    CHECK = "check"

def parse_word(word: Union[str, List[str]]) -> List[str]:
    """Read a word as a list of characters.

    Accepts a list of chars, a space separated string ("a b c") or a plain
    string of single char tokens ("abba").
    """
    if not isinstance(word, str):
        return list(word)
    w = word.strip()
    if not w:
        return []
    if " " in w:
        return [t for t in w.split(" ") if t != ""]
    return list(w)

def parse(source: CraspSource) -> CRASP:
    """Parse a C-RASP program from text, a file path or pass through a CRASP"""
    if isinstance(source, CRASP):
        return source
    if isinstance(source, Path):
        return parse_crasp(source.read_text())
    return parse_crasp(source)

def crasp_to_lustre_property(
    source: CraspSource,
    output: str,
    property: Property = Property.CHECK,
) -> LustreProperty:
    """Translate a C-RASP program to a Lustre program

    :param source: the C-RASP program, as text, a Path, or a parsed CRASP
    :param output: the name of the C-RASP bool rule that is the output
    :param property: whether to include a check node (see Property)
    """
    crasp = parse(source)
    if Property(property) == Property.RAW:
        return _translate_raw(crasp, output)
    return translate_check(crasp, output)

def crasp_to_lustre(
    source: CraspSource,
    output: str,
    property: Property = Property.CHECK,
) -> str:
    """Translate a C-RASP program to Lustre source text.

    See crasp_to_lustre_property for the arguments.
    """
    return print_lustre(crasp_to_lustre_property(source, output, property).lustre)

def compare_to_lustre_property(
    source1: CraspSource,
    output1: str,
    source2: CraspSource,
    output2: str,
    comparison: Comparison = Comparison.EQUAL,
) -> LustreProperty:
    """Translate two C-RASP programs into a Lustre program comparing them

    The two programs must share an alphabet. A counter example to the check
    node is a word on which the comparison fails.
    """
    return translate_compare(
        parse(source1),
        output1,
        parse(source2),
        output2,
        comparison,
    )

def compare_to_lustre(
    source1: CraspSource,
    output1: str,
    source2: CraspSource,
    output2: str,
    comparison: Comparison = Comparison.EQUAL,
) -> str:
    """Lustre source text comparing two C-RASP programs.

    See compare_to_lustre_property for the arguments.
    """
    return print_lustre(
        compare_to_lustre_property(
            source1,
            output1,
            source2,
            output2,
            comparison,
        ).lustre
    )

def robust_to_lustre_property(
    source: CraspSource,
    output: str,
    distance: int,
    word: Union[str, List[str]],
    measure: DistanceMeasure = DistanceMeasure.HAMMING,
) -> LustreProperty:
    """Translate a C-RASP program into a Lustre robustness property

    A counter example to the check node is a word within `distance` of `word`
    that the program classifies differently.
    """
    return translate_robust(
        parse(source),
        output,
        measure,
        distance,
        parse_word(word),
    )

def robust_to_lustre(
    source: CraspSource,
    output: str,
    distance: int,
    word: Union[str, List[str]],
    measure: DistanceMeasure = DistanceMeasure.HAMMING,
) -> str:
    """Lustre source text of a robustness property.

    See robust_to_lustre_property for the arguments.
    """
    return print_lustre(
        robust_to_lustre_property(
            source,
            output,
            distance,
            word,
            measure,
        ).lustre
    )

def accept_to_lustre_property(
    source: CraspSource,
    output: str,
    word: Union[str, List[str]],
) -> LustreProperty:
    """Translate a C-RASP program into a Lustre acceptance query for `word`

    The input stream is fixed to `word`, and the check is the negation of
    acceptance, so a counter example means the word is accepted.
    """
    return translate_word_acceptance(parse(source), output, parse_word(word))

def accept_to_lustre(
    source: CraspSource,
    output: str,
    word: Union[str, List[str]],
) -> str:
    """Lustre source text of an acceptance query.

    See accept_to_lustre_property for the arguments.
    """
    return print_lustre(accept_to_lustre_property(source, output, word).lustre)

def _translate_raw(crasp: CRASP, output: str) -> LustreProperty:
    """Translate with no property attached

    The alphabet still reserves eos and eternity so that the encoding of the
    characters matches the property translations. `check_node` is the main
    node, as there is no check node.
    """
    alphabet_map = translate_crasp_alphabet(
        crasp.alphabet | set([END_OF_STREAM, ETERNITY]),
    )
    lustre = translate_crasp_to_lustre(
        crasp,
        MAIN_FUN,
        IN_VAR,
        output,
        alphabet_map,
    )
    return LustreProperty(
        lustre=lustre,
        alphabet_map=reverse_map(alphabet_map),
        check_node=MAIN_FUN,
        input_var=IN_VAR,
        eos=alphabet_map[END_OF_STREAM],
        eternity=alphabet_map[ETERNITY],
    )
