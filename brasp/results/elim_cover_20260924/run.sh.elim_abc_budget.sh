#!/usr/bin/env bash
# Elimination + ABC with the cover at sigma = 1000/2000/3000, rerun with the same
# budget and stack as the direct route (-Dpvwaa.maxTotalWork=10^12, -Xss512m).
# The first attempt used the backend's default budget of 500000 cell-symbol
# evaluations and was refused at sigma=1000 (4006 cells x 1001 symbols) before
# ABC ran: superseded_budget/. Same protocol otherwise.
set -u
cd "$(dirname "$0")/../.."
O=results/elim_cover_20260924
python3 scripts/circuit_one_variable_baseline.py --jar $O/brasp-verification-cover-asciifix.jar \
  --manifest $O/instances_1k3k.tsv --reference $O/reference.jsonl --abc ../abc/abc --timeout 900 \
  --repetitions 1 --heap 4g --stack 512m --budget 1000000000000 --skip-after-failure \
  --out $O/elim_abc_1k3k.jsonl > $O/log_elim_abc_1k3k.txt 2>&1
echo done
