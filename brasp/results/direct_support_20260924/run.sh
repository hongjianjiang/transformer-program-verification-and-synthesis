#!/usr/bin/env bash
# Ablation: Direct + ABC without the realizability reduction (route direct-support:
# every support cell gets a register), on the 77 Figure 3 instances plus the
# sigma = 1000/2000/3000 mono/since/slb instances. Same protocol as the Figure 3
# direct-realizable run: 900 s end-to-end, median of 3 successful runs, 4 GB heap,
# unlimited cell budget, same jar. Unlike that run, a family's larger instances are
# skipped after a failure (as for the baselines), since support-only is expected to blow up.
# Waits for results/alphabet_1k_3k_20260924/run.sh so timings are uncontended.
set -u
cd "$(dirname "$0")/../.."
O=results/direct_support_20260924
while pgrep -f "alphabet_1k_3k_20260924/run.sh" > /dev/null; do sleep 60; done
python3 scripts/direct_circuit.py $(cut -f3 $O/instances.tsv) --route direct-support \
  --timeout 900 --repetitions 3 --heap 4g --reference $O/reference.jsonl --out $O/direct \
  > $O/log_direct.txt 2>&1
echo done
