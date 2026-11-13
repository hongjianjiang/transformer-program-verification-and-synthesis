#!/usr/bin/env bash
# Figure 3 alphabet families (mono/since/slb) at sigma = 1000, 2000, 3000 on the six
# plotted approaches, same protocol as Figure 3: 900 s end-to-end per run, 4 GB heap,
# larger sigma skipped after a timeout/size limit within a family.
# Inputs come from gen_inputs.py, which reproduces the 27 existing sigma <= 256 inputs
# byte for byte. Expected verdicts: mono empty, since/slb nonempty.
#   Stage 1 (serial, uncontended): Direct + ABC (3 reps, median), Direct + NuSMV (BDD).
#   Stage 2 (two at a time): Aalta, Lisa (-nap 1000), Elimination + ABC, Elimination + NuSMV (BDD).
set -u
cd "$(dirname "$0")/../.."
O=results/alphabet_1k_3k_20260924
IN=$(cut -f3 $O/instances.tsv)
NUSMV=tmp/nusmv/NuSMV-2.7.1-macosx/bin/NuSMV

python3 scripts/direct_circuit.py $IN --timeout 900 --repetitions 3 --heap 4g \
  --reference $O/reference.jsonl --out $O/direct > $O/log_direct.txt 2>&1
python3 scripts/nusmv_circuit.py $IN --engine bdd --nusmv $NUSMV --timeout 900 --heap 4g \
  --reference $O/reference.jsonl --out $O/circuit_nusmv_bdd > $O/log_circuit_nusmv_bdd.txt 2>&1

( python3 scripts/ltlf_solver_baseline.py $IN --solver aalta --timeout 900 --repetitions 1 --witness \
    --skip-after-failure --reference $O/reference.jsonl --out $O/aalta.jsonl > $O/log_aalta.txt 2>&1
  python3 scripts/circuit_one_variable_baseline.py --manifest $O/instances.tsv --reference $O/reference.jsonl \
    --abc ../abc/abc --timeout 900 --repetitions 1 --heap 4g --skip-after-failure \
    --out $O/elim_abc.jsonl > $O/log_elim_abc.txt 2>&1 ) &
( python3 scripts/ltlf_solver_baseline.py $IN --solver lisa --lisa-args="-nap 1000" --timeout 900 --repetitions 1 \
    --skip-after-failure --reference $O/reference.jsonl --out $O/lisa.jsonl > $O/log_lisa.txt 2>&1
  python3 scripts/nusmv_baseline.py --manifest $O/instances.tsv --nusmv $NUSMV --engine bdd --timeout 900 \
    --repetitions 1 --heap 4g --skip-after-failure --reference $O/reference.jsonl \
    --out $O/elim_nusmv_bdd.jsonl > $O/log_elim_nusmv_bdd.txt 2>&1 ) &
wait
echo done
