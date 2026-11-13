
from rasptranslation.rasp.ast_nodes import (
    BoolAtomBoolRule,
    BoolAtomComparison,
    BoolAtomEqual,
    BoolAtomInitial,
    BoolAtomPositionPeriod,
    BoolExpr,
    BoolExprAnd,
    BoolExprNot,
    BoolExprOr,
    BoolRule,
    CRASP,
    CountAtomConst,
    CountAtomCountRule,
    CountAtomCounting,
    CountExpr,
    CountExprAdd,
    CountExprConditional,
    CountExprMax,
    CountExprMin,
    CountExprSubtract,
    CountRule,
)

from typing import Callable

class Transformer:
    """
    A transformer class for RASP AST nodes.

    Children are visited before parents (post-order traversal). Subclass
    this and override transform_* methods to customize transformation behavior.
    """

    def transform_bool_expr(self, node : BoolExpr) -> BoolExpr:
        """Transform a BoolExpr node."""
        if isinstance(node, BoolExprAnd):
            return self.transform_bool_and(node)
        elif isinstance(node, BoolExprOr):
            return self.transform_bool_or(node)
        elif isinstance(node, BoolExprNot):
            return self.transform_bool_not(node)
        elif isinstance(node, BoolAtomInitial):
            return self.transform_bool_atom_initial(node)
        elif isinstance(node, BoolAtomBoolRule):
            return self.transform_bool_atom_bool_rule(node)
        elif isinstance(node, BoolAtomComparison):
            return self.transform_bool_atom_comparison(node)
        elif isinstance(node, BoolAtomEqual):
            return self.transform_bool_atom_equal(node)
        elif isinstance(node, BoolAtomPositionPeriod):
            return self.transform_bool_atom_position_period(node)
        else:
            raise ValueError(f"Unknown BoolExpr type : {type(node)}")

    def transform_count_expr(self, node : CountExpr) -> CountExpr:
        """Transform a CountExpr node."""
        if isinstance(node, CountAtomCounting):
            return self.transform_count_atom_counting(node)
        if isinstance(node, CountAtomCountRule):
            return self.transform_count_atom_count_rule(node)
        elif isinstance(node, CountExprConditional):
            return self.transform_count_expr_conditional(node)
        elif isinstance(node, CountExprAdd):
            return self.transform_count_expr_add(node)
        elif isinstance(node, CountExprSubtract):
            return self.transform_count_expr_subtract(node)
        elif isinstance(node, CountExprMin):
            return self.transform_count_expr_min(node)
        elif isinstance(node, CountExprMax):
            return self.transform_count_expr_max(node)
        elif isinstance(node, CountAtomConst):
            return self.transform_count_atom_const(node)
        else:
            raise ValueError(f"Unknown CountRuleBody type : {type(node)}")

    def transform_bool_rule_name(self, name : str, name_use : bool) -> str:
        """Transform a bool rule name.
        :param name_use: true if use not definition"""
        return name

    def transform_count_rule_name(self, name : str, name_use : bool) -> str:
        """Transform a count rule name.
        :param name_use: true if use not definition"""
        return name

    def transform_bool_and(self, node : BoolExprAnd) -> BoolExpr:
        left = self.transform_bool_expr(node.left)
        right = self.transform_bool_expr(node.right)
        return BoolExprAnd(left=left, right=right)

    def transform_bool_or(self, node : BoolExprOr) -> BoolExpr:
        left = self.transform_bool_expr(node.left)
        right = self.transform_bool_expr(node.right)
        return BoolExprOr(left=left, right=right)

    def transform_bool_not(self, node : BoolExprNot) -> BoolExpr:
        expr = self.transform_bool_expr(node.expr)
        return BoolExprNot(expr=expr)

    def transform_bool_atom_bool_rule(
        self,
        node : BoolAtomBoolRule,
    ) -> BoolExpr:
        bool_rule_name = self.transform_bool_rule_name(
            node.bool_rule_name,
            True,
        )
        return BoolAtomBoolRule(bool_rule_name=bool_rule_name)

    def transform_letter(self, a : str) -> str:
        return a

    def transform_bool_atom_initial(
        self,
        node : BoolAtomInitial,
    ) -> BoolExpr:
        return BoolAtomInitial(char = self.transform_letter(node.char))

    def transform_bool_atom_comparison(
        self,
        node : BoolAtomComparison,
    ) -> BoolExpr:
        left = self.transform_count_expr(node.left)
        right = self.transform_count_expr(node.right)
        return BoolAtomComparison(
            strict=node.strict,
            left=left,
            right=right,
        )

    def transform_bool_rule(self, node : BoolRule) -> BoolRule:
        name = self.transform_bool_rule_name(node.name, False)
        body = self.transform_bool_expr(node.body)
        return BoolRule(name=name, body=body)

    def transform_bool_atom_equal(self, node : BoolAtomEqual) -> BoolExpr:
        left = self.transform_count_expr(node.left)
        right = self.transform_count_expr(node.right)
        return BoolAtomEqual(left=left, right=right)

    def transform_bool_atom_position_period(
        self,
        node : BoolAtomPositionPeriod,
    ) -> BoolExpr:
        return node

    def transform_count_atom_counting(
        self,
        node : CountAtomCounting,
    ) -> CountExpr:
        bool_expr = self.transform_bool_expr(node.bool_expr)
        return CountAtomCounting(bool_expr=bool_expr)

    def transform_count_atom_count_rule(
        self,
        node : CountAtomCountRule,
    ) -> CountExpr:
        count_rule_name = self.transform_count_rule_name(
            node.count_rule_name,
            True,
        )
        return CountAtomCountRule(count_rule_name=count_rule_name)

    def transform_count_expr_conditional(
        self,
        node : CountExprConditional,
    ) -> CountExpr:
        bool_rule_name \
            = self.transform_bool_rule_name(node.bool_rule_name, True)
        expr_then = self.transform_count_expr(node.expr_then)
        expr_else = self.transform_count_expr(node.expr_else)
        return CountExprConditional(
            bool_rule_name=bool_rule_name,
            expr_then=expr_then,
            expr_else=expr_else,
        )

    def transform_count_expr_add(self, node : CountExprAdd) -> CountExpr:
        left = self.transform_count_expr(node.left)
        right = self.transform_count_expr(node.right)
        return CountExprAdd(left=left, right=right)

    def transform_count_expr_min(self, node : CountExprMin) -> CountExpr:
        left = self.transform_count_expr(node.left)
        right = self.transform_count_expr(node.right)
        return CountExprMin(left=left, right=right)

    def transform_count_expr_subtract(
        self,
        node : CountExprSubtract,
    ) -> CountExpr:
        left = self.transform_count_expr(node.left)
        right = self.transform_count_expr(node.right)
        return CountExprSubtract(left=left, right=right)

    def transform_count_expr_max(self, node : CountExprMax) -> CountExpr:
        left = self.transform_count_expr(node.left)
        right = self.transform_count_expr(node.right)
        return CountExprMax(left=left, right=right)

    def transform_count_atom_const(
        self,
        node : CountAtomConst,
    ) -> CountExpr:
        return CountAtomConst(value=node.value)

    def transform_count_rule(self, node : CountRule) -> CountRule:
        name = self.transform_count_rule_name(node.name, False)
        body = self.transform_count_expr(node.body)
        return CountRule(name=name, body=body)

    def transform_crasp(self, node : CRASP) -> CRASP:
        alphabet = set(self.transform_letter(a) for a in node.alphabet)
        bool_rules = [
            self.transform_bool_rule(rule)
            for rule in node.bool_rules
        ]
        count_rules = [
            self.transform_count_rule(rule)
            for rule in node.count_rules
        ]
        return CRASP(
            alphabet = alphabet,
            bool_rules = bool_rules,
            count_rules = count_rules,
        )

class RuleRenamer(Transformer):
    def __init__(
        self,
        rename_bool_rule  : Callable[[str], str],
        rename_count_rule  : Callable[[str], str],
    ):
        self.rename_bool_rule = rename_bool_rule
        self.rename_count_rule = rename_count_rule

    def transform_bool_rule_name(self, name : str, name_use : bool) -> str:
        return self.rename_bool_rule(name)

    def transform_count_rule_name(self, name : str, name_use : bool) -> str:
        return self.rename_count_rule(name)

