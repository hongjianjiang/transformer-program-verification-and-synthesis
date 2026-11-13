
from dataclasses import dataclass
from enum import Enum, StrEnum

from rasptranslation.lustre.ast_nodes import (
    ASTNode,
    Application,
    BinaryOp,
    BinaryOperator,
    BoolLiteral,
    Check,
    Contract,
    ContractVar,
    Equation,
    IntLiteral,
    LustreType,
    Node,
    Not,
    Parameter,
    Pre,
    Program,
    Require,
    Variable,
)
from rasptranslation.lustre.functions import generic_count_fun
from rasptranslation.lustre.transformers import (
    NodeRenamer,
    list_to_expression,
)
from rasptranslation.rasp.parsers import parse_crasp
from rasptranslation.rasp.ast_nodes import CRASP
from rasptranslation.translation.crasp_translator import (
    translate_crasp_alphabet,
    translate_crasp_to_lustre,
)

from typing import Dict, List

CHECK_NODE = "__CHECK__"
COMPARE_NODE = "__COMPARE__"
MAIN_FUN = "MAIN"
IN_VAR = "__I__"
WORD_VAR = "__W__"
OUT_VAR1 = "__O1__"
OUT_VAR2 = "__O2__"
CHECK_VAR = "__P__"
IN_DISTANCE_VAR = "__D__"
DISTANCE_FUN = "__DISTANCE_CHECK"
DISTANCE_COUNT_VAR = "__DC__"
COUNT_FUN_NAME = "__PROP_COUNT_TRUE__"
END_OF_STREAM = "__eos__"
ETERNITY = "__eternity__"
SAME_EOS_VAR = "__SAME_EOS"
GOOD_CHAR_VAR = "__GOOD_CHAR"
GOOD_CHARS_VAR = "__GOOD_CHARS"
GOOD_EOS = "__GOOD_EOS"

@dataclass
class LustreProperty:
    """Encoded CRASP to Lustre program
    :param lustre: the Lustre code
    :param alphabet_map: map from CRASP characters to ints used in Lustre
    :param check_node: the node containing the check
    :param input_var: the input var name of the check node
    :param eos: the end of stream character as Lustre int
    :param eternity: the eternity character as Lustre int"""
    lustre : Program
    alphabet_map : Dict[int, str]
    check_node : str
    input_var : str
    eos : int
    eternity : int

class Comparison(Enum):
    EQUAL = 1
    SUBSET = 2
    DISJOINT = 3

class DistanceMeasure(StrEnum):
    HAMMING = "hamming"

def reverse_map(m):
    return { v : k for k, v in m.items() }

def translate_check_file(
    crasp_file : str,
    output : str,
) -> LustreProperty:
    with open(crasp_file) as f:
        txt = f.read()
    return translate_check_string(txt, output)

def translate_check_string(
    crasp_text : str,
    output : str,
) -> LustreProperty:
    crasp = parse_crasp(crasp_text)
    return translate_check(crasp, output)

def translate_check(
    crasp : CRASP,
    output : str,
) -> LustreProperty:
    alphabet = crasp.alphabet | set([END_OF_STREAM, ETERNITY])
    alphabet_map = translate_crasp_alphabet(alphabet)

    lustre = translate_crasp_to_lustre(
        crasp,
        MAIN_FUN,
        IN_VAR,
        output,
        alphabet_map,
    )

    check_node = Node(
        name = CHECK_NODE,
        inputs = [Parameter(IN_VAR, LustreType.INT)],
        outputs = [],
        contracts = get_in_var_contracts(IN_VAR, alphabet_map),
        variables = [
            Parameter(OUT_VAR1, LustreType.BOOL),
            Parameter(CHECK_VAR, LustreType.BOOL),
        ],
        equations = [
            Equation(
                OUT_VAR1,
                Application(MAIN_FUN, [Variable(IN_VAR)]),
            ),
            Equation(
                CHECK_VAR,
                check_at_eos(
                    IN_VAR,
                    alphabet_map[END_OF_STREAM],
                    Variable(OUT_VAR1),
                ),
            ),

        ],
        checks = [Check(CHECK_VAR)],
    )

    lustre = Program(nodes = lustre.nodes + [check_node])

    return LustreProperty(
        lustre = lustre,
        alphabet_map = reverse_map(alphabet_map),
        check_node = check_node.name,
        input_var = IN_VAR,
        eos = alphabet_map[END_OF_STREAM],
        eternity = alphabet_map[ETERNITY],
    )

def translate_robust_file(
    crasp_file : str,
    output : str,
    measure : DistanceMeasure,
    distance : int,
    word : List[str],
) -> LustreProperty:
    with open(crasp_file) as f:
        txt = f.read()
    return translate_robust_string(txt, output, measure, distance, word)

def translate_robust_string(
    crasp_text : str,
    output : str,
    measure : DistanceMeasure,
    distance : int,
    word : List[str],
) -> LustreProperty:
    crasp = parse_crasp(crasp_text)
    return translate_robust(crasp, output, measure, distance, word)

def translate_word_acceptance(
    crasp: CRASP,
    output: str,
    word: List[str],
) -> LustreProperty:
    """
    Build a Lustre program that fixes the input stream to:
        word, eos (#), eternity (Ω), Ω, Ω, ...
    and checks whether the CRASP program accepts that word.

    Result:
      - If Kind2 finds a counterexample, the word IS accepted.
      - If Kind2 proves the check, the word is NOT accepted.
    """
    alphabet = crasp.alphabet | set([END_OF_STREAM, ETERNITY])
    alphabet_map = translate_crasp_alphabet(alphabet)

    # Translate CRASP program to Lustre main
    lustre = translate_crasp_to_lustre(
        crasp,
        MAIN_FUN,
        IN_VAR,
        output,
        alphabet_map,
    )

    eos_int = alphabet_map[END_OF_STREAM]
    et_int  = alphabet_map[ETERNITY]

    # Build a fixed input stream __I__ = word ++ [eos] ++ [eternity, eternity, ...]
    # list_to_expression already builds: a0 -> a1 -> ... -> eos -> eternity -> eternity -> ...
    word_stream = list_to_expression(
        [alphabet_map[a] for a in word],
        eos_int,
        et_int,
    )

    # We'll compute Out on that fixed stream
    # and then CHECK acceptance using paper-aligned check_at_eos (requires pre(Out) when I=eos).
    # To make Kind2 "answer" acceptance, we assert the NEGATION and ask it to refute.
    #
    # accepted(word)  <=> (I=eos and pre(Out))
    # so we set:
    #    CHECK = not( check_at_eos(...) )   ??? careful:
    # check_at_eos returns a *property* "true -> (I!=eos OR pre(Out))"
    # This property is TRUE iff (I=eos -> pre(Out)) holds.
    #
    # We want to know if the word is accepted, i.e. pre(Out) IS TRUE at eos.
    # So we can directly check:
    #    check NOT( (I=eos) and pre(Out) )
    # and Kind2 counterexample => accepted.
    #
    # We'll implement this directly.

    in_var = Variable(IN_VAR)
    out_var = Variable(OUT_VAR1)

    accept_at_eos = BinaryOp(
        BinaryOperator.AND,
        BinaryOp(BinaryOperator.EQ, in_var, IntLiteral(eos_int)),
        Pre(out_var),
    )

    # We want Kind2 to refute "NOT accepted"
    # If accepted(word) is true at eos, then NOT(accepted) becomes false -> counterexample.
    check_formula = BinaryOp(
        BinaryOperator.ARROW,
        BoolLiteral(True),
        Not(accept_at_eos),
    )

    check_node = Node(
        name=CHECK_NODE,
        inputs=[],
        outputs=[],
        # no need for get_in_var_contracts because input is fixed and well-formed
        contracts=[],
        variables=[
            Parameter(IN_VAR, LustreType.INT),
            Parameter(OUT_VAR1, LustreType.BOOL),
            Parameter(CHECK_VAR, LustreType.BOOL),
        ],
        equations=[
            Equation(IN_VAR, word_stream),
            Equation(OUT_VAR1, Application(MAIN_FUN, [Variable(IN_VAR)])),
            Equation(CHECK_VAR, check_formula),
        ],
        checks=[Check(CHECK_VAR)],
    )

    lustre = Program(nodes=lustre.nodes + [check_node])

    return LustreProperty(
        lustre=lustre,
        alphabet_map=reverse_map(alphabet_map),
        check_node=CHECK_NODE,
        input_var=IN_VAR,
        eos=eos_int,
        eternity=et_int,
    )

    
def translate_robust(
    crasp : CRASP,
    output : str,
    measure : DistanceMeasure,
    distance : int,
    word : List[str],
) -> LustreProperty:
    alphabet = crasp.alphabet | set([END_OF_STREAM, ETERNITY])
    alphabet_map = translate_crasp_alphabet(alphabet)

    lustre = translate_crasp_to_lustre(
        crasp,
        MAIN_FUN,
        IN_VAR,
        output,
        alphabet_map,
    )

    in_var = Variable(IN_VAR)
    word_var = Variable(WORD_VAR)
    eos = IntLiteral(alphabet_map[END_OF_STREAM])
    eternity = IntLiteral(alphabet_map[ETERNITY])

    in_eos = BinaryOp(BinaryOperator.EQ, in_var, eos)
    in_not_eos = BinaryOp(BinaryOperator.NEQ, in_var, eos)
    word_eos = BinaryOp(BinaryOperator.EQ, word_var, eos)
    in_eternity = BinaryOp(BinaryOperator.EQ, in_var, eternity)
    word_eternity = BinaryOp(BinaryOperator.EQ, word_var, eternity)

    equal_eos = BinaryOp(
        BinaryOperator.AND,
        BinaryOp(BinaryOperator.EQ, in_eos, word_eos),
        BinaryOp(BinaryOperator.EQ, in_eternity, word_eternity),
    )

    pre_equal_outputs = BinaryOp(
        BinaryOperator.EQ,
        Pre(Variable(OUT_VAR1)),
        Pre(Variable(OUT_VAR2)),
    )

    invalid_query = BinaryOp(
        BinaryOperator.OR,
        Not(Variable(IN_DISTANCE_VAR)),
        Not(Variable(SAME_EOS_VAR)),
    )
    # check only at eos for valid queries
    dont_check = BinaryOp(
        BinaryOperator.OR,
        invalid_query,
        in_not_eos,
    )

    distance_nodes = get_distance_nodes(
        DISTANCE_FUN,
        measure,
        distance,
    )

    word_stream = list_to_expression(
        [alphabet_map[a] for a in word],
        alphabet_map[END_OF_STREAM],
        alphabet_map[ETERNITY],
    )

    check_node = Node(
        name = CHECK_NODE,
        inputs = [Parameter(IN_VAR, LustreType.INT)],
        outputs = [],
        contracts = get_in_var_contracts(IN_VAR, alphabet_map),
        variables = [
            Parameter(WORD_VAR, LustreType.INT),
            Parameter(IN_DISTANCE_VAR, LustreType.BOOL),
            Parameter(SAME_EOS_VAR, LustreType.BOOL),
            Parameter(OUT_VAR1, LustreType.BOOL),
            Parameter(OUT_VAR2, LustreType.BOOL),
            Parameter(CHECK_VAR, LustreType.BOOL),
        ],
        equations = [
            Equation(WORD_VAR, word_stream),
            Equation(
                IN_DISTANCE_VAR,
                Application(
                    DISTANCE_FUN,
                    [Variable(IN_VAR), Variable(WORD_VAR)],
                ),
            ),
            Equation(
                SAME_EOS_VAR,
                BinaryOp(
                    BinaryOperator.ARROW,
                    equal_eos,
                    BinaryOp(
                        BinaryOperator.AND,
                        Pre(Variable(SAME_EOS_VAR)),
                        equal_eos,
                    ),
                ),
            ),
            Equation(
                OUT_VAR1,
                Application(MAIN_FUN, [Variable(WORD_VAR)]),
            ),
            Equation(
                OUT_VAR2,
                Application(MAIN_FUN, [Variable(IN_VAR)]),
            ),
            Equation(
                CHECK_VAR,
                BinaryOp(
                    BinaryOperator.ARROW,
                    BoolLiteral(True),
                    BinaryOp(
                        BinaryOperator.OR,
                        dont_check,
                        pre_equal_outputs,
                    ),
                ),
            ),
        ],
        checks = [Check(CHECK_VAR)],
    )

    lustre = Program(
        nodes = lustre.nodes + [check_node] + distance_nodes
    )

    return LustreProperty(
        lustre = lustre,
        alphabet_map = reverse_map(alphabet_map),
        check_node = CHECK_NODE,
        input_var = IN_VAR,
        eos = alphabet_map[END_OF_STREAM],
        eternity = alphabet_map[ETERNITY],
    )

def translate_compare_files(
    crasp_file1 : str,
    output1 : str,
    crasp_file2 : str,
    output2 : str,
    comparison : Comparison,
) -> LustreProperty:
    with open(crasp_file1) as f:
        txt1 = f.read()
    with open(crasp_file2) as f:
        txt2 = f.read()
    return translate_compare_strings(
        txt1,
        output1,
        txt2,
        output2,
        comparison,
    )

def translate_compare_strings(
    crasp_text1 : str,
    output1 : str,
    crasp_text2 : str,
    output2 : str,
    comparison : Comparison,
) -> LustreProperty:
    crasp1 = parse_crasp(crasp_text1)
    crasp2 = parse_crasp(crasp_text2)
    return translate_compare(crasp1, output1, crasp2, output2, comparison)

def translate_compare(
    crasp1 : CRASP,
    output1 : str,
    crasp2 : CRASP,
    output2 : str,
    comparison : Comparison,
) -> LustreProperty:
    if crasp1.alphabet != crasp2.alphabet:
        extra1 = ",".join(crasp1.alphabet - crasp2.alphabet)
        if len(extra1) == 0:
            extra1 = "no"
        extra2 = crasp2.alphabet - crasp1.alphabet
        if len(extra2) == 0:
            extra1 = "no"
        raise ValueError(
            f"Mismatched alphabets: first program has {extra1} extra"
            + f" and second has {extra2} extra."
        )

    def rename1(name : str):
        return "left_" + name

    def rename2(name : str):
        return "right_" + name

    alphabet = crasp1.alphabet | set([END_OF_STREAM, ETERNITY])
    alphabet_map = translate_crasp_alphabet(alphabet)

    lustre1 = NodeRenamer(rename1).transform_program(
        translate_crasp_to_lustre(
            crasp1,
            MAIN_FUN,
            IN_VAR,
            output1,
            alphabet_map,
        ),
    )
    lustre2 = NodeRenamer(rename2).transform_program(
        translate_crasp_to_lustre(
            crasp2,
            MAIN_FUN,
            IN_VAR,
            output2,
            alphabet_map,
        ),
    )

    main1 = rename1(MAIN_FUN)
    main2 = rename2(MAIN_FUN)
    in_var = Variable(IN_VAR)

    comp_node = get_compare_node(
        comparison,
        in_var,
        main1,
        main2,
        alphabet_map,
    )

    lustre = Program(
        nodes = lustre1.nodes + lustre2.nodes + [comp_node],
    )

    return LustreProperty(
        lustre = lustre,
        alphabet_map = reverse_map(alphabet_map),
        check_node = comp_node.name,
        input_var = IN_VAR,
        eos = alphabet_map[END_OF_STREAM],
        eternity = alphabet_map[ETERNITY],
    )

def get_compare_node(
    comparison : Comparison,
    in_var : Variable,
    main1 : str,
    main2 : str,
    alphabet_map : Dict[str, int],
) -> Node:
    """Make a node that compares the output of two main funs
    :param comparison: the type of comparison
    :param in_var: the Variable that is the input of both mains
    :param main1: the name of prog1's main fun
    :param main2: the name of prog2's main fun"""
    match comparison:
        case Comparison.EQUAL:
            compare_expr = BinaryOp(
                BinaryOperator.EQ,
                Variable(OUT_VAR1),
                Variable(OUT_VAR2),
            )
        case Comparison.SUBSET:
            compare_expr = BinaryOp(
                BinaryOperator.OR,
                Not(Variable(OUT_VAR1)),
                Variable(OUT_VAR2),
            )
        case Comparison.DISJOINT:
            compare_expr = BinaryOp(
                BinaryOperator.OR,
                Not(Variable(OUT_VAR1)),
                Not(Variable(OUT_VAR2)),
            )

    return Node(
        name = COMPARE_NODE,
        inputs = [Parameter(IN_VAR, LustreType.INT)],
        outputs = [],
        variables = [
            Parameter(OUT_VAR1, LustreType.BOOL),
            Parameter(OUT_VAR2, LustreType.BOOL),
            Parameter(CHECK_VAR, LustreType.BOOL),
        ],
        contracts = get_in_var_contracts(IN_VAR, alphabet_map),
        equations = [
            Equation(
                OUT_VAR1,
                Application(main1, [in_var]),
            ),
            Equation(
                OUT_VAR2,
                Application(main2, [in_var]),
            ),
            Equation(
                CHECK_VAR,
                check_at_eos(
                    IN_VAR,
                    alphabet_map[END_OF_STREAM],
                    compare_expr,
                ),
            ),
        ],
        checks = [Check(CHECK_VAR)],
    )

def get_in_var_contracts(
    in_var_name : str,
    alphabet_map : Dict[str, int],
) -> List[Contract]:
    """Ensure input words are of expected form
    That is:
    1. only use letters from alphabet
    2. have an eos followed by eternity*"""

    in_var = Variable(in_var_name)

    # 1. right alphabet
    alphabet_check = BoolLiteral(False)
    for alt in (
        BinaryOp(
            BinaryOperator.EQ,
            in_var,
            IntLiteral(v),
        )
        for v in alphabet_map.values()
    ):
        alphabet_check = BinaryOp(BinaryOperator.OR, alphabet_check, alt)

    # 2. eos eternity*
    eos = IntLiteral(alphabet_map[END_OF_STREAM])
    eternity = IntLiteral(alphabet_map[ETERNITY])
    is_eos = BinaryOp(BinaryOperator.EQ, in_var, eos)
    is_eternity = BinaryOp(BinaryOperator.EQ, in_var, eternity)
    # don't start with eternity, then see eternity after eos/eternity seen
    eos_check = BinaryOp(
        BinaryOperator.ARROW,
        Not(is_eternity),
        BinaryOp(
            BinaryOperator.OR,
            Not(Pre(BinaryOp(BinaryOperator.OR, is_eos, is_eternity))),
            is_eternity,
        ),
    )

    return [
        ContractVar(GOOD_CHAR_VAR, LustreType.BOOL, alphabet_check),
        ContractVar(
            GOOD_CHARS_VAR,
            LustreType.BOOL,
            BinaryOp(
                BinaryOperator.ARROW,
                Variable(GOOD_CHAR_VAR),
                BinaryOp(
                    BinaryOperator.AND,
                    Variable(GOOD_CHAR_VAR),
                    Pre(Variable(GOOD_CHARS_VAR)),
                ),
            ),
        ),
        ContractVar(GOOD_EOS, LustreType.BOOL, eos_check),
        Require(
            BinaryOp(
                BinaryOperator.AND,
                Variable(GOOD_CHARS_VAR),
                Variable(GOOD_EOS),
            ),
        ),
    ]

def get_distance_nodes(
    node_name : str,
    measure : DistanceMeasure,
    distance : int,
) -> List[Node]:
    match measure:
        case DistanceMeasure.HAMMING:
            return get_hamming_distance_nodes(
                node_name,
                distance,
            )

def get_hamming_distance_nodes(
    node_name : str,
    distance : int,
) -> List[Node]:
    count_true_fun = generic_count_fun(COUNT_FUN_NAME)
    check_hamming = Node(
        name = node_name,
        inputs = [
            Parameter(WORD_VAR, LustreType.INT),
            Parameter(IN_VAR, LustreType.INT),
        ],
        outputs = [Parameter(OUT_VAR1, LustreType.BOOL)],
        variables = [Parameter(DISTANCE_COUNT_VAR, LustreType.INT)],
        equations = [
            Equation(
                DISTANCE_COUNT_VAR,
                Application(
                    COUNT_FUN_NAME,
                    [
                        BinaryOp(
                            BinaryOperator.NEQ,
                            Variable(WORD_VAR),
                            Variable(IN_VAR),
                        ),
                    ],
                ),
            ),
            Equation(
                OUT_VAR1,
                BinaryOp(
                    BinaryOperator.LE,
                    Variable(DISTANCE_COUNT_VAR),
                    IntLiteral(distance),
                ),
            ),
        ],
        checks = [],
    )

    return [count_true_fun, check_hamming]

def check_at_eos(in_var : str, eos : int, expr : ASTNode) -> ASTNode:
    """Check expr holds at step before end of stream"""
    return BinaryOp(
        BinaryOperator.ARROW,
        BoolLiteral(True),
        BinaryOp(
            BinaryOperator.OR,
            Not(
                BinaryOp(
                    BinaryOperator.EQ,
                    Variable(in_var),
                    IntLiteral(eos),
                ),
            ),
            Pre(expr),
        ),
    )


def check_at_eos_current(in_var: str, eos: int, expr: ASTNode) -> ASTNode:
    """Check expr holds at the instant when in_var = eos."""
    return BinaryOp(
        BinaryOperator.ARROW,
        BoolLiteral(True),
        BinaryOp(
            BinaryOperator.OR,
            Not(
                BinaryOp(
                    BinaryOperator.EQ,
                    Variable(in_var),
                    IntLiteral(eos),
                ),
            ),
            expr,   # <-- no Pre(...)
        ),
    )
