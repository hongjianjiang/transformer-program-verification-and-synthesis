"""
Tests for Lustre AST and Printer
"""

import pytest

from typing import List

from rasptranslation.lustre.ast_nodes import (
    BinaryOp,
    BinaryOperator,
    BoolLiteral,
    Check,
    Contract,
    ContractVar,
    Equation,
    IfThenElse,
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
from rasptranslation.lustre.printer import print_lustre

class TestASTConstruction:
    """Test AST node construction"""

    def test_variable(self):
        """Test variable node"""
        var = Variable("X")
        assert var.name == "X"
        assert str(var) == "X"

    def test_int_literal(self):
        """Test integer literal"""
        lit = IntLiteral(42)
        assert lit.value == 42
        assert str(lit) == "42"

    def test_bool_literal(self):
        """Test boolean literal"""
        true_lit = BoolLiteral(True)
        false_lit = BoolLiteral(False)
        assert str(true_lit) == "true"
        assert str(false_lit) == "false"

    def test_pre(self):
        """Test pre operator"""
        var = Variable("X")
        pre_x = Pre(var)
        assert str(pre_x) == "pre(X)"

    def test_binary_op(self):
        """Test binary operations"""
        x = Variable("X")
        y = Variable("Y")
        add = BinaryOp(BinaryOperator.ADD, x, y)
        assert str(add) == "(X + Y)"

    def test_not(self):
        """Test NOT operator"""
        x = Variable("X")
        not_x = Not(x)
        assert str(not_x) == "not X"

    def test_if_then_else(self):
        """Test if-then-else"""
        cond = Variable("C")
        then_e = IntLiteral(1)
        else_e = IntLiteral(0)
        ite = IfThenElse(cond, then_e, else_e)
        assert str(ite) == "if C then 1 else 0"


class TestParameter:
    """Test parameter declarations"""

    def test_bool_parameter(self):
        """Test boolean parameter"""
        param = Parameter("X", LustreType.BOOL)
        assert param.name == "X"
        assert param.type == LustreType.BOOL
        assert str(param) == "X: bool"

    def test_int_parameter(self):
        """Test integer parameter"""
        param = Parameter("Y", LustreType.INT)
        assert param.name == "Y"
        assert param.type == LustreType.INT
        assert str(param) == "Y: int"


class TestEquation:
    """Test equation construction"""

    def test_simple_equation(self):
        """Test simple equation"""
        eq = Equation("X", Variable("Y"))
        assert eq.lhs == "X"
        assert isinstance(eq.rhs, Variable)
        assert str(eq) == "X = Y;"

    def test_arrow_equation(self):
        """Test equation with arrows"""
        eq = Equation("X", BinaryOp(BinaryOperator.ARROW, IntLiteral(0), Variable("Y")))
        assert isinstance(eq.rhs, BinaryOp)
        assert str(eq) == "X = (0 -> Y);"

    def test_multiple_arrow_equation(self):
        """Test equation with multiple arrows"""
        eq = Equation(
            "X",
            BinaryOp(
                BinaryOperator.ARROW,
                IntLiteral(0),
                BinaryOp(BinaryOperator.ARROW, IntLiteral(1), Variable("Y"))
            )
        )
        assert str(eq) == "X = (0 -> (1 -> Y));"


class TestNode:
    """Test node declarations"""

    def test_simple_node(self):
        """Test simple node with one input and output"""
        inputs = [Parameter("X", LustreType.BOOL)]
        outputs = [Parameter("Y", LustreType.BOOL)]
        equations = [Equation("Y", Variable("X"))]

        node = Node(
            name = "IDENTITY",
            inputs = inputs,
            outputs = outputs,
            equations = equations,
        )
        assert node.name == "IDENTITY"
        assert len(node.inputs) == 1
        assert len(node.outputs) == 1
        assert len(node.equations) == 1
        assert len(node.variables) == 0

    def test_node_with_variables(self):
        """Test node with local variable declarations"""
        inputs = [Parameter("X", LustreType.BOOL)]
        outputs = [Parameter("Y", LustreType.BOOL)]
        variables = [
            Parameter("S1", LustreType.BOOL),
            Parameter("I1", LustreType.INT),
        ]
        equations = [
            Equation("S1", Variable("X")),
            Equation("Y", Variable("S1"))
        ]

        node = Node(
            name = "WITH_VARS",
            inputs = inputs,
            outputs = outputs,
            variables = variables,
            equations = equations,
        )
        assert node.name == "WITH_VARS"
        assert len(node.variables) == 2
        assert node.variables[0].name == "S1"
        assert node.variables[0].type == LustreType.BOOL
        assert node.variables[1].name == "I1"
        assert node.variables[1].type == LustreType.INT

    def test_counter_node(self):
        """Test counter node"""
        inputs = [Parameter("RESET", LustreType.BOOL)]
        outputs = [Parameter("COUNT", LustreType.INT)]

        # COUNT = 0 -> if RESET then 0 else pre(COUNT) + 1;
        init = IntLiteral(0)
        reset_cond = Variable("RESET")
        zero = IntLiteral(0)
        increment = BinaryOp(
            BinaryOperator.ADD,
            Pre(Variable("COUNT")),
            IntLiteral(1)
        )
        step = IfThenElse(reset_cond, zero, increment)

        equations = [Equation("COUNT", BinaryOp(BinaryOperator.ARROW, init, step))]

        node = Node(
            name = "COUNTER",
            inputs = inputs,
            outputs = outputs,
            equations = equations,
        )
        assert node.name == "COUNTER"


class TestProgram:
    """Test program construction"""

    def test_empty_program(self):
        """Test empty program"""
        prog = Program([])
        assert len(prog.nodes) == 0

    def test_single_node_program(self):
        """Test program with one node"""
        inputs = [Parameter("X", LustreType.BOOL)]
        outputs = [Parameter("Y", LustreType.BOOL)]
        equations = [Equation("Y", Variable("X"))]
        node = Node(
            name = "IDENTITY",
            inputs = inputs,
            outputs = outputs,
            equations = equations,
        )

        prog = Program([node])
        assert len(prog.nodes) == 1


class TestPrinter:
    """Test Lustre printer"""

    def test_print_simple_node(self):
        """Test printing a simple node"""
        inputs = [Parameter("X", LustreType.BOOL)]
        outputs = [Parameter("Y", LustreType.BOOL)]
        equations = [Equation("Y", Variable("X"))]
        node = Node(
            name = "IDENTITY",
            inputs = inputs,
            outputs = outputs,
            equations = equations,
        )
        prog = Program([node])

        output = print_lustre(prog)
        assert "node IDENTITY" in output
        assert "(X: bool)" in output
        assert "returns (Y: bool);" in output
        assert "let" in output
        assert "Y = X;" in output
        assert "tel" in output

    def test_print_counter(self):
        """Test printing a counter node"""
        inputs = [Parameter("RESET", LustreType.BOOL)]
        outputs = [Parameter("COUNT", LustreType.INT)]

        init = IntLiteral(0)
        reset_cond = Variable("RESET")
        zero = IntLiteral(0)
        increment = BinaryOp(
            BinaryOperator.ADD,
            Pre(Variable("COUNT")),
            IntLiteral(1)
        )
        step = IfThenElse(reset_cond, zero, increment)

        equations = [Equation("COUNT", BinaryOp(BinaryOperator.ARROW, init, step))]
        node = Node(
            name = "COUNTER",
            inputs = inputs,
            outputs = outputs,
            equations = equations,
        )
        prog = Program([node])

        output = print_lustre(prog)
        assert "node COUNTER" in output
        assert "COUNT: int" in output
        assert "0 -> " in output
        assert "if RESET then 0 else" in output
        assert "pre(COUNT)" in output

    def test_print_multiple_inputs_outputs(self):
        """Test printing node with multiple inputs and outputs"""
        inputs = [
            Parameter("A", LustreType.BOOL),
            Parameter("B", LustreType.BOOL)
        ]
        outputs = [
            Parameter("X", LustreType.BOOL),
            Parameter("Y", LustreType.BOOL)
        ]
        equations = [
            Equation("X", BinaryOp(
                BinaryOperator.AND,
                Variable("A"),
                Variable("B")
            )),
            Equation("Y", BinaryOp(
                BinaryOperator.OR,
                Variable("A"),
                Variable("B")
            ))
        ]
        node = Node(
            name = "ANDOR",
            inputs = inputs,
            outputs = outputs,
            equations = equations,
        )
        prog = Program([node])

        output = print_lustre(prog)
        assert "A: bool; B: bool" in output
        assert "X: bool; Y: bool" in output
        assert "X = (A and B);" in output
        assert "Y = (A or B);" in output

    def test_print_all_operators(self):
        """Test printing all operators"""
        inputs = [
            Parameter("A", LustreType.BOOL),
            Parameter("B", LustreType.BOOL)
        ]
        outputs = [Parameter("R", LustreType.BOOL)]

        # Test various operators
        expr = BinaryOp(
            BinaryOperator.XOR,
            Variable("A"),
            Variable("B")
        )
        equations = [Equation("R", expr)]
        node = Node(
            name = "TEST",
            inputs = inputs,
            outputs = outputs,
            equations = equations,
        )
        prog = Program([node])

        output = print_lustre(prog)
        assert "xor" in output

    def test_print_comparisons(self):
        """Test printing comparison operators"""
        inputs = [
            Parameter("X", LustreType.INT),
            Parameter("Y", LustreType.INT)
        ]
        outputs = [Parameter("R", LustreType.BOOL)]

        expr = BinaryOp(BinaryOperator.LT, Variable("X"), Variable("Y"))
        equations = [Equation("R", expr)]
        node = Node(
            name = "LESS",
            inputs = inputs,
            outputs = outputs,
            equations = equations,
        )
        prog = Program([node])

        output = print_lustre(prog)
        assert "(X < Y)" in output

    def test_print_arithmetic(self):
        """Test printing arithmetic operators"""
        inputs = [
            Parameter("A", LustreType.INT),
            Parameter("B", LustreType.INT)
        ]
        outputs = [Parameter("R", LustreType.INT)]

        # (A + B) * 2
        add = BinaryOp(BinaryOperator.ADD, Variable("A"), Variable("B"))
        mul = BinaryOp(BinaryOperator.MUL, add, IntLiteral(2))
        equations = [Equation("R", mul)]
        node = Node(
            name = "CALC",
            inputs = inputs,
            outputs = outputs,
            equations = equations,
        )
        prog = Program([node])

        output = print_lustre(prog)
        assert "((A + B) * 2)" in output

    def test_print_node_with_variables(self):
        """Test printing node with variable declarations"""
        inputs = [Parameter("X", LustreType.BOOL)]
        outputs = [Parameter("Y", LustreType.BOOL)]
        variables = [Parameter("S1", LustreType.BOOL), Parameter("I1", LustreType.INT)]
        equations = [
            Equation("S1", Variable("X")),
            Equation("Y", Variable("S1"))
        ]
        node = Node(
            name = "WITH_VARS",
            inputs = inputs,
            outputs = outputs,
            variables = variables,
            equations = equations,
        )
        prog = Program([node])

        output = print_lustre(prog)
        assert "node WITH_VARS" in output
        assert "S1: bool;" in output
        assert "I1: int;" in output
        assert "let" in output
        assert "S1 = X;" in output
        assert "Y = S1;" in output
        assert "tel" in output

    def test_print_node_with_checks(self):
        """Test printing node with variable declarations"""
        inputs = [Parameter("X", LustreType.BOOL)]
        outputs = [Parameter("Y", LustreType.BOOL)]
        variables = []
        equations = [Equation("Y", Variable("X"))]
        checks = [Check("Y")]
        node = Node(
            name = "WITH_CHECKS",
            inputs = inputs,
            outputs = outputs,
            variables = variables,
            equations = equations,
            checks = checks,
        )
        prog = Program([node])

        output = print_lustre(prog)
        assert "node WITH_CHECKS" in output
        assert "check Y;" in output
        assert "tel" in output

    def test_print_node_with_contracts(self):
        """Test printing node with variable declarations"""
        inputs = [Parameter("X", LustreType.BOOL)]
        outputs = [Parameter("Y", LustreType.BOOL)]
        variables = []
        contracts : List[Contract] = [
            ContractVar("Y", LustreType.INT, IntLiteral(0)),
            Require(
                BinaryOp(
                    BinaryOperator.EQ,
                    Variable("Y"),
                    Variable("X"),
                ),
            ),
        ]
        checks = [Check("Y")]
        node = Node(
            name = "WITH_CHECKS",
            inputs = inputs,
            outputs = outputs,
            variables = variables,
            contracts = contracts,
            checks = checks,
        )
        prog = Program([node])

        output = print_lustre(prog)
        assert "node WITH_CHECKS" in output
        assert "contract" in output
        assert "assume (Y = X);" in output
        assert "tel" in output

if __name__ == "__main__":
    pytest.main([__file__, "-v"])
