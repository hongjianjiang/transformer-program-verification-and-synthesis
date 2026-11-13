"""
Lustre AST Node Definitions

This module defines the Abstract Syntax Tree (AST) nodes for Lustre
programs.
"""

from dataclasses import dataclass, field
from typing import List
from enum import Enum

@dataclass
class ASTNode:
    """Base class for all AST nodes"""
    pass

class LustreType(Enum):
    """Lustre types"""
    BOOL = "bool"
    INT = "int"

@dataclass
class Variable(ASTNode):
    """Variable reference"""
    name: str

    def __repr__(self):
        return self.name

@dataclass
class IntLiteral(ASTNode):
    """Integer literal"""
    value: int

    def __repr__(self):
        return str(self.value)

@dataclass
class BoolLiteral(ASTNode):
    """Boolean literal"""
    value: bool

    def __repr__(self):
        return "true" if self.value else "false"

@dataclass
class Pre(ASTNode):
    """Pre operator: pre(expr)"""
    expr: ASTNode

    def __repr__(self):
        return f"pre({self.expr})"

@dataclass
class Application(ASTNode):
    """Fun app: MYFUN(X, Y, Z)"""
    name: str
    arguments: List[ASTNode]

    def __repr__(self):
        return f"{self.name}(" + ",".join(str(p) for p in self.arguments) + ")"

class BinaryOperator(Enum):
    """Binary operators"""
    # Temporal
    ARROW = "->"
    # Logical
    AND = "and"
    OR = "or"
    XOR = "xor"
    # Arithmetic
    ADD = "+"
    SUB = "-"
    MUL = "*"
    DIV = "/"
    MOD = "mod"
    # Comparison
    EQ = "="
    NEQ = "<>"
    LT = "<"
    LE = "<="
    GT = ">"
    GE = ">="

@dataclass
class BinaryOp(ASTNode):
    """Binary operation"""
    op: BinaryOperator
    left: ASTNode
    right: ASTNode

    def __repr__(self):
        return f"({self.left} {self.op.value} {self.right})"

@dataclass
class Not(ASTNode):
    """Logical NOT"""
    expr: ASTNode

    def __repr__(self):
        return f"not {self.expr}"

@dataclass
class IfThenElse(ASTNode):
    """if ... then ... else expression"""
    condition: ASTNode
    then_expr: ASTNode
    else_expr: ASTNode

    def __repr__(self):
        return (
            f"if {self.condition} then {self.then_expr} "
            f"else {self.else_expr}"
        )

@dataclass
class Equation(ASTNode):
    """Equation: X = expr;"""
    lhs: str  # Variable name
    rhs: ASTNode  # Expression (can include arrow operators)

    def __repr__(self):
        return f"{self.lhs} = {self.rhs};"

@dataclass
class Check(ASTNode):
    """Check: check X"""
    name: str

    def __repr__(self):
        return f"  check {self.name};"

@dataclass
class Contract(ASTNode):
    pass

@dataclass
class Require(Contract):
    expr: ASTNode

    def __repr__(self):
        # Note: should be require in future versions of Kind2
        return f"  assume {self.expr};"

@dataclass
class ContractVar(Contract):
    name: str
    type: LustreType
    expr: ASTNode

    def __repr__(self):
        return f"  var {self.name}: {self.type.value} = {self.expr};"

@dataclass
class Parameter(ASTNode):
    """Parameter declaration: name: type"""
    name: str
    type: LustreType

    def __repr__(self):
        return f"{self.name}: {self.type.value}"

@dataclass
class Node(ASTNode):
    """Node declaration"""
    name: str
    inputs: List[Parameter] = field(default_factory=list)
    outputs: List[Parameter] = field(default_factory=list)
    variables: List[Parameter] = field(default_factory=list)
    contracts : List[Contract] = field(default_factory=list)
    equations: List[Equation] = field(default_factory=list)
    checks: List[Check] = field(default_factory=list)

    def __post_init__(self):
        if self.variables is None:
            self.variables = []

    def __repr__(self):
        inputs_str = ", ".join(str(p) for p in self.inputs)
        outputs_str = ", ".join(str(p) for p in self.outputs)

        # Build the node declaration
        result = (
            f"node {self.name} ({inputs_str}) "
            f"returns ({outputs_str});\n"
        )

        if len(self.contracts) > 0:
            contract_str = "\n  ".join(str(eq) for eq in self.contracts)
            # TODO: should be con...noc instead of (*@contract...*) in
            # newer versions of Kind2
            result += f"(*@contract\n  {contract_str}\n*)\n"

        # Add var declaration if there are variables
        if len(self.variables) > 0:
            vars_str = ";\n  ".join(str(v) for v in self.variables)
            result += f"var\n  {vars_str};\n"

        # Add equations and checks
        eqs_str = "\n  ".join(str(eq) for eq in self.equations)
        checks_str = "\n  ".join(str(check) for check in self.checks)
        if len(checks_str) > 0:
            checks_str += "\n"
        result += f"let\n  {eqs_str}\n{checks_str}tel"

        return result

@dataclass
class Program(ASTNode):
    """Top-level program: sequence of nodes"""
    nodes: List[Node]

    def __repr__(self):
        return "\n\n".join(str(node) for node in self.nodes)

