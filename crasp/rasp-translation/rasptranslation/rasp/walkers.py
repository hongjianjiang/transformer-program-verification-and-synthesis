
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

from typing import Self

class Walker:
    """
    A walker class for RASP AST nodes.

    Parents are visited before parents (pre-order traversal). Rule
    bodies are visited before their defined names. Subclass
    this and override on_* methods to do something with nodes.
    """

    def __init__(self):
        self._terminated = False

    def on_bool_expr(self, node : BoolExpr):
        pass

    def on_count_expr(self, node : CountExpr):
        pass

    def on_bool_rule_name(self, name : str, name_use : bool):
        pass

    def on_count_rule_name(self, name : str, name_use : bool):
        pass

    def on_bool_and(self, node : BoolExprAnd):
        pass

    def on_bool_or(self, node : BoolExprOr):
        pass

    def on_bool_not(self, node : BoolExprNot):
        pass

    def on_bool_atom_bool_rule(self, node : BoolAtomBoolRule):
        pass

    def on_bool_atom_initial(self, node : BoolAtomInitial):
        pass

    def on_bool_atom_comparison(self, node : BoolAtomComparison):
        pass

    def on_bool_atom_position_period(self, node : BoolAtomPositionPeriod):
        pass

    def on_bool_rule(self, node : BoolRule):
        pass

    def on_count_atom_counting(self, node : CountAtomCounting):
        pass

    def on_bool_atom_equal(self, node : BoolAtomEqual):
        pass

    def on_count_expr_conditional(self, node : CountExprConditional):
        pass

    def on_count_expr_add(self, node : CountExprAdd):
        pass

    def on_count_expr_min(self, node : CountExprMin):
        pass

    def on_count_expr_subtract(self, node : CountExprSubtract):
        pass

    def on_count_expr_max(self, node : CountExprMax):
        pass

    def on_count_atom_const(self, node : CountAtomConst):
        pass

    def on_count_atom_count_rule(self, node : CountAtomCountRule):
        pass

    def on_count_rule(self, node : CountRule):
        pass

    def on_crasp(self, node : CRASP):
        pass

    def on_start(self):
        pass

    def on_end(self):
        pass

    def walk_bool_expr(self, node : BoolExpr):
        self.on_bool_expr(node)
        if self._terminated:
            return

        if isinstance(node, BoolExprAnd):
            return self.walk_bool_and(node)
        elif isinstance(node, BoolExprOr):
            return self.walk_bool_or(node)
        elif isinstance(node, BoolExprNot):
            return self.walk_bool_not(node)
        elif isinstance(node, BoolAtomBoolRule):
            return self.walk_bool_atom_bool_rule(node)
        elif isinstance(node, BoolAtomInitial):
            return self.walk_bool_atom_initial(node)
        elif isinstance(node, BoolAtomComparison):
            return self.walk_bool_atom_comparison(node)
        elif isinstance(node, BoolAtomPositionPeriod):
            return self.walk_bool_atom_position_period(node)
        elif isinstance(node, BoolAtomEqual):
            return self.walk_bool_atom_equal(node)
        else:
            raise ValueError(f"Unknown BoolExpr type : {type(node)}")

    def walk_count_expr(self, node : CountExpr):
        self.on_count_expr(node)
        if self._terminated:
            return

        if isinstance(node, CountAtomCounting):
            return self.walk_count_atom_counting(node)
        elif isinstance(node, CountExprConditional):
            return self.walk_count_expr_conditional(node)
        elif isinstance(node, CountExprAdd):
            return self.walk_count_expr_add(node)
        elif isinstance(node, CountExprSubtract):
            return self.walk_count_expr_subtract(node)
        elif isinstance(node, CountExprMin):
            return self.walk_count_expr_min(node)
        elif isinstance(node, CountExprMax):
            return self.walk_count_expr_max(node)
        elif isinstance(node, CountAtomConst):
            return self.walk_count_atom_const(node)
        elif isinstance(node, CountAtomCountRule):
            return self.walk_count_atom_count_rule(node)
        else:
            raise ValueError(f"Unknown CountExpr type : {type(node)}")

    def walk_bool_rule_name(self, name : str, name_use):
        self.on_bool_rule_name(name, name_use)

    def walk_count_rule_name(self, name : str, name_use : bool):
        self.on_count_rule_name(name, name_use)

    def walk_bool_and(self, node : BoolExprAnd):
        self.on_bool_and(node)
        if not self._terminated:
            self.walk_bool_expr(node.left)
        if not self._terminated:
            self.walk_bool_expr(node.right)

    def walk_bool_or(self, node : BoolExprOr):
        self.on_bool_or(node)
        if not self._terminated:
            self.walk_bool_expr(node.left)
        if not self._terminated:
            self.walk_bool_expr(node.right)

    def walk_bool_not(self, node : BoolExprNot):
        self.on_bool_not(node)
        if not self._terminated:
            self.walk_bool_expr(node.expr)

    def walk_bool_atom_bool_rule(self, node : BoolAtomBoolRule):
        self.on_bool_atom_bool_rule(node)
        if not self._terminated:
            self.walk_bool_rule_name(node.bool_rule_name, True)

    def walk_bool_atom_initial(self, node : BoolAtomInitial):
        self.on_bool_atom_initial(node)

    def walk_bool_atom_comparison(self, node : BoolAtomComparison):
        self.on_bool_atom_comparison(node)
        if not self._terminated:
            self.walk_count_expr(node.left)
        if not self._terminated:
            self.walk_count_expr(node.right)

    def walk_bool_atom_position_period(self, node : BoolAtomPositionPeriod):
        self.on_bool_atom_position_period(node)

    def walk_bool_atom_equal(self, node : BoolAtomEqual):
        self.on_bool_atom_equal(node)
        if not self._terminated:
            self.walk_count_expr(node.left)
        if not self._terminated:
            self.walk_count_expr(node.right)

    def walk_bool_rule(self, node : BoolRule):
        self.on_bool_rule(node)
        if not self._terminated:
            self.walk_bool_expr(node.body)
        if not self._terminated:
            self.walk_bool_rule_name(node.name, False)

    def walk_count_atom_counting(self, node : CountAtomCounting):
        self.on_count_atom_counting(node)
        if not self._terminated:
            self.walk_bool_expr(node.bool_expr)

    def walk_count_expr_conditional(self, node : CountExprConditional):
        self.on_count_expr_conditional(node)
        if not self._terminated:
            self.walk_bool_rule_name(node.bool_rule_name, True)
        if not self._terminated:
            self.walk_count_expr(node.expr_then)
        if not self._terminated:
            self.walk_count_expr(node.expr_else)

    def walk_count_expr_add(self, node : CountExprAdd):
        self.on_count_expr_add(node)
        if not self._terminated:
            self.walk_count_expr(node.left)
        if not self._terminated:
            self.walk_count_expr(node.right)

    def walk_count_expr_min(self, node : CountExprMin):
        self.on_count_expr_min(node)
        if not self._terminated:
            self.walk_count_expr(node.left)
        if not self._terminated:
            self.walk_count_expr(node.right)

    def walk_count_expr_subtract(self, node : CountExprSubtract):
        self.on_count_expr_subtract(node)
        if not self._terminated:
            self.walk_count_expr(node.left)
        if not self._terminated:
            self.walk_count_expr(node.right)

    def walk_count_expr_max(self, node : CountExprMax):
        self.on_count_expr_max(node)
        if not self._terminated:
            self.walk_count_expr(node.left)
        if not self._terminated:
            self.walk_count_expr(node.right)

    def walk_count_atom_const(self, node : CountAtomConst):
        self.on_count_atom_const(node)

    def walk_count_atom_count_rule(self, node : CountAtomCountRule):
        self.on_count_atom_count_rule(node)
        if not self._terminated:
            self.walk_count_rule_name(node.count_rule_name, True)

    def walk_count_rule(self, node : CountRule):
        self.on_count_rule(node)
        if not self._terminated:
            self.walk_count_expr(node.body)
        if not self._terminated:
            self.walk_count_rule_name(node.name, False)

    def walk_crasp(self, node : CRASP) -> Self:
        self._terminated = False
        self.on_start()
        self.on_crasp(node)
        for rule in node.bool_rules:
            if not self._terminated:
                self.walk_bool_rule(rule)
        if not self._terminated:
            for rule in node.count_rules:
                if not self._terminated:
                    self.walk_count_rule(rule)
        self.on_end()
        return self

class RuleNameCollector(Walker):
    def __init__(self):
        self.defined_bool_rule_names = set()
        self.defined_count_rule_names = set()
        self.used_bool_rule_names = set()
        self.used_count_rule_names = set()

    def on_bool_rule_name(self, name : str, name_use : bool):
        if name_use:
            self.used_bool_rule_names.add(name)
        else:
            self.defined_bool_rule_names.add(name)

    def on_count_rule_name(self, name : str, name_use : bool):
        if name_use:
            self.used_count_rule_names.add(name)
        else:
            self.defined_count_rule_names.add(name)

class HasPositionPeriods(Walker):
    def on_start(self):
        self.has_position_periods = False

    def on_bool_atom_position_period(self, node : BoolAtomPositionPeriod):
        self.has_position_periods = True
        self._terminated = True

