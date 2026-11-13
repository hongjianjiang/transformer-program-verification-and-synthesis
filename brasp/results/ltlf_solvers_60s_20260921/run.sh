#!/usr/bin/env bash
# 60 s budget (export + solve), 3 repetitions per decided instance, witnesses replayed where available.
cd "$(dirname "$0")/../.."
O=results/ltlf_solvers_60s_20260921
INPUTS=$(cut -f3 $O/instances.tsv | sed "s|^|results/aalta_baseline_20260918/inputs/|")
for s in black lisa lydia; do
  python3 scripts/ltlf_solver_baseline.py $INPUTS --solver $s --timeout 60 --repetitions 3 --witness \
    --reference $O/aalta_reference.jsonl --out $O/records_$s.jsonl > $O/log_$s.txt 2>&1
done
