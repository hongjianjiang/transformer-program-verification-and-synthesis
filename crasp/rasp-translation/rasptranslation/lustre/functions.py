"""A collection of useful predefined lustre functions"""

from rasptranslation.lustre.ast_nodes import (
    BinaryOp,
    BinaryOperator,
    Equation,
    IfThenElse,
    IntLiteral,
    LustreType,
    Node,
    Parameter,
    Pre,
    Variable,
)

def generic_count_fun(
    fun_name : str,
) -> Node:
    IN = "B"
    ONE_IF = "ONE_IF"
    SUM = "S"
    return Node(
        name = fun_name,
        inputs = [Parameter(IN, LustreType.BOOL)],
        outputs = [Parameter(SUM, LustreType.INT)],
        variables = [Parameter(ONE_IF, LustreType.INT)],
        equations = [
            Equation(
                ONE_IF,
                IfThenElse(Variable(IN), IntLiteral(1), IntLiteral(0)),
            ),
            Equation(
                SUM,
                BinaryOp(
                    BinaryOperator.ARROW,
                    Variable(ONE_IF),
                    BinaryOp(
                        BinaryOperator.ADD,
                        Variable(ONE_IF),
                        Pre(Variable("S")),
                    ),
                ),
            )
        ],
        checks = [],
    )

