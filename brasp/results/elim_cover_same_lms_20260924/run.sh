#!/usr/bin/env bash
# The four elimination-based Figure 3 approaches with the realizability cover on
# L_same and L_lms, whose one-variable exports the cover changes (marks, dot, Y,
# no2a are byte-identical with and without it). Same protocol and jar as
# results/elim_cover_20260924: 900 s, one run, 4 GB heap, skip after a
# timeout/size limit, four concurrent lanes; Elimination + ABC with the direct
# route's budget and stack.
set -u
cd "$(dirname "$0")/../.."
O=results/elim_cover_same_lms_20260924
J=$O/brasp-verification-cover-asciifix.jar
NUSMV=tmp/nusmv/NuSMV-2.7.1-macosx/bin/NuSMV
IN=$(cut -f3 $O/instances.tsv)
( python3 scripts/ltlf_solver_baseline.py $IN --jar $J --solver aalta --timeout 900 --repetitions 1 --witness \
    --skip-after-failure --reference $O/reference.jsonl --out $O/aalta.jsonl > $O/log_aalta.txt 2>&1 ) &
( python3 scripts/ltlf_solver_baseline.py $IN --jar $J --solver lisa --lisa-args="-nap 1000" --timeout 900 --repetitions 1 \
    --skip-after-failure --reference $O/reference.jsonl --out $O/lisa.jsonl > $O/log_lisa.txt 2>&1 ) &
( python3 scripts/circuit_one_variable_baseline.py --jar $J --manifest $O/instances.tsv --reference $O/reference.jsonl \
    --abc ../abc/abc --timeout 900 --repetitions 1 --heap 4g --stack 512m --budget 1000000000000 \
    --skip-after-failure --out $O/elim_abc.jsonl > $O/log_elim_abc.txt 2>&1 ) &
( python3 scripts/nusmv_baseline.py --jar $J --manifest $O/instances.tsv --nusmv $NUSMV --engine bdd --timeout 900 \
    --repetitions 1 --heap 4g --skip-after-failure --reference $O/reference.jsonl \
    --out $O/elim_nusmv_bdd.jsonl > $O/log_elim_nusmv_bdd.txt 2>&1 ) &
wait
echo done