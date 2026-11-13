#!/usr/bin/env bash
# Extend the Figure 3 (exp-scaling-comparison) L_marks panel with two extra
# depths, n = 200 and n = 800, for all three methods (circuit, Aalta, Lisa).
# Same protocol as results/lisa_figure3_900s_20260921 and
# results/figure_direct_20260922: circuit 900 s x 3 reps / 4 GB heap;
# Aalta 120 s x 3 initial, timeouts rerun at 900 s; Lisa (Spot route,
# -nap 1000) 900 s x 3.
set -u
cd "$(dirname "$0")/../.."
O=results/marks_n200_n800_20260922
INPUTS="examples/brasp/families/marks__k-200.brasp examples/brasp/families/marks__k-800.brasp"
REF=$O/reference.jsonl
printf '%s\n' \
  '{"input": "marks__k-200.brasp", "status": "nonempty"}' \
  '{"input": "marks__k-800.brasp", "status": "nonempty"}' > $REF

echo "=== circuit ===" | tee $O/log.txt
python3 scripts/direct_circuit.py $INPUTS --timeout 900 --repetitions 3 --heap 4g --keep-going \
  --reference $REF --out $O/direct >> $O/log.txt 2>&1

echo "=== aalta 120s ===" | tee -a $O/log.txt
python3 scripts/ltlf_solver_baseline.py $INPUTS --solver aalta --timeout 120 --repetitions 3 \
  --witness --reference $REF --out $O/aalta_120.jsonl >> $O/log.txt 2>&1

RERUN=$(python3 -c "
import json
for l in open('$O/aalta_120.jsonl'):
    r = json.loads(l)
    if r['status'] == 'timeout': print('examples/brasp/families/' + r['input'])")
if [ -n "$RERUN" ]; then
  echo "=== aalta 900s rerun: $RERUN ===" | tee -a $O/log.txt
  python3 scripts/ltlf_solver_baseline.py $RERUN --solver aalta --timeout 900 --repetitions 3 \
    --witness --reference $REF --out $O/aalta_900.jsonl >> $O/log.txt 2>&1
fi

echo "=== lisa 900s ===" | tee -a $O/log.txt
python3 scripts/ltlf_solver_baseline.py $INPUTS --solver lisa --lisa-args="-nap 1000" --timeout 900 \
  --repetitions 3 --reference $REF --out $O/lisa_900.jsonl >> $O/log.txt 2>&1

echo done | tee -a $O/log.txt
