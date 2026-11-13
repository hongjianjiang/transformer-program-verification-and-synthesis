#!/usr/bin/env bash
# Rerun of the instances refused by the one-variable translation's 2^22-node
# guard (lms 15/16, eq 16) with the guard lifted (-Delimination.cap=2^31-1),
# as the direct route's cell budget is. Same protocol otherwise. Waits for the
# direct reruns so timings are uncontended.
set -u
cd "$(dirname "$0")/../.."
O=results/elim_abc_direct_backend_20260924
while pgrep -f "direct_selector_fix_20260924/run.sh" > /dev/null; do sleep 30; done
python3 scripts/circuit_one_variable_baseline.py --jar $O/brasp-verification-cap.jar --manifest $O/instances_cap.tsv \
  --reference $O/reference.jsonl --abc ../abc/abc --timeout 900 --repetitions 1 --heap 4g --stack 512m \
  --skip-after-failure --out $O/elim_abc_cap.jsonl > $O/log_elim_abc_cap.txt 2>&1
echo done
