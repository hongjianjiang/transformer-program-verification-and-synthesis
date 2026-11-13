
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
)
from rasptranslation.lustre.ast_nodes import (
    ASTNode as LustreNode,
    Application,
    BinaryOp,
    BinaryOperator,
    BoolLiteral,          # <-- add this
    Equation,
    IfThenElse,
    IntLiteral,
    LustreType,
    Node,
    Not as LustreNot,
    Parameter,
    Pre,
    Program as LustreProgram,
    Variable,
)
from rasptranslation.lustre.functions import generic_count_fun
from rasptranslation.rasp.walkers import HasPositionPeriods
from typing import Dict, List, Optional, Set

class CRASPToLustreTranslator:
    COUNT_TRUE_FUN = "__COUNT_TRUE__"
    POSITION_VAR = "__POS__"
    ONE_IF_FUN = "__ONE_IF__"

    def __init__(
        self,
        main_fun_name : str,
        input_name : str,
        output_bool_rule_name : str,
        alphabet_map : Dict[str, int],
    ):
        self.main_fun_name = main_fun_name
        self.input_name = input_name
        self.output_bool_rule_name = output_bool_rule_name
        self.alphabet_map = alphabet_map

    def translate_program(
        self,
        prog : CRASP,
    ) -> LustreProgram:
        """Translate a B-RASP program to Lustre"""
        aux_nodes = list()
        main_vars = list()
        main_eqs = list()
        in_var = Parameter(self.input_name, LustreType.INT)
        out_var = Parameter(self.output_bool_rule_name, LustreType.BOOL)

        if HasPositionPeriods().walk_crasp(prog).has_position_periods:
            main_vars.append(Parameter(self.POSITION_VAR, LustreType.INT))
            main_eqs.append(
                Equation(
                    self.POSITION_VAR,
                    BinaryOp(
                        BinaryOperator.ARROW,
                        IntLiteral(0),
                        BinaryOp(
                            BinaryOperator.ADD,
                            Pre(Variable(self.POSITION_VAR)),
                            IntLiteral(1),
                        ),
                    ),
                ),
            )

        if any(
            r.name == self.input_name
            for r in prog.bool_rules
        ) or any(
            r.name == self.input_name
            for r in prog.count_rules
        ):
            raise ValueError(
                "Input name {self.input_name} should not be the name of a "
                + "rule in the CRASP program. It should be unique."
            )

        if not any(
            r.name == self.output_bool_rule_name
            for r in prog.bool_rules
        ):
            raise ValueError(
                f"Output bool rule {self.output_bool_rule_name} not found "
                + "in CRASP program supplied."
            )

        self._translate_bool_rules(prog, main_vars, main_eqs)
        self._translate_count_rules(prog, main_vars, main_eqs, aux_nodes)

        local_vars = list(filter(
            lambda v: v not in [in_var, out_var],
            main_vars,
        ))

        aux_nodes.append(
            Node(
                name = self.main_fun_name,
                inputs = [in_var],
                outputs = [out_var],
                variables = local_vars,
                equations = main_eqs,
                checks = [],
            )
        )

        return LustreProgram(aux_nodes)

    def _translate_bool_rules(
        self,
        prog : CRASP,
        main_vars : List[Parameter],
        main_eqs : List[Equation],
    ):
        for rule in prog.bool_rules:
            main_vars.append(Parameter(rule.name, LustreType.BOOL))
            main_eqs.append(
                Equation(
                    rule.name,
                    self._translate_bool_expr(rule.body),
                ),
            )

    def _translate_bool_expr(self, expr : BoolExpr) -> LustreNode:
        if isinstance(expr, BoolExprAnd):
            return BinaryOp(
                BinaryOperator.AND,
                self._translate_bool_expr(expr.left),
                self._translate_bool_expr(expr.right),
            )
        elif isinstance(expr, BoolExprOr):
            return BinaryOp(
                BinaryOperator.OR,
                self._translate_bool_expr(expr.left),
                self._translate_bool_expr(expr.right),
            )
        elif isinstance(expr, BoolExprNot):
            return LustreNot(self._translate_bool_expr(expr.expr))
        elif isinstance(expr, BoolAtomBoolRule):
            return Variable(expr.bool_rule_name)
        elif isinstance(expr, BoolAtomInitial):
            return BinaryOp(
                BinaryOperator.EQ,
                Variable(self.input_name),
                IntLiteral(self.alphabet_map[expr.char]),
            )
        elif isinstance(expr, BoolAtomComparison):
            op = BinaryOperator.LT if expr.strict else BinaryOperator.LE
            return BinaryOp(
                op,
                self._translate_count_expr(expr.left),
                self._translate_count_expr(expr.right),
            )
        elif isinstance(expr, BoolAtomEqual):
            return BinaryOp(
                BinaryOperator.EQ,
                self._translate_count_expr(expr.left),
                self._translate_count_expr(expr.right),
            )
        elif isinstance(expr, BoolAtomPositionPeriod):
            return BinaryOp(
                BinaryOperator.EQ,
                IntLiteral(expr.offset),
                BinaryOp(
                    BinaryOperator.MOD,
                    Variable(self.POSITION_VAR),
                    IntLiteral(expr.period),
                ),
            )
        else:
            raise ValueError("Unexpected bool expr: " + str(expr))

    def _translate_count_rules(
        self,
        prog : CRASP,
        main_vars : List[Parameter],
        main_eqs : List[Equation],
        aux_nodes : List[LustreNode],
    ):
        aux_nodes.append(self._make_one_if_fun())
        aux_nodes.append(generic_count_fun(self.COUNT_TRUE_FUN))

        for rule in prog.count_rules:
            main_vars.append(Parameter(rule.name, LustreType.INT))
            main_eqs.append(
                Equation(
                    rule.name,
                    self._translate_count_expr(rule.body),
                ),
            )

    def _pre(self, count: int, expr: LustreNode) -> LustreNode:
        """
        Safe k-step pre:
        pre^k(expr) but initialized so that missing past = false.

        safe_pre(0,e) = e
        safe_pre(k+1,e) = false -> pre(safe_pre(k,e))
        """
        if count <= 0:
            return expr
        return BinaryOp(
            BinaryOperator.ARROW,
            BoolLiteral(False),
            Pre(self._pre(count - 1, expr)),
        )


    def _translate_count_atom_counting(
        self,
        expr : CountAtomCounting,
    ) -> LustreNode:
        if expr.range_start is None:
            return Application(
                self.COUNT_TRUE_FUN,
                [self._translate_bool_expr(expr.bool_expr)],
            )

        if expr.range_start <= 0:
            raise ValueError(
                "Local counting constraints must start "
                + "at least 1 step in the past. "
                + f"{expr.range_start} is not valid."
            )

        range_end = expr.range_end or expr.range_start
        if range_end < expr.range_start:
            raise ValueError(
                "In local counting constraint, range end must be "
                + f">= range start. ({expr.range_start}, {range_end}) "
                + "is not valid."
            )

        count = Application(
            self.ONE_IF_FUN,
            [
                self._pre(
                    expr.range_start,
                    self._translate_bool_expr(expr.bool_expr),
                ),
            ],
        )
        for offset in range(expr.range_start + 1, range_end + 1):
            count = BinaryOp(
                BinaryOperator.ADD,
                count,
                Application(
                    self.ONE_IF_FUN,
                    [
                        self._pre(
                            offset,
                            self._translate_bool_expr(expr.bool_expr),
                        ),
                    ],
                ),
            )

        return count

    def _translate_count_expr(self, expr : CountExpr) -> LustreNode:
        if isinstance(expr, CountAtomCounting):
            return self._translate_count_atom_counting(expr)
        elif isinstance(expr, CountAtomCountRule):
            return Variable(expr.count_rule_name)
        elif isinstance(expr, CountExprConditional):
            return IfThenElse(
                Variable(expr.bool_rule_name),
                self._translate_count_expr(expr.expr_then),
                self._translate_count_expr(expr.expr_else),
            )
        elif isinstance(expr, CountExprAdd):
            return BinaryOp(
                BinaryOperator.ADD,
                self._translate_count_expr(expr.left),
                self._translate_count_expr(expr.right),
            )
        elif isinstance(expr, CountExprSubtract):
            return BinaryOp(
                BinaryOperator.SUB,
                self._translate_count_expr(expr.left),
                self._translate_count_expr(expr.right),
            )
        elif isinstance(expr, CountExprMin):
            return IfThenElse(
                BinaryOp(
                    BinaryOperator.LE,
                    self._translate_count_expr(expr.left),
                    self._translate_count_expr(expr.right),
                ),
                self._translate_count_expr(expr.left),
                self._translate_count_expr(expr.right),
            )
        elif isinstance(expr, CountExprMax):
            return IfThenElse(
                BinaryOp(
                    BinaryOperator.LE,
                    self._translate_count_expr(expr.left),
                    self._translate_count_expr(expr.right),
                ),
                self._translate_count_expr(expr.right),
                self._translate_count_expr(expr.left),
            )
        elif isinstance(expr, CountAtomConst):
            return IntLiteral(expr.value)
        else:
            raise ValueError(
                f"Unrecognised count expression: {expr} of type {type(expr)}"
            )

    def _make_one_if_fun(self) -> Node:
        IN = "B"
        OUT = "V"
        return Node(
            name = self.ONE_IF_FUN,
            inputs = [Parameter(IN, LustreType.BOOL)],
            outputs = [Parameter(OUT, LustreType.INT)],
            variables = [],
            equations = [
                Equation(
                    OUT,
                    IfThenElse(
                        Variable(IN),
                        IntLiteral(1),
                        IntLiteral(0),
                    ),
                )
            ],
            checks = [],
        )

def translate_crasp_to_lustre(
    prog : CRASP,
    main_fun_name : str,
    input_name : str,
    output_bool_rule_name : str,
    alphabet_map : Optional[Dict[str, int]] = None,
) -> LustreProgram:
    alphabet_map = alphabet_map or translate_crasp_alphabet(prog.alphabet)
    translator = CRASPToLustreTranslator(
        main_fun_name,
        input_name,
        output_bool_rule_name,
        alphabet_map,
    )
    return translator.translate_program(prog)

def translate_crasp_alphabet(alphabet : Set[str]) -> Dict[str, int]:
    """Map alphabet to ints
    Use returned map to translate_crasp_to_lustre"""
    return {
        a : i
        for i, a in enumerate(sorted(alphabet))
    }

