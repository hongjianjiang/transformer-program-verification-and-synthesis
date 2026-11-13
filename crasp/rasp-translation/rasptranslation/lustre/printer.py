"""
Lustre Printer

This module provides pretty-printing for Lustre AST nodes.
"""

from typing import List
from .ast_nodes import (
    ASTNode,
    Application,
    BinaryOp,
    BoolLiteral,
    Check,
    Contract,
    ContractVar,
    Equation,
    IfThenElse,
    IntLiteral,
    Node,
    Not,
    Parameter,
    Pre,
    Program,
    Require,
    Variable,
)

class LustrePrinter:
    """Pretty printer for Lustre programs"""

    def __init__(self, indent_size: int = 2):
        self.indent_size = indent_size
        self.indent_level = 0

    def _indent(self) -> str:
        """Get current indentation string"""
        return " " * (self.indent_level * self.indent_size)

    def print_program(self, program: Program) -> str:
        """Print a complete program"""
        return "\n\n".join(self.print_node(node) for node in program.nodes)

    def print_node(self, node: Node) -> str:
        """Print a node declaration"""
        # Header
        inputs_str = self.print_param_list(node.inputs)
        outputs_str = self.print_param_list(node.outputs)
        result = (
            f"node {node.name} ({inputs_str}) "
            f"returns ({outputs_str});\n"
        )

        if len(node.contracts) > 0:
            # TODO: should be con...noc instead of (*@contract...*) in
            # newer versions of Kind2
            result += "(*@contract\n"
            self.indent_level += 1
            result += self.print_contract_list(node.contracts)
            self.indent_level -= 1
            result += "*)"

        # Variable declaration
        if len(node.variables) > 0:
            result += f"var\n"
            self.indent_level += 1
            result += self.print_var_list(node.variables)
            self.indent_level -= 1

        # Body
        self.indent_level += 1
        equations_str = "\n".join(
            self._indent() + self.print_equation(eq)
            for eq in node.equations
        )
        checks_str = "\n".join(
            self._indent() + self.print_check(check)
            for check in node.checks
        )
        if len(checks_str) > 0:
            checks_str += "\n"
        self.indent_level -= 1

        result += f"let\n{equations_str}\n{checks_str}tel"
        return result

    def print_param_list(self, params: List[Parameter]) -> str:
        """Print parameter list"""
        return "; ".join(self.print_parameter(p) for p in params)

    def print_contract_list(self, contracts: List[Contract]) -> str:
        """Print parameter list"""
        separator = ";\n" + self._indent()
        return self._indent() \
            + separator.join(self.print_contract(c) for c in contracts) + ";\n"

    def print_var_list(self, params: List[Parameter]) -> str:
        """Print parameter list"""
        separator = ";\n" + self._indent()
        return self._indent() \
            + separator.join(self.print_parameter(p) for p in params) + ";\n"

    def print_contract(self, expr: Contract) -> str:
        """Print a parameter"""
        if isinstance(expr, Require):
            e = self.print_expr(expr.expr)
            # TODO: should be require not assume in future versions of Kind2
            return f"assume {e}"
        if isinstance(expr, ContractVar):
            e = self.print_expr(expr.expr)
            return f"var {expr.name}: {expr.type.value} = {e}"
        else:
            raise ValueError(f"Unknown contract type {type(expr)}")

    def print_parameter(self, param: Parameter) -> str:
        """Print a parameter"""
        return f"{param.name}: {param.type.value}"

    def print_equation(self, eq: Equation) -> str:
        """Print an equation"""
        return f"{eq.lhs} = {self.print_expr(eq.rhs)};"

    def print_check(self, check: Check) -> str:
        """Print an equation"""
        return f"check {check.name};"

    def print_expr(self, expr: ASTNode) -> str:
        """Print an expression"""
        if isinstance(expr, Variable):
            return expr.name

        elif isinstance(expr, IntLiteral):
            return str(expr.value)

        elif isinstance(expr, BoolLiteral):
            return "true" if expr.value else "false"

        elif isinstance(expr, Pre):
            return f"pre({self.print_expr(expr.expr)})"

        elif isinstance(expr, Not):
            return f"not {self.print_expr(expr.expr)}"

        elif isinstance(expr, BinaryOp):
            left = self.print_expr(expr.left)
            right = self.print_expr(expr.right)
            op = expr.op.value
            return f"({left} {op} {right})"

        elif isinstance(expr, IfThenElse):
            cond = self.print_expr(expr.condition)
            then_e = self.print_expr(expr.then_expr)
            else_e = self.print_expr(expr.else_expr)
            return f"if {cond} then {then_e} else {else_e}"

        elif isinstance(expr, Application):
            args = ",".join(self.print_expr(a) for a in expr.arguments)
            return f"{expr.name}({args})"
        else:
            raise ValueError(f"Unknown expression type: {type(expr)}")


# ====================================================================
# Convenience Function
# ====================================================================

def print_lustre(program: Program, indent_size: int = 2) -> str:
    """Pretty-print a Lustre program"""
    printer = LustrePrinter(indent_size)
    return printer.print_program(program)

