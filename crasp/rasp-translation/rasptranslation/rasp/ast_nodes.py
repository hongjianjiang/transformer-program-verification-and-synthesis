
from dataclasses import dataclass
from typing import List, Optional, Set

@dataclass
class BoolExpr:
    pass

@dataclass
class BoolExprAnd(BoolExpr):
    left : BoolExpr
    right : BoolExpr

@dataclass
class BoolExprOr(BoolExpr):
    left : BoolExpr
    right : BoolExpr

@dataclass
class BoolExprNot(BoolExpr):
    expr : BoolExpr

@dataclass
class BoolAtom(BoolExpr):
    pass

@dataclass
class BoolAtomBoolRule(BoolAtom):
    bool_rule_name : str

@dataclass
class BoolRule:
    name : str
    body : BoolExpr

class CountExpr:
    pass

class CountAtom(CountExpr):
    pass

@dataclass
class CountRule:
    name : str
    body : CountExpr

@dataclass
class BoolAtomInitial(BoolAtom):
    char : str

@dataclass
class BoolAtomComparison(BoolAtom):
    strict : bool
    left : CountExpr
    right : CountExpr

@dataclass
class BoolAtomEqual(BoolAtom):
    left : CountExpr
    right : CountExpr

@dataclass
class BoolAtomPositionPeriod(BoolAtom):
    """Current position i is (i % period) == offset"""
    period : int
    offset : int

@dataclass
class CountAtomCounting(CountAtom):
    """Count num times bool_expr true
    If neither range_start nor range_end, then count all time.
    If range_start only, count position range_start steps back.
    If range_end also, count from range_start to range_end steps back."""
    bool_expr : BoolExpr
    range_start : Optional[int] = None
    range_end : Optional[int] = None

@dataclass
class CountExprConditional(CountExpr):
    bool_rule_name : str
    expr_then : CountExpr
    expr_else : CountExpr

@dataclass
class CountExprAdd(CountExpr):
    left : CountExpr
    right : CountExpr

@dataclass
class CountExprSubtract(CountExpr):
    left : CountExpr
    right : CountExpr

@dataclass
class CountExprMin(CountExpr):
    left : CountExpr
    right : CountExpr

@dataclass
class CountExprMax(CountExpr):
    left : CountExpr
    right : CountExpr

@dataclass
class CountAtomConst(CountAtom):
    value : int

@dataclass
class CountAtomCountRule(CountAtom):
    count_rule_name : str

@dataclass
class CRASP:
    alphabet : Set[str]
    bool_rules : List[BoolRule]
    count_rules : List[CountRule]

