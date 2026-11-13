# Markey agreement: three routes from the same 2LTL input

Language (n inputs, |Σ| = 2^(n+1) letters (p0, p1..pn)): the twin of the last
letter — same inputs, opposite p0 — does not occur earlier (BOS reads as the
all-zero letter). Nonempty for every n. 120 s budget per instance, median of 3
runs, serial on one MacBook Pro; after a failure the larger n of that solver are
skipped. Reproduce with `run.sh`.

- **A. Automatic translation** — `--one-variable --ltlf` export of the 2LTL file, then the solver.
  Times include the export (JVM start-up included).
- **B. Idealized translation** — hand-written LTLf `OR_c (c ∧ N G ¬twin(c))`,
  2^(n+1)−1 disjuncts of size O(n) (`scripts/markey_fair_ltl.py`). Not produced by any
  tool; it stands in for a perfect 2LTL→LTL translator. Times are the solver alone.
- **C. Direct circuit** — 2LTL → AIGER → ABC `scleanup; dc2; pdr`. Times include
  JVM start-up (≈0.45 s).

Equivalence: for n = 1, 2 the definition, formula B, export A and (on a sample)
the jar's own 2LTL evaluator agree on every word up to length 4 (340 and 4,680
words, both accepting and rejecting). Every Aalta witness was replayed and
accepted (A: through the jar; B: against the definition). All decided verdicts
are `nonempty`, as expected.

| n | \|Σ\| | A: export size | A: Aalta | A: Aalta -blsc | A: Lisa | B: size | B: Aalta | B: Aalta -blsc | B: Lisa | C: circuit | C: latches / ANDs |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 4 | 3.7 KB | 0.41 | 0.42 | 0.46 | 101 B | 0.006 | 0.008 | 0.04 | 0.52 | 4 / 29 |
| 2 | 8 | 32 KB | 0.46 | 0.48 | 0.63 | 317 B | 0.007 | 0.006 | 0.03 | 0.53 | 8 / 84 |
| 3 | 16 | 301 KB | 0.74 | 0.87 | 1.20 | 849 B | 0.007 | 0.008 | 0.05 | 0.49 | 16 / 199 |
| 4 | 32 | 2.8 MB | 3.09 | 3.65 | MONA abort | 2.1 KB | 0.009 | 0.009 | 0.07 | 0.54 | 32 / 438 |
| 5 | 64 | 26 MB | 59.0 | timeout | – | 5.0 KB | 0.016 | 0.016 | 0.16 | 0.56 | 64 / 933 |
| 6 | 128 | > 64 MB, export refused | – | – | – | 11 KB | 0.043 | 0.052 | 0.41 | 0.66 | 128 / 1,956 |
| 7 | 256 | – | – | – | – | 26 KB | 0.14 | 0.14 | 0.39 | 0.76 | 256 / 4,067 |
| 8 | 512 | – | – | – | – | 57 KB | 0.69 | 0.72 | 0.96 | 1.00 | 512 / 8,418 |
| 9 | 1,024 | | | | | 126 KB | 2.19 | 2.60 | MONA abort | 1.68 | 1,024 / 17,377 |
| 10 | 2,048 | | | | | 278 KB | 9.90 | 9.74 | – | 2.39 | 2,048 / 35,808 |
| 11 | 4,096 | | | | | 610 KB | 49.0 | 46.7 | – | 4.67 | 4,096 / 73,695 |
| 12 | 8,192 | | | | | 1.3 MB | timeout | timeout | – | 9.91 | 8,192 / 151,518 |

Times in seconds. The export only exists for n ≤ 8 in `examples/ltl`; B and C
were generated up to n = 12.

## Observations

1. **The automatic translation is the bottleneck.** Export size grows ≈ 9× per n
   (it enumerates all 2^(2(n+1)) valuations of the query facts, most of them
   inconsistent, and cannot share subformulas); at n = 6 it exceeds the 64 MB
   text limit and no LTL solver is ever called. The circuit decides the same
   2LTL input up to n = 12 in under 10 s.
2. **Even a perfect translation does not close the gap as n grows.** On formula
   B, Aalta is faster up to n = 8 (sub-second, and the circuit column carries
   ≈ 0.45 s of JVM start-up), but its time grows 3–5× per n (0.69 → 2.2 → 9.9 → 49 s) against
   ≈ 2.2× growth of the formula, and it times out at n = 12. The circuit grows
   at most ≈ 2× per n (1.0 → 1.7 → 2.4 → 4.7 → 9.9 s) — linear in |Σ| — and
   is faster from n = 9 on.
3. **Automata-based LTLf tools fail early.** Lisa (via MONA) aborts at n = 4 on
   A and at n = 9 on B, consistent with the 2^(2^n) lower bound on automata for
   this language.
4. **Variance.** Aalta default on A at n = 5 took 59 s here but timed out in an
   earlier single-repetition run under the same 120 s budget: it sits near the
   limit, and background load was not controlled.

## The paper's routes (`paper_routes.jsonl`, used for Table `tab:markey`)

Same inputs, 120 s, median of 3, 4 GB heap (`scripts/markey_paper_routes.py`).

| n | PVWAA-to-circuit (latches) | guard raised | LTL-to-circuit (latches) | DFA |
|---:|---:|---:|---:|---:|
| 1 | 0.59 (28) | 0.57 | 0.61 (42) | 0.57 |
| 2 | 0.60 (50) | 0.59 | 0.70 (142) | 2.41 |
| 3 | 0.63 (88) | 0.60 | 1.06 (530) | timeout |
| 4 | 0.63 (158) | 0.62 | 5.46 (2,070) | – |
| 5 | 0.67 (292) | 0.68 | timeout | – |
| 6 | 0.77 (554) | 0.77 | – | – |
| 7 | 0.94 (1,072) | 0.95 | – | – |
| 8 | size guard (500,000) | 1.26 (2,102) | – | – |
| 9 | – | 1.95 (4,156) | – | – |
| 10 | – | 3.30 (8,258) | – | – |
| 11 | – | 6.54 (16,456) | – | – |
| 12 | – | 13.7 (32,846) | – | – |

The DFA route needed > 400 s at n = 3 with the current jar; an older build
(results/1ltl_examples.csv, 2026-08-29) solved n ≤ 5. Unexplained regression,
to be investigated before publication.

## Lisa on its Spot route (`-nap 1000`, as in Figure 3), used for Table `tab:markey`

`A_export_lisa_spot.jsonl` (translated formula, times include the JVM export) and
`B_lisa_spot/records.jsonl` (hand-written formula, solver alone); 120 s, median of 3.

| n | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| translated | 0.70 | 0.75 | 7.04 | timeout | – | – | – | – | – | – |
| hand-written | 0.025 | 0.032 | 0.050 | 0.068 | 0.14 | 0.38 | 1.31 | 5.80 | 36.4 | timeout |

The earlier `A_export_lisa.jsonl` / `BC` Lisa columns use Lisa's default MONA
route (aborts at n = 4 translated, n = 9 hand-written).
