#!/usr/bin/env bash
# Elimination + ABC with the automaton code removed: 2LTL -> one-variable past
# LTL (TwoLtlToOneVariable, realizability cover) -> AIGER via DirectSummary
# (one latch per temporal subformula) -> ABC scleanup; dc2; pdr. All 102
# Figure 3 instances, 900 s, one run, 4 GB heap, 512 MB stack, cell budget
# 10^12 as for the direct route, larger instances skipped after a failure.
# Serial and uncontended. Jar: brasp-verification.jar (jar.sha256).
set -u
cd "$(dirname "$0")/../.."
O=results/elim_abc_direct_backend_20260924
python3 scripts/circuit_one_variable_baseline.py --jar $O/brasp-verification.jar --manifest $O/instances.tsv \
  --reference $O/reference.jsonl --abc ../abc/abc --timeout 900 --repetitions 1 --heap 4g --stack 512m \
  --skip-after-failure --out $O/elim_abc.jsonl > $O/log_elim_abc.txt 2>&1
echo done
