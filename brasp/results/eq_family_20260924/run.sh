#!/usr/bin/env bash
# L_eq(n), n = 1..16 (scripts/eq_family.py): the last n-bit label occurred at an
# earlier word position. 2LTL size O(n); every one-variable LTL formula
# 2^Omega(n); every circuit >= 2^n latches. Six Figure 3 approaches, 900 s,
# 4 GB heap, larger n skipped after a timeout/size limit/crash.
#  - Direct + ABC (median of 3) and Direct + NuSMV: target/ jar, as in Figure 3.
#  - Aalta, Lisa, Elimination + ABC, Elimination + NuSMV: brasp-verification-eq.jar
#    = cover jar + (a) bit tests count as symbol-determined in the cover, and
#    (b) the LTLf export spells an all-bit-strings alphabet as m free
#    propositions b0..b(m-1) (no mutual exclusion) instead of 2^m letters.
#    Elimination + ABC uses the direct route's budget and stack.
# Two waves of three lanes (memory: each JVM may take 4 GB).
set -u
cd "$(dirname "$0")/../.."
O=results/eq_family_20260924
J=$O/brasp-verification-eq.jar
NUSMV=tmp/nusmv/NuSMV-2.7.1-macosx/bin/NuSMV
IN=$(cut -f3 $O/instances.tsv)
( python3 scripts/direct_circuit.py $IN --timeout 900 --repetitions 3 --heap 4g \
    --reference $O/reference.jsonl --out $O/direct > $O/log_direct.txt 2>&1 ) &
( python3 scripts/ltlf_solver_baseline.py $IN --jar $J --solver aalta --timeout 900 --repetitions 1 --witness \
    --skip-after-failure --reference $O/reference.jsonl --out $O/aalta.jsonl > $O/log_aalta.txt 2>&1 ) &
( python3 scripts/circuit_one_variable_baseline.py --jar $J --manifest $O/instances.tsv --reference $O/reference.jsonl \
    --abc ../abc/abc --timeout 900 --repetitions 1 --heap 4g --stack 512m --budget 1000000000000 \
    --skip-after-failure --out $O/elim_abc.jsonl > $O/log_elim_abc.txt 2>&1 ) &
wait
( python3 scripts/nusmv_circuit.py $IN --engine bdd --nusmv $NUSMV --timeout 900 --heap 4g \
    --reference $O/reference.jsonl --out $O/circuit_nusmv_bdd > $O/log_circuit_nusmv_bdd.txt 2>&1 ) &
( python3 scripts/ltlf_solver_baseline.py $IN --jar $J --solver lisa --lisa-args="-nap 1000" --timeout 900 --repetitions 1 \
    --skip-after-failure --reference $O/reference.jsonl --out $O/lisa.jsonl > $O/log_lisa.txt 2>&1 ) &
( python3 scripts/nusmv_baseline.py --jar $J --manifest $O/instances.tsv --nusmv $NUSMV --engine bdd --timeout 900 \
    --repetitions 1 --heap 4g --skip-after-failure --reference $O/reference.jsonl \
    --out $O/elim_nusmv_bdd.jsonl > $O/log_elim_nusmv_bdd.txt 2>&1 ) &
wait
echo done
