"""
Transformer for Lustre AST nodes.

This module provides a transformer class for traversing and transforming
Lustre AST nodes in a post-order manner (children before parents).
"""

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
from typing import Callable, List

class Transformer:
    """
    A transformer class for Lustre AST nodes.

    Children are visited before parents (post-order traversal). Subclass
    this and override transform_* methods to customize transformation behavior.
    """

    def transform_expr(self, node : ASTNode) -> ASTNode:
        """Transform an expression node."""
        if isinstance(node, Variable):
            return self.transform_variable(node)
        elif isinstance(node, IntLiteral):
            return self.transform_int_literal(node)
        elif isinstance(node, BoolLiteral):
            return self.transform_bool_literal(node)
        elif isinstance(node, Pre):
            return self.transform_pre(node)
        elif isinstance(node, Application):
            return self.transform_application(node)
        elif isinstance(node, BinaryOp):
            return self.transform_binary_op(node)
        elif isinstance(node, Not):
            return self.transform_not(node)
        elif isinstance(node, IfThenElse):
            return self.transform_if_then_else(node)
        else:
            raise ValueError(f"Unknown expression type : {type(node)}")

    def transform_node_name(self, name : str, name_use : bool) -> str:
        """Transform a node name.
        :param name_use : true if use not definition"""
        return name

    def transform_variable(self, node : Variable) -> ASTNode:
        """Transform a Variable node."""
        return Variable(name=node.name)

    def transform_int_literal(self, node : IntLiteral) -> ASTNode:
        """Transform an IntLiteral node."""
        return IntLiteral(value=node.value)

    def transform_bool_literal(self, node : BoolLiteral) -> ASTNode:
        """Transform a BoolLiteral node."""
        return BoolLiteral(value=node.value)

    def transform_pre(self, node : Pre) -> ASTNode:
        """Transform a Pre node."""
        expr = self.transform_expr(node.expr)
        return Pre(expr=expr)

    def transform_application(self, node : Application) -> ASTNode:
        """Transform an Application node."""
        name = self.transform_node_name(node.name, True)
        arguments = [self.transform_expr(arg) for arg in node.arguments]
        return Application(name=name, arguments=arguments)

    def transform_binary_op(self, node : BinaryOp) -> ASTNode:
        """Transform a BinaryOp node."""
        left = self.transform_expr(node.left)
        right = self.transform_expr(node.right)
        return BinaryOp(op=node.op, left=left, right=right)

    def transform_not(self, node : Not) -> ASTNode:
        """Transform a Not node."""
        expr = self.transform_expr(node.expr)
        return Not(expr=expr)

    def transform_if_then_else(self, node : IfThenElse) -> ASTNode:
        """Transform an IfThenElse node."""
        condition = self.transform_expr(node.condition)
        then_expr = self.transform_expr(node.then_expr)
        else_expr = self.transform_expr(node.else_expr)
        return IfThenElse(
            condition=condition,
            then_expr=then_expr,
            else_expr=else_expr
        )

    def transform_equation(self, node : Equation) -> Equation:
        """Transform an Equation node."""
        rhs = self.transform_expr(node.rhs)
        return Equation(lhs=node.lhs, rhs=rhs)

    def transform_check(self, node : Check) -> Check:
        """Transform a Check node."""
        return Check(name=node.name)

    def transform_parameter(self, node : Parameter) -> Parameter:
        """Transform a Parameter node."""
        return Parameter(name=node.name, type=node.type)

    def transform_require(self, require : Require) -> Require:
        return Require(expr=self.transform_expr(require.expr))

    def transform_contract_var(self, contract_var : ContractVar) -> ContractVar:
        return ContractVar(
            name=contract_var.name,
            type=contract_var.type,
            expr=self.transform_expr(contract_var.expr),
        )

    def transform_contract(self, contract : Contract) -> Contract:
        """Transform a Parameter node."""
        if isinstance(contract, Require):
            return self.transform_require(contract)
        if isinstance(contract, ContractVar):
            return self.transform_contract_var(contract)
        else:
            raise ValueError(f"Unknown expression type : {type(contract)}")

    def transform_node(self, node : Node) -> Node:
        """Transform a Node declaration."""
        name = self.transform_node_name(node.name, False)
        inputs = [self.transform_parameter(p) for p in node.inputs]
        outputs = [self.transform_parameter(p) for p in node.outputs]
        contracts = [self.transform_contract(c) for c in node.contracts]
        variables = [self.transform_parameter(v) for v in node.variables]
        equations = [self.transform_equation(eq) for eq in node.equations]
        checks = [self.transform_check(chk) for chk in node.checks]
        return Node(
            name=name,
            inputs=inputs,
            outputs=outputs,
            contracts=contracts,
            variables=variables,
            equations=equations,
            checks=checks
        )

    def transform_program(self, node : Program) -> Program:
        """Transform a Program (top-level AST node)."""
        nodes = [self.transform_node(n) for n in node.nodes]
        return Program(nodes=nodes)


class NodeRenamer(Transformer):
    """A transformer that renames nodes using a provided function."""

    def __init__(self, rename_node : Callable[[str], str]):
        self.rename_node = rename_node

    def transform_node_name(self, name : str, name_use : bool) -> str:
        return self.rename_node(name)

def list_to_expression(word : List[int], eos : int, eternity : int):
    """Convert a list of integers into a sequence expression.
    E.g. [1,2] becomes a sequence 1, 2, eos, eternity*"""
    word_stream = BinaryOp(
        BinaryOperator.ARROW,
        IntLiteral(eos),
        IntLiteral(eternity),
    )
    for i in word[::-1]:
        if i in [eos, eternity]:
            continue
        word_stream = BinaryOp(
            BinaryOperator.ARROW,
            IntLiteral(i),
            Pre(word_stream),
        )
    return word_stream

def constrain_input_stream_to_word(
    lustre: Program,
    check_node: str,
    input_var: str,
    word_ints: List[int],
    eos: int,
    eternity: int,
    suffix: str = "0",
):
    node = next((n for n in lustre.nodes if n.name == check_node), None)
    if node is None:
        raise ValueError(f"Cannot find {check_node} in lustre program.")

    word_name = f"__WORD_{suffix}"
    word_stream = list_to_expression(word_ints, eos, eternity)

    # var __WORD_k: int = <stream>;
    node.contracts.append(ContractVar(word_name, LustreType.INT, word_stream))

    # assume (__I__ = __WORD_k);
    node.contracts.append(
        Require(
            BinaryOp(
                BinaryOperator.EQ,
                Variable(input_var),
                Variable(word_name),
            )
        )
    )

