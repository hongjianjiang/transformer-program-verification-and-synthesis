#!/usr/bin/env bash
# LMS'02 Thm. 3.3 witness as a block language (scripts/lms_blocks.py), n = 1..16.
# Direct 2LTL -> circuit -> ABC; Aalta (default search, witnesses replayed) and
# Lisa (Spot route, -nap 1000, as in the scaling figure) on the --one-variable --ltlf
# export. 120 s per instance; baselines: median of 3; after a failure the larger n
# of that route are skipped. All instances are nonempty.
set -u
cd "$(dirname "$0")/../.."
O=results/lms_blocks_20260922
python3 scripts/lms_blocks.py write --n $(seq 1 16) --out $O/inputs
INPUTS=$(ls $O/inputs/lms_blocks__n-*.ltl | sort -t- -k2 -n)
for f in $INPUTS; do echo "{\"input\": \"$(basename $f)\", \"status\": \"nonempty\"}"; done > $O/reference.jsonl
python3 scripts/direct_circuit.py $INPUTS --timeout 120 --reference $O/reference.jsonl \
  --out $O/direct > $O/log_direct.txt 2>&1
python3 scripts/ltlf_solver_baseline.py $INPUTS --solver aalta --timeout 120 --repetitions 3 \
  --witness --skip-after-failure --reference $O/reference.jsonl --out $O/aalta.jsonl > $O/log_aalta.txt 2>&1
python3 scripts/ltlf_solver_baseline.py $INPUTS --solver lisa --lisa-args="-nap 1000" --timeout 120 \
  --repetitions 3 --skip-after-failure --reference $O/reference.jsonl --out $O/lisa.jsonl > $O/log_lisa.txt 2>&1
echo done
