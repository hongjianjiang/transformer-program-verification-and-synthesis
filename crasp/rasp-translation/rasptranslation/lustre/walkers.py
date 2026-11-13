"""
Walker for Lustre AST nodes.

This module provides a walker class for traversing Lustre AST nodes
in a pre-order manner (parents before children).
"""

from rasptranslation.lustre.ast_nodes import (
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

class Walker:
    """
    A walker class for Lustre AST nodes.

    Parents are visited before children (pre-order traversal). Node
    bodies are visited before their defined names. Subclass this and
    override on_* methods to do something with nodes.
    """

    def on_expr(self, node : ASTNode):
        pass

    def on_node_name(self, name : str, name_use : bool):
        """Called when a node name is encountered.
        :param name_use : true if use not definition"""
        pass

    def on_variable(self, node : Variable):
        pass

    def on_int_literal(self, node : IntLiteral):
        pass

    def on_bool_literal(self, node : BoolLiteral):
        pass

    def on_pre(self, node : Pre):
        pass

    def on_application(self, node : Application):
        pass

    def on_binary_op(self, node : BinaryOp):
        pass

    def on_not(self, node : Not):
        pass

    def on_if_then_else(self, node : IfThenElse):
        pass

    def on_equation(self, node : Equation):
        pass

    def on_check(self, node : Check):
        pass

    def on_contract(self, contract : Contract):
        pass

    def on_require(self, require : Require):
        pass

    def on_contract_var(self, contract_var : ContractVar):
        pass

    def on_parameter(self, node : Parameter):
        pass

    def on_node(self, node : Node):
        pass

    def on_program(self, node : Program):
        pass

    def on_end(self):
        pass

    def walk_expr(self, node : ASTNode):
        self.on_expr(node)
        if isinstance(node, Variable):
            return self.walk_variable(node)
        elif isinstance(node, IntLiteral):
            return self.walk_int_literal(node)
        elif isinstance(node, BoolLiteral):
            return self.walk_bool_literal(node)
        elif isinstance(node, Pre):
            return self.walk_pre(node)
        elif isinstance(node, Application):
            return self.walk_application(node)
        elif isinstance(node, BinaryOp):
            return self.walk_binary_op(node)
        elif isinstance(node, Not):
            return self.walk_not(node)
        elif isinstance(node, IfThenElse):
            return self.walk_if_then_else(node)
        else:
            raise ValueError(f"Unknown expression type : {type(node)}")

    def walk_node_name(self, name : str, name_use : bool):
        self.on_node_name(name, name_use)

    def walk_variable(self, node : Variable):
        self.on_variable(node)

    def walk_int_literal(self, node : IntLiteral):
        self.on_int_literal(node)

    def walk_bool_literal(self, node : BoolLiteral):
        self.on_bool_literal(node)

    def walk_pre(self, node : Pre):
        self.on_pre(node)
        self.walk_expr(node.expr)

    def walk_application(self, node : Application):
        self.on_application(node)
        self.walk_node_name(node.name, True)
        for arg in node.arguments:
            self.walk_expr(arg)

    def walk_binary_op(self, node : BinaryOp):
        self.on_binary_op(node)
        self.walk_expr(node.left)
        self.walk_expr(node.right)

    def walk_not(self, node : Not):
        self.on_not(node)
        self.walk_expr(node.expr)

    def walk_if_then_else(self, node : IfThenElse):
        self.on_if_then_else(node)
        self.walk_expr(node.condition)
        self.walk_expr(node.then_expr)
        self.walk_expr(node.else_expr)

    def walk_equation(self, node : Equation):
        self.on_equation(node)
        self.walk_expr(node.rhs)

    def walk_check(self, node : Check):
        self.on_check(node)

    def walk_parameter(self, node : Parameter):
        self.on_parameter(node)

    def walk_require(self, require : Require):
        self.on_require(require)
        self.walk_expr(require.expr)

    def walk_contract_var(self, contract_var : ContractVar):
        self.on_contract_var(contract_var)
        self.walk_expr(contract_var.expr)

    def walk_contract(self, contract: Contract):
        self.on_contract(contract)
        if isinstance(contract, Require):
            self.walk_require(contract)
            return
        elif isinstance(contract, ContractVar):
            self.walk_contract_var(contract)
            return
        else:
            raise ValueError(f"Unknown contract type : {type(contract)}")


    def walk_node(self, node : Node):
        self.on_node(node)
        for param in node.inputs:
            self.walk_parameter(param)
        for param in node.outputs:
            self.walk_parameter(param)
        for contract in node.contracts:
            self.walk_contract(contract)
        for param in node.variables:
            self.walk_parameter(param)
        for eq in node.equations:
            self.walk_equation(eq)
        for check in node.checks:
            self.walk_check(check)
        self.walk_node_name(node.name, False)

    def walk_program(self, node : Program):
        self.on_program(node)
        for n in node.nodes:
            self.walk_node(n)
        self.on_end()

class NodeNameCollector(Walker):
    """Collects all node names that are defined and used in a Lustre program."""

    def __init__(self):
        self.defined_node_names = set()
        self.used_node_names = set()

    def on_node_name(self, name : str, name_use : bool):
        if name_use:
            self.used_node_names.add(name)
        else:
            self.defined_node_names.add(name)

