
from copy import deepcopy

from typing import Generator, List

from rasptranslation.checkers.kind2 import Kind2, Kind2Config
from rasptranslation.lustre.ast_nodes import (
    BinaryOp,
    BinaryOperator,
    ContractVar,
    IntLiteral,
    LustreType,
    Not,
    Pre,
    Program,
    Require,
    Variable,
)
from rasptranslation.lustre.transformers import list_to_expression
from rasptranslation.translation.properties_translators import LustreProperty

COUNTER_EXAMPLE_VAR_FMT = "__CEX_{number}"
IS_COUNTER_EXAMPLE_VAR_FMT = "__IS_CEX_{number}"

def check_kind2(
    config : Kind2Config,
    prop : LustreProperty,
    num_counter_examples : int = 1,
) -> Generator[List[int], None, None]:
    """Generates counter examples to prop
    Stops when num_counter_examples reached or no more counter examples. In
    particular, if it does not generate any counter examples, the property
    holds universally."""
    kind2 = Kind2(config)
    lustre = prop.lustre

    for iter_num in range(num_counter_examples):
        result = kind2.run(lustre, prop.check_node, prop.input_var)
        if result.counter_example is None:
            break
        else:
            yield result.counter_example
            if iter_num < num_counter_examples - 1:
                if iter_num == 0:
                    lustre = deepcopy(lustre)
                block_counter_example(
                    lustre,
                    prop.check_node,
                    prop.input_var,
                    result.counter_example,
                    iter_num,
                    prop.eos,
                    prop.eternity,
                )

def block_counter_example(
    lustre : Program,
    check_node : str,
    input_var : str,
    counter_example : List[int],
    counter_example_number : int,
    eos : int,
    eternity : int,
):
    """Block the given counter example by modifying lustre
    Adds a contract to the check_node over input_var that it is not the counter
    example. TODO: must require eos/eternity ended else just eliminating
    prefixes."""
    try:
        node = next(n for n in lustre.nodes if n.name == check_node)

        cex_var = COUNTER_EXAMPLE_VAR_FMT.format(
            number = counter_example_number,
        )
        cex_var_eq = list_to_expression(counter_example, eos, eternity)
        node.contracts.append(ContractVar(cex_var, LustreType.INT, cex_var_eq))

        is_cex_var = IS_COUNTER_EXAMPLE_VAR_FMT.format(
            number = counter_example_number,
        )
        is_cex_now = BinaryOp(
            BinaryOperator.EQ,
            Variable(input_var),
            Variable(cex_var),
        )
        is_cex_eq = BinaryOp(
            BinaryOperator.ARROW,
            is_cex_now,
            BinaryOp(
                BinaryOperator.AND,
                is_cex_now,
                Pre(Variable(is_cex_var)),
            ),
        )
        node.contracts.append(
            ContractVar(is_cex_var, LustreType.BOOL, is_cex_eq),
        )

        is_eos = BinaryOp(
            BinaryOperator.EQ,
            Variable(input_var),
            IntLiteral(eos),
        )

        # check is not eos yet, or if it is, we don't have an existing cex
        node.contracts.append(
            Require(
                BinaryOp(
                    BinaryOperator.OR,
                    Not(is_eos),
                    Not(Variable(is_cex_var)),
                ),
            ),
        )
    except StopIteration:
        raise ValueError(f"Cannot find {check_node} in lustre program.")

