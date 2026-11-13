#!/usr/bin/env python3
"""L_eq(n): the label of the last position occurred at an earlier word position.

Letters are n-bit labels (the alphabet is all 2^n bit strings, tested with
`bit(k)`), so the 2LTL formula has size O(n):

    P( !bos@j  &  AND_k (bit(k)@i <-> bit(k)@j) )

BOS is excluded as a witness: every bit reads as 0 there, which would
otherwise accept the all-zero label trivially.

Any one-variable LTL formula (past or future) for L_eq(n) has size 2^Omega(n):
a formula of size s yields an automaton with at most 2^s states, while the
minimal DFA must remember the set of labels seen so far, 2^(2^n) states. The
same count bounds every sequential circuit by 2^n latches, so the gap is one
of formula size, not of circuit size.

    python3 scripts/eq_family.py --n 1 2 ... 14 --out DIR    # DIR/eq__n-<n>.ltl
    python3 scripts/eq_family.py --check 3                   # brute force vs definition
"""
from __future__ import annotations

import argparse
import itertools
from pathlib import Path


def conj(parts: list[str]) -> str:
    """Balanced binary conjunction, so nesting depth stays logarithmic."""
    if len(parts) == 1:
        return parts[0]
    mid = len(parts) // 2
    return f"({conj(parts[:mid])} & {conj(parts[mid:])})"


def eq_ltl(n: int) -> str:
    alphabet = ["".join(bits) for bits in itertools.product("01", repeat=n)]
    definitions = [f"f_{k} := bit({k})@i" for k in range(n)]
    bos = n
    definitions.append(f"f_{bos} := bos@i")
    same = [f"(!((f_{k}@i & !(f_{k}@j))) & !((!(f_{k}@i) & f_{k}@j)))" for k in range(n)]
    output = n + 1
    definitions.append(f"f_{output} := P({conj([f'!(f_{bos}@j)', *same])})")
    return ("logic past-strict\n"
            f"alphabet {' '.join(alphabet)}\n\n"
            + "\n".join(definitions)
            + f"\n\noutput := f_{output}@i\n"
            "evaluate at i = |w| (the final input position)\n")


def accepts(word: list[str]) -> bool:
    return len(word) >= 1 and word[-1] in word[:-1]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--n", type=int, nargs="+", default=[])
    parser.add_argument("--out", type=Path)
    parser.add_argument("--check", type=int, help="print words up to this length with their verdicts (n=2)")
    args = parser.parse_args()
    if args.out:
        args.out.mkdir(parents=True, exist_ok=True)
        for n in args.n:
            (args.out / f"eq__n-{n}.ltl").write_text(eq_ltl(n))
    if args.check:
        letters = ["00", "01", "10", "11"]
        for length in range(1, args.check + 1):
            for word in itertools.product(letters, repeat=length):
                print(" ".join(word), int(accepts(list(word))))


if __name__ == "__main__":
    main()
