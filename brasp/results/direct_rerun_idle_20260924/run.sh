#!/usr/bin/env bash
# Uncontended rerun of the 12 instances where Direct + ABC was slower than
# Elimination + ABC (with the cover) in the updated Figure 3: since sigma <= 32,
# slb 256, since/slb 1000-2000, since 3000. Nothing else runs on the machine.
#  1. Direct + ABC, same protocol as Figure 3: 900 s, median of 3, 4 GB heap,
#     unlimited cell budget, same jar (target/, b60cbf6f...).
#  2. Elimination + ABC with the cover on the same instances, same protocol as
#     its covered run (single repetition, matched budget/stack), since its
#     original timings were contended; this keeps the comparison like for like.
set -u
cd "$(dirname "$0")/../.."
O=results/direct_rerun_idle_20260924
python3 scripts/direct_circuit.py $(cut -f3 $O/instances.tsv) --timeout 900 --repetitions 3 --heap 4g \
  --keep-going --reference $O/reference.jsonl --out $O/direct > $O/log_direct.txt 2>&1
python3 scripts/circuit_one_variable_baseline.py --jar results/elim_cover_20260924/brasp-verification-cover-asciifix.jar \
  --manifest $O/instances.tsv --reference $O/reference.jsonl --abc ../abc/abc --timeout 900 --repetitions 1 \
  --heap 4g --stack 512m --budget 1000000000000 --out $O/elim_abc.jsonl > $O/log_elim_abc.txt 2>&1
echo done
