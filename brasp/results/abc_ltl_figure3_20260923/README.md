# ABC one-variable LTL Figure 3 experiment

Pipeline: 2LTL/B-RASP input → one-variable past LTL by variable elimination →
future LTL → ordinary VWAA → one-latch-per-state AIGER circuit → ABC
`scleanup; dc2; pdr; print_status`.

The manifest and reference classifications are shared with the 77-case NuSMV
Figure 3 experiment. Each remaining case has one 900-second end-to-end run with
a 4 GB Java heap. Larger cases in a family are explicitly skipped after the
first timeout or size-limit failure, matching the other Figure 3 baselines.
The run shares the machine with the ongoing NuSMV SAT-BMC sweep, so timings may
include contention.

Reproduce from the repository root:

```sh
python3 scripts/circuit_one_variable_baseline.py \
  --manifest results/nusmv_figure3_20260923/instances.tsv \
  --reference results/nusmv_figure3_20260923/reference.jsonl \
  --abc ../abc/abc --timeout 900 --repetitions 1 \
  --skip-after-failure --resume \
  --out results/abc_ltl_figure3_20260923/records.jsonl
```
