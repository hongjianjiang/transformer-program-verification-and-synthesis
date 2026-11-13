#!/usr/bin/env bash
# Direct + ABC and Direct + NuSMV on all 102 Figure 3 instances after the
# DirectSummary selector fix: a cell is selected by the letters inducing its
# symbol-determined pattern (shared symbol tests) instead of comparing every
# support coordinate, which made the circuit quadratic in sigma on the
# alphabet families. Same protocol as Figure 3: Direct + ABC 900 s, median of
# 3, 4 GB heap, cell budget 10^12, no skipping; Direct + NuSMV (BDD) 900 s,
# larger instances skipped after a failure. Runs after the Elimination + ABC
# rerun, serially, so timings are uncontended.
set -u
cd "$(dirname "$0")/../.."
O=results/direct_selector_fix_20260924
while pgrep -f "elim_abc_direct_backend_20260924/run.sh" > /dev/null; do sleep 30; done
IN=$(cut -f3 $O/instances.tsv)
python3 scripts/direct_circuit.py $IN --jar $O/brasp-verification.jar --timeout 900 --repetitions 3 --heap 4g \
  --keep-going --reference $O/reference.jsonl --out $O/direct > $O/log_direct.txt 2>&1
python3 scripts/nusmv_circuit.py $IN --jar $O/brasp-verification.jar --engine bdd \
  --nusmv tmp/nusmv/NuSMV-2.7.1-macosx/bin/NuSMV --timeout 900 --heap 4g \
  --reference $O/reference.jsonl --out $O/circuit_nusmv_bdd > $O/log_circuit_nusmv_bdd.txt 2>&1
echo done
