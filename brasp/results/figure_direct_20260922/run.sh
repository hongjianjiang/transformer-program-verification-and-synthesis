#!/usr/bin/env bash
# Data for the scaling figure with the direct 2LTL -> circuit construction.
#  1. Direct construction on the 53 existing figure instances plus L_lms(n), n = 1..16:
#     900 s per run, median of 3 successful runs, no skipping, 4 GB heap.
#  2. L_lms baselines, same protocol as the other families in the figure:
#     Aalta 120 s x 3 with every timeout rerun at 900 s; Lisa (Spot route) 900 s x 3.
set -u
cd "$(dirname "$0")/../.."
O=results/figure_direct_20260922
python3 scripts/lms_blocks.py write --n $(seq 1 16) --out $O/lms_inputs
LMS=$(ls $O/lms_inputs/lms_blocks__n-*.ltl | sort -t- -k2 -n)
FIG=$(cut -f3 results/lisa_figure3_900s_20260921/instances.tsv)
{ cat results/lisa_figure3_900s_20260921/reference.jsonl
  for f in $LMS; do echo "{\"input\": \"$(basename $f)\", \"status\": \"nonempty\"}"; done; } > $O/reference.jsonl

python3 scripts/direct_circuit.py $FIG $LMS --timeout 900 --repetitions 3 --heap 4g --keep-going \
  --reference $O/reference.jsonl --out $O/direct > $O/log_direct.txt 2>&1

python3 scripts/ltlf_solver_baseline.py $LMS --solver aalta --timeout 120 --repetitions 3 --witness \
  --skip-after-failure --reference $O/reference.jsonl --out $O/lms_aalta_120.jsonl > $O/log_lms_aalta_120.txt 2>&1
RERUN=$(python3 -c "
import json
for l in open('$O/lms_aalta_120.jsonl'):
    r = json.loads(l)
    if r['status'] in ('timeout', 'skipped'): print('$O/lms_inputs/' + r['input'])")
[ -n "$RERUN" ] && python3 scripts/ltlf_solver_baseline.py $RERUN --solver aalta --timeout 900 --repetitions 3 \
  --witness --skip-after-failure --reference $O/reference.jsonl --out $O/lms_aalta_900.jsonl > $O/log_lms_aalta_900.txt 2>&1

python3 scripts/ltlf_solver_baseline.py $LMS --solver lisa --lisa-args="-nap 1000" --timeout 900 --repetitions 3 \
  --skip-after-failure --reference $O/reference.jsonl --out $O/lms_lisa_900.jsonl > $O/log_lms_lisa_900.txt 2>&1
echo done
