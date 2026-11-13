#!/usr/bin/env bash
# Rerun the two Figure 3 failures that were drawn as "size limit (out of memory)"
# although their records say only status=error: Lisa on no2a k=24 and Aalta on
# lms n=12. Same harness and flags as the original runs, 900 s; the solver runs
# through measure.sh, which logs its exit status and peak memory (/usr/bin/time -l).
# Waits for the Direct + NuSMV sigma run (pid given as $1) and the covered
# elimination run to avoid memory contention.
set -u
cd "$(dirname "$0")/../.."
O=results/failure_audit_20260924
while [ -n "${1:-}" ] && kill -0 "$1" 2>/dev/null; do sleep 30; done
# ...and for the elimination-with-cover lanes, so memory is not contended.
while pgrep -f "elim_cover_20260924/run.sh" > /dev/null; do sleep 30; done
MEASURE_OUT=$PWD/$O/lisa_no2a24.time python3 scripts/ltlf_solver_baseline.py \
  results/aalta_baseline_20260918/inputs/y_depth__no_two_a__k-24.ltl --solver lisa --bin $O/lisa_bin \
  --lisa-args="-nap 1000" --timeout 900 --repetitions 1 --out $O/lisa_no2a24.jsonl > $O/log_lisa_no2a24.txt 2>&1
MEASURE_OUT=$PWD/$O/aalta_lms12.time python3 scripts/ltlf_solver_baseline.py \
  results/figure_direct_20260922/lms_inputs/lms_blocks__n-12.ltl --solver aalta --aaltaf $O/aaltaf \
  --timeout 900 --repetitions 1 --witness --out $O/aalta_lms12.jsonl > $O/log_aalta_lms12.txt 2>&1
echo done
