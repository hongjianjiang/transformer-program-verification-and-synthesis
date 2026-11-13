
from lark import Lark, Transformer

from rasptranslation.rasp.ast_nodes import (
    BoolAtomBoolRule,
    BoolAtomComparison,
    BoolAtomEqual,
    BoolAtomInitial,
    BoolAtomPositionPeriod,
    BoolExprAnd,
    BoolExprNot,
    BoolExprOr,
    BoolRule,
    CRASP,
    CountAtomConst,
    CountAtomCountRule,
    CountAtomCounting,
    CountExprAdd,
    CountExprConditional,
    CountExprMax,
    CountExprMin,
    CountExprSubtract,
    CountRule,
)
from rasptranslation.rasp.walkers import Walker

GRAMMAR = """
    %import common.DIGIT
    %import common.INT
    %import common.LCASE_LETTER
    %import common.UCASE_LETTER
    %import common.WS
    %ignore WS

    UNAME: ("_" | UCASE_LETTER) ("_" | UCASE_LETTER | LCASE_LETTER | DIGIT)*
    LNAME: ("_" | LCASE_LETTER) ("_" | LCASE_LETTER | DIGIT)*

    COMMENT: "/*" /.*/s "*/"
    %ignore COMMENT

    crasp: alphabet rule*

    alphabet: "ALPHABET: " letter*

    rule: bool_rule | count_rule

    bool_rule_name: UNAME
    count_rule_name: UNAME
    letter: LNAME

    bool_rule: bool_rule_name "=" bool_expr
    bool_expr: bool_expr "|" bool_expr -> bool_or
        | bool_expr "&" bool_expr -> bool_and
        | "!" bool_expr -> bool_not
        | bool_rule_name -> bool_atom_bool_rule
        | letter -> bool_atom_initial
        | comparison -> bool_atom_comparison
        | "(" bool_expr ")" -> bool_expr_brackets
        | position_period -> bool_atom_position_period
    comparison: count_expr "<=" count_expr -> comparison_non_strict
        | count_expr "<" count_expr -> comparison_strict
        | count_expr "=" count_expr -> comparison_equal
    position_period: "%" INT "=" INT

    count_rule: count_rule_name "=" count_expr
    count_expr : "#" bool_expr -> count_expr_counting
        | "#<(" INT ")" bool_expr -> count_expr_counting_at_pos
        | "#<(" INT "," INT ")" bool_expr -> count_expr_counting_range
        | bool_rule_name "?" count_expr ":" count_expr -> count_expr_conditional
        | count_expr "+" count_expr -> count_expr_add
        | count_expr "-" count_expr -> count_expr_subtract
        | "max(" count_expr "," count_expr ")" -> count_expr_max
        | "min(" count_expr "," count_expr ")" -> count_expr_min
        | INT -> count_expr_constant
        | count_rule_name -> count_expr_count_rule
        | "(" count_expr ")" -> count_expr_brackets
"""

CRASP_PARSER = Lark(GRAMMAR, start='crasp')

class CRASPTransformer(Transformer):
    """Transformer to convert Lark parse tree to CRASP AST"""

    # Extract token values from terminal rules
    def bool_rule_name(self, children):
        return children[0].value

    def bool_expr_brackets(self, children):
        return children[0]

    def bool_atom_bool_rule(self, children):
        return BoolAtomBoolRule(children[0])

    def count_rule_name(self, children):
        return children[0].value

    def letter(self, children):
        return children[0].value

    def bool_or(self, children):
        left, right = children
        return BoolExprOr(left, right)

    def bool_and(self, children):
        left, right = children
        return BoolExprAnd(left, right)

    def bool_not(self, children):
        return BoolExprNot(children[0])

    def bool_atom_initial(self, children):
        return BoolAtomInitial(children[0])

    def bool_atom_comparison(self, children):
        return children[0]

    def bool_atom_position_period(self, children):
        return children[0]

    def position_period(self, children):
        period, offset = children
        return BoolAtomPositionPeriod(int(period.value), int(offset.value))

    def comparison_non_strict(self, children):
        return BoolAtomComparison(False, children[0], children[1])

    def comparison_strict(self, children):
        return BoolAtomComparison(True, children[0], children[1])

    def comparison_equal(self, children):
        return BoolAtomEqual(children[0], children[1])

    def count_expr_counting(self, children):
        return CountAtomCounting(children[0])

    def count_expr_counting_at_pos(self, children):
        return CountAtomCounting(
            children[1],
            int(children[0].value),
        )

    def count_expr_counting_range(self, children):
        return CountAtomCounting(
            children[2],
            int(children[0].value),
            int(children[1].value),
        )

    def count_expr_brackets(self, children):
        return children[0]

    def count_expr_conditional(self, children):
        bool_rule_name, count_if, count_then = children
        return CountExprConditional(bool_rule_name, count_if, count_then)

    def count_expr_add(self, children):
        left, right = children
        return CountExprAdd(left, right)

    def count_expr_subtract(self, children):
        left, right = children
        return CountExprSubtract(left, right)

    def count_expr_min(self, children):
        left, right = children
        return CountExprMin(left, right)

    def count_expr_max(self, children):
        left, right = children
        return CountExprMax(left, right)

    def count_expr_constant(self, children):
        return CountAtomConst(children[0].value)

    def count_expr_count_rule(self, children):
        return CountAtomCountRule(children[0])

    def bool_rule(self, children):
        name, body = children
        return BoolRule(name, body)

    def count_rule(self, children):
        name, body = children
        return CountRule(name, body)

    def rule(self, children):
        return children[0]

    def alphabet(self, children):
        return set(children)

    def crasp(self, children):
        bool_rules = [
            rule
            for rule in children[1:]
            if isinstance(rule, BoolRule)
        ]
        count_rules = [
            rule
            for rule in children
            if isinstance(rule, CountRule)
        ]
        return CRASP(children[0], bool_rules, count_rules)

class CRASPValidator(Walker):
    def __init__(self):
        self.defined_bool_rule_names = set()
        self.required_count_rule_names = set()
        self.defined_count_rule_names = set()
        self.in_count_rules = False

    def on_bool_rule_name(self, name : str, name_use : bool):
        if name_use:
            if name not in self.defined_bool_rule_names:
                raise SyntaxError(f"Rule {name} used before defined.")
        else:
            self.defined_bool_rule_names.add(name)

    def on_count_rule_name(self, name : str, name_use : bool):
        if name_use:
            if self.in_count_rules:
                if name not in self.defined_count_rule_names:
                    raise SyntaxError(f"Rule {name} used before defined.")
            else:
                self.required_count_rule_names.add(name)
        else:
            self.in_count_rules = True
            self.defined_count_rule_names.add(name)

    def on_end(self):
        undefined = (
            self.required_count_rule_names
            - self.defined_count_rule_names
        )
        if len(undefined) > 0:
            raise SyntaxError(f"Undefined count rules {undefined} used.")

def parse_crasp(prog : str) -> CRASP:
    tree = CRASP_PARSER.parse(prog)
    transformer = CRASPTransformer()
    prog = transformer.transform(tree)
    CRASPValidator().walk_crasp(prog)
    return prog

def parse_crasp_file(crasp_file : str) -> CRASP:
    with open(crasp_file) as f:
        return parse_crasp(f.read())

