"""
Tool for translation of RASP programs into other verification languages
and verifying

Currently only C-RASP -> Lustre supported. Some verification implemented
via Kind2.

<config> is a config file. By default, config.toml, or default values if
it is not found. See config_example.toml.

Commands:
    translate -- print the Lustre translation of a program, no verification
    translate_compare -- print the Lustre program that compares two programs
                (the same program that equal/subset/disjoint verify)
    check   -- check that the given bool rule is always true
    equal   -- check that the two programs produce equal output
    subset  -- check if program 1 has an output that is a subset of program 2
    disjoint -- check if program 1 and 2 have no common words (negation
                of intersection)
    robust  -- check if there's a word within a certain Hamming
               distance of <word> that changes the output
    accept  -- check whether a concrete word is accepted by a program

Arguments:
    craspX -- file name of a C-RASP program
    outputX -- the name of a C-RASP rule that is the output of the program
    measure -- one of "hamming" (no others)
    distance -- look for an example within this distance
    word -- a space separated sequence of chars, quoted. E.g. "a b c"
            (also accepts a single token word like "abba")
    num_cexs -- the number of counter examples to output, default 1
    property -- for translate, "check" (default) to include the end of stream
                check node, or "raw" for the main node only
    comparison -- for translate_compare, one of "equal" (default), "subset",
                "disjoint"
    out -- for translate/translate_compare, a file to write the Lustre to,
                default stdout

Usage:
    main.py translate <crasp> <output> [--property=<property>] [--out=<out>]
    main.py translate_compare <crasp1> <output1> <crasp2> <output2> [--comparison=<comparison>] [--out=<out>]
    main.py [--config=<config>] check <crasp> <output> [--num_cexs=<num_cexs>]
    main.py [--config=<config>] equal <crasp1> <output1> <crasp2> <output2> [--num_cexs=<num_cexs>]
    main.py [--config=<config>] subset <crasp1> <output1> <crasp2> <output2> [--num_cexs=<num_cexs>]
    main.py [--config=<config>] disjoint <crasp1> <output1> <crasp2> <output2> [--num_cexs=<num_cexs>]
    main.py [--config=<config>] robust <crasp> <output> <measure> <distance> <word> [--num_cexs=<num_cexs>]
    main.py [--config=<config>] accept <crasp> <output> <word>

"""

import tomllib
from dataclasses import dataclass, field
from docopt import docopt
from pathlib import Path
from typing import Dict, List

from rasptranslation.api import (
    Property,
    compare_to_lustre,
    crasp_to_lustre,
    parse_word,
)
from rasptranslation.checkers.kind2 import Kind2Config
from rasptranslation.checkers.runners import check_kind2
from rasptranslation.rasp.parsers import parse_crasp
from rasptranslation.translation.properties_translators import (
    Comparison,
    DistanceMeasure,
    LustreProperty,
    translate_check_file,
    translate_compare_files,
    translate_robust_file,
    translate_word_acceptance,   # <-- your new translator
)

DEFAULT_CONFIG = "config.toml"


@dataclass
class Config:
    kind2_config: Kind2Config = field(default_factory=Kind2Config)
    num_counter_examples: int = 1

    @staticmethod
    def load(args: Dict[str, str]) -> "Config":
        filename = args.get("--config")
        if filename is None and not Path(DEFAULT_CONFIG).is_file():
            return Config()
        else:
            with open(filename or DEFAULT_CONFIG, "rb") as f:
                toml = tomllib.load(f)
            return Config(
                kind2_config=Kind2Config.load(toml),
                num_counter_examples=int(
                    args.get("--num_cexs") or toml.get("num_cexs") or "1"
                ),
            )


def check_and_print(config: Config, prop: LustreProperty):
    has_cex = False
    for cex in check_kind2(
        config.kind2_config,
        prop,
        config.num_counter_examples,
    ):
        has_cex = True
        decoded = " ".join(prop.alphabet_map[i] for i in cex)
        print(f"Counter example: {decoded}")

    if not has_cex:
        print("No counter example")


def do_translate(
    crasp_file: str,
    output: str,
    property: Property,
    out_file: str | None,
):
    lustre = crasp_to_lustre(Path(crasp_file), output, property)
    if out_file is None:
        print(lustre)
    else:
        with open(out_file, "w") as f:
            f.write(lustre + "\n")


COMPARISON_NAMES = {
    "equal": Comparison.EQUAL,
    "subset": Comparison.SUBSET,
    "disjoint": Comparison.DISJOINT,
}


def do_translate_compare(
    crasp_file1: str,
    output1: str,
    crasp_file2: str,
    output2: str,
    comparison: Comparison,
    out_file: str | None,
):
    lustre = compare_to_lustre(
        Path(crasp_file1),
        output1,
        Path(crasp_file2),
        output2,
        comparison,
    )
    if out_file is None:
        print(lustre)
    else:
        with open(out_file, "w") as f:
            f.write(lustre + "\n")


def do_check(
    config: Config,
    crasp_file: str,
    output: str,
):
    prop = translate_check_file(crasp_file, output)
    check_and_print(config, prop)


def do_compare(
    config: Config,
    crasp_file1: str,
    output1: str,
    crasp_file2: str,
    output2: str,
    comparison: Comparison,
):
    prop = translate_compare_files(
        crasp_file1,
        output1,
        crasp_file2,
        output2,
        comparison,
    )
    check_and_print(config, prop)


def do_robust(
    config: Config,
    crasp_file: str,
    output: str,
    measure: DistanceMeasure,
    distance: int,
    word: List[str],
):
    prop = translate_robust_file(
        crasp_file,
        output,
        measure,
        distance,
        word,
    )
    check_and_print(config, prop)


def do_accept(
    config: Config,
    crasp_file: str,
    output: str,
    word: List[str],
):
    """
    Check whether `crasp_file` accepts the concrete `word`.

    IMPORTANT: This assumes translate_word_acceptance is implemented so that
    Kind2 returns a counterexample IFF the word is accepted (the usual pattern
    is to assert NOT(accepted) so acceptance produces a violation).
    """
    with open(crasp_file) as f:
        crasp_txt = f.read()
    crasp = parse_crasp(crasp_txt)

    prop = translate_word_acceptance(crasp, output, word)

    has_cex = False
    for _ in check_kind2(config.kind2_config, prop, num_counter_examples=1):
        has_cex = True
        break

    if has_cex:
        print("Accepted")
    else:
        print("Rejected")


def main():
    arguments = docopt(__doc__, version="Grid 0.1")

    if arguments["translate"]:
        do_translate(
            arguments["<crasp>"],
            arguments["<output>"],
            Property(arguments["--property"] or Property.CHECK),
            arguments["--out"],
        )
        return

    if arguments["translate_compare"]:
        comparison_name = arguments["--comparison"] or "equal"
        if comparison_name not in COMPARISON_NAMES:
            raise SystemExit(
                f"Unknown comparison '{comparison_name}';"
                + " expected equal, subset or disjoint."
            )
        do_translate_compare(
            arguments["<crasp1>"],
            arguments["<output1>"],
            arguments["<crasp2>"],
            arguments["<output2>"],
            COMPARISON_NAMES[comparison_name],
            arguments["--out"],
        )
        return

    config = Config.load(arguments)

    if arguments["check"]:
        do_check(
            config,
            arguments["<crasp>"],
            arguments["<output>"],
        )
    elif arguments["equal"]:
        do_compare(
            config,
            arguments["<crasp1>"],
            arguments["<output1>"],
            arguments["<crasp2>"],
            arguments["<output2>"],
            Comparison.EQUAL,
        )
    elif arguments["subset"]:
        do_compare(
            config,
            arguments["<crasp1>"],
            arguments["<output1>"],
            arguments["<crasp2>"],
            arguments["<output2>"],
            Comparison.SUBSET,
        )
    elif arguments["disjoint"]:
        do_compare(
            config,
            arguments["<crasp1>"],
            arguments["<output1>"],
            arguments["<crasp2>"],
            arguments["<output2>"],
            Comparison.DISJOINT,
        )
    elif arguments["robust"]:
        do_robust(
            config,
            arguments["<crasp>"],
            arguments["<output>"],
            DistanceMeasure(arguments["<measure>"]),
            int(arguments["<distance>"]),
            parse_word(arguments["<word>"]),
        )
    elif arguments["accept"]:
        do_accept(
            config,
            arguments["<crasp>"],
            arguments["<output>"],
            parse_word(arguments["<word>"]),
        )
    else:
        # Shouldn't happen because docopt enforces usage, but keep it safe.
        raise SystemExit("Unknown command; see usage in --help.")


if __name__ == "__main__":
    main()
