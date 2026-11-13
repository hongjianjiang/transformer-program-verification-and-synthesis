#!/usr/bin/env bash
# Aalta on y_depth and dot_depth with both of its search methods: the default
# CDLSC and -blsc. Same settings as the aalta baseline: 120 s budget
# (export + solve), median of 3 runs per decided instance, witnesses replayed.
# Within each family, instances run smallest first; after the first failure the
# larger ones are recorded as skipped.
cd "$(dirname "$0")/../.."
O=results/aalta_blsc_depth_20260922
INPUTS="$(ls examples/ltl/y_depth__k-*.ltl | sort -t- -k2 -n) $(ls examples/ltl/dot_depth__k-*.ltl | sort -t- -k2 -n)"
REF=results/lisa_figure3_900s_20260921/reference.jsonl
for mode in default blsc; do
  FLAGS=$([ $mode = blsc ] && echo "-blsc" || true)
  python3 scripts/ltlf_solver_baseline.py $INPUTS --solver aalta --aalta-args="$FLAGS" \
    --timeout 120 --repetitions 3 --witness --skip-after-failure \
    --reference $REF --out $O/records_$mode.jsonl > $O/log_$mode.txt 2>&1
done
# The direct 2LTL -> circuit route on the same inputs, for the side-by-side table.
python3 scripts/direct_circuit.py $INPUTS --timeout 120 --out $O/direct > $O/log_direct.txt 2>&1
