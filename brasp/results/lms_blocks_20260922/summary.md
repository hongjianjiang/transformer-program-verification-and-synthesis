# LMS'02 Theorem 3.3 witness as a block language

Source: Laroussinie, Markey, Schnoebelen, *Temporal Logic with Forgettable Past*,
LICS 2002, Thm. 3.1/3.3. psi'_n = G N psi_n: any two positions that agree on
p_1..p_n agree on p_0. Every PLTL/LTL equivalent has size Omega(2^n), with
Omega(2^n) distinct subformulas, so DAG sharing does not help. Every Büchi
automaton needs Omega(2^(2^n)) states (footnote 4).

Flattening (`scripts/lms_blocks.py`): each valuation is a block of n+1 bits,
preceded by a separator, because over {0,1} alone the block boundaries would
need counting modulo n+1, which star-free 2LTL cannot express.

    words  (sep b_0 b_1 ... b_n)^+   over {b0, b1, sep},   bit k of a block = p_k
    L_n    every two blocks with equal b_1..b_n have equal b_0      (nonempty)

The strict-past 2LTL formula is O(n) (see the script's docstring). Checked
against a direct Python definition, using the jar's `--word` evaluator, on 120
words per n for n = 1, 2, 3: 0 disagreements. The words include 18/13/4
well-formed conflicting (rejected) words and 60+ malformed ones per n.

## Results (120 s per instance, `run.sh`)

| n | direct circuit (s) | latches / ANDs | Aalta (s) | LTLf export | Lisa, Spot route (s) |
|---:|---:|---:|---:|---:|---:|
| 1 | 0.64 | 12 / 42 | 0.68 | 1.7 KB | 0.96 |
| 2 | 0.61 | 18 / 72 | 0.67 | 4.3 KB | timeout |
| 3 | 0.52 | 28 / 130 | 0.81 | 11 KB | – |
| 4 | 0.50 | 46 / 244 | 0.76 | 27 KB | – |
| 5 | 0.56 | 80 / 470 | 0.88 | 66 KB | – |
| 6 | 0.72 | 146 / 920 | 1.22 | 159 KB | – |
| 7 | 0.84 | 276 / 1,818 | 2.09 | 378 KB | – |
| 8 | 1.20 | 534 / 3,612 | 4.72 | 887 KB | – |
| 9 | 1.09 | 1,048 / 7,198 | 16.7 | 2.1 MB | – |
| 10 | 1.62 | 2,074 / 14,368 | 58.4 | 4.7 MB | – |
| 11 | 2.86 | 4,124 / 28,706 | timeout | – | – |
| 12 | 4.69 | 8,222 / 57,380 | – | – | – |
| 13 | 13.4 | 16,416 / 114,726 | – | – | – |
| 14 | 32.4 | 32,802 / 229,416 | – | – | – |
| 15 | 64.0 | 65,572 / 458,794 | – | – | – |
| 16 | 157 s (over the limit, see below) | 131,110 / 917,548 | – | – | – |

All decided verdicts are nonempty, as expected. Direct circuit: one run per
instance. Aalta and Lisa: median of 3, larger n skipped after a failure.
Aalta witnesses: all 10 are accepted by both the jar's reference evaluator and
the definition (`aalta_witness_replay.jsonl`).

The direct circuit has 2^(n+1) + O(n) latches: one summary latch per block value,
as required by the doubly exponential automaton lower bound, encoded
symbolically. The export grows 2.3–2.5× per step of n, i.e. exponentially, as the
Omega(2^n) formula lower bound requires.

## Tooling issues found and fixed during this run

- **`aalta.jsonl` reports `witness_accepted: false` for every instance. This is
  a replay failure, not a wrong witness.** The replay went through
  `--boolean-automaton`, which rejects this formula shape ("Until operands must
  be Boolean combinations of named i/j references"). `aalta_baseline.verify` now
  falls back to the reference evaluator, and an error is no longer recorded as a
  rejection.
- **The direct run at n = 16 recorded `error`.** ABC segfaulted (exit 139)
  reading the AIG: its reader recurses along the logic depth (131,092 levels),
  which overflows the default 8 MB stack. `direct_circuit.py` now runs ABC with
  `ulimit -s 65520`. Rerun by hand with that stack: nonempty (counterexample at
  frame 17 = one block), 157 s of ABC plus ~11 s of compile. Over the 120 s
  limit, so under the protocol n = 16 is a timeout.
