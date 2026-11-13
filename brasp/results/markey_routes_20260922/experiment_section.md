# Experiment section draft: why the direct circuit beats the LTL route

Numbers from `results/markey_routes_20260922/summary.md` (Markey) and
`results/aalta_blsc_depth_20260922/summary.md` (Yᵏ). Adjust notation to the paper.

---

### Circuit vs. LTL: a controlled comparison

Both routes start from the same 2LTL formula. The *LTL route* eliminates the
second variable to obtain a one-variable formula, mirrors it to future LTLf and
calls an LTLf satisfiability solver (Aalta [ref], with both its default CDLSC and
its BLSC search, and the automata-based Lisa [ref]). The *circuit route*
compiles the formula directly into an AIGER circuit and calls ABC's PDR [ref].

To separate the cost of the translation from the cost of solving, we evaluate
the LTL route twice on Markey's agreement family [Laroussinie–Markey–Schnoebelen,
LICS'02; Markey 2003]: once with our automatic translation (A) and once with a
hand-written formula (B) that stands in for a perfect translator. For this
language — the last letter's twin, with the same inputs p₁..pₙ and the opposite
output p₀, never occurs earlier — the smallest LTLf formula we know is
⋁_c (c ∧ N G ¬twin(c)), with 2ⁿ⁺¹−1 disjuncts of size O(n). No pure-future
formula can be polynomial, since any such formula has size 2^Ω(n) [LMS02]. Formula
B is therefore close to the best any 2LTL→LTL translation could produce. We checked
it against the 2LTL semantics on all words up to length 4 for n ≤ 2 and replayed
every solver witness.

**Results.** (Table X.) With the automatic translation, the LTL route fails
at n = 5 (Aalta with BLSC; the default search needs 59 s, close to the 120 s
limit) and cannot be attempted at n = 6. There the translated formula exceeds
64 MB, because it grows ≈ 9× per step of n. With the idealized formula, Aalta is
faster for n ≤ 8, where every route needs under a second. Its running time,
however, grows 3–5× per step of n against 2.2× for the formula, and it times out
at n = 12. Lisa aborts at n = 9. The circuit route solves every instance up to
n = 12 in under 10 s. Its time roughly doubles per step, i.e. it is linear in
|Σ| = 2ⁿ⁺¹, and it is faster than the idealized LTL route from n = 9 on.

### Why the circuit route scales better

**1. It never eliminates the second variable.** A strict-past subformula
O_j φ(i, j) becomes a *summary table*: one latch per realizable valuation of the
facts about position i that φ reads. For Markey's formula the single H-row has
one latch per letter c, meaning "twin(c) has not occurred yet". Of the
2^{2(n+1)} syntactically possible query valuations, only the 2ⁿ⁺¹ realizable
ones are kept (8 of 64 at n = 2). The one-variable translation must case-split
on the same valuations in the syntax. Because LTL formulas are trees, it cannot
share the resulting subformulas. This is where the exponential blow-up of the
LTL route arises: before any solver runs.

**2. The circuit is a symbolic automaton.** Every automaton for Markey's
language needs 2^{2ⁿ} states [LMS02]. The circuit represents that state space
with |Σ| = 2ⁿ⁺¹ latches, and it updates each latch with O(1) gates per step.
Tools that construct the automaton explicitly fail early (Lisa/MONA at n = 9,
even on the idealized formula). Search-based solvers avoid the construction but
pay superlinearly in formula size. The circuit hands this state space to PDR,
which reasons about it through inductive invariants rather than enumeration.

**3. Past operators are one-step recurrences.** Y, P, H and S each update from
the previous value and the current letter, so the circuit is a deterministic
monitor with no guessing. The LTL route must mirror the formula into future
operators, and the solver then searches over pending obligations. Nesting depth
shows the difference most clearly. For Yᵏa the circuit is a k-latch shift
register, decided at k = 32,000 in 42 s. Aalta handles the mirrored Xᵏa up to
k = 500 with BLSC (k = 100 with its default search) and times out at
k = 2,000, while it is still preprocessing the nested formula.

**Limits of the comparison.** The size lower bound alone does not separate the
routes, because the circuit is also exponential in n (linear in |Σ|). The
advantage lies in avoiding the translation and in the symbolic back end. For
small instances an LTL solver on a hand-optimized formula is faster, partly
because our times include JVM start-up (≈ 0.45 s). The idealized formula B is
not produced by any tool; with our automatic translation the gap is far larger.
All times come from a single machine without load control. Aalta's time at
n = 5 on the translated formula varied between runs because it lies near the
limit.
