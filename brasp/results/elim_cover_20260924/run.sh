#!/usr/bin/env bash
# The four elimination-based Figure 3 approaches with the realizability cover in
# TwoLtlToOneVariable (brasp-verification-cover.jar, built from the working tree;
# see jar.sha256), on mono/since/slb at every Figure 3 sigma plus 1000/2000/3000.
# Protocol as in Figure 3: 900 s end-to-end, one run, 4 GB heap, larger sigma
# skipped after a timeout/size limit within a family, verdicts checked against
# the reference. The four approaches run as four concurrent lanes (4 physical
# cores; each lane is serial, smallest first), alongside the Direct + NuSMV sigma
# job, so timings include contention.
set -u
cd "$(dirname "$0")/../.."
O=results/elim_cover_20260924
J=$O/brasp-verification-cover.jar
NUSMV=tmp/nusmv/NuSMV-2.7.1-macosx/bin/NuSMV
IN=$(cut -f3 $O/instances.tsv)
( python3 scripts/ltlf_solver_baseline.py $IN --jar $J --solver aalta --timeout 900 --repetitions 1 --witness \
    --skip-after-failure --reference $O/reference.jsonl --out $O/aalta.jsonl > $O/log_aalta.txt 2>&1 ) &
( python3 scripts/circuit_one_variable_baseline.py --jar $J --manifest $O/instances.tsv --reference $O/reference.jsonl \
    --abc ../abc/abc --timeout 900 --repetitions 1 --heap 4g --skip-after-failure \
    --out $O/elim_abc.jsonl > $O/log_elim_abc.txt 2>&1 ) &
( python3 scripts/ltlf_solver_baseline.py $IN --jar $J --solver lisa --lisa-args="-nap 1000" --timeout 900 --repetitions 1 \
    --skip-after-failure --reference $O/reference.jsonl --out $O/lisa.jsonl > $O/log_lisa.txt 2>&1 ) &
( python3 scripts/nusmv_baseline.py --jar $J --manifest $O/instances.tsv --nusmv $NUSMV --engine bdd --timeout 900 \
    --repetitions 1 --heap 4g --skip-after-failure --reference $O/reference.jsonl \
    --out $O/elim_nusmv_bdd.jsonl > $O/log_elim_nusmv_bdd.txt 2>&1 ) &
wait
echo done
