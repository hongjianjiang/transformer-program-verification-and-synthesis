#!/usr/bin/env bash
# Restart of the three lanes that read the exported LTLf text (Aalta, Lisa,
# Elimination + NuSMV). The first attempt crashed at mono sigma=128: LtlfExport
# kept non-ASCII letters such as 'ª' as proposition names, which the tokenizers
# reject. brasp-verification-cover-asciifix.jar = cover jar + ASCII-only names.
# Superseded partial records: superseded_nonascii_crash/. Same protocol as run.sh.
set -u
cd "$(dirname "$0")/../.."
O=results/elim_cover_20260924
J=$O/brasp-verification-cover-asciifix.jar
NUSMV=tmp/nusmv/NuSMV-2.7.1-macosx/bin/NuSMV
IN=$(cut -f3 $O/instances.tsv)
( python3 scripts/ltlf_solver_baseline.py $IN --jar $J --solver aalta --timeout 900 --repetitions 1 --witness \
    --skip-after-failure --reference $O/reference.jsonl --out $O/aalta.jsonl > $O/log_aalta.txt 2>&1 ) &
( python3 scripts/ltlf_solver_baseline.py $IN --jar $J --solver lisa --lisa-args="-nap 1000" --timeout 900 --repetitions 1 \
    --skip-after-failure --reference $O/reference.jsonl --out $O/lisa.jsonl > $O/log_lisa.txt 2>&1 ) &
( python3 scripts/nusmv_baseline.py --jar $J --manifest $O/instances.tsv --nusmv $NUSMV --engine bdd --timeout 900 \
    --repetitions 1 --heap 4g --skip-after-failure --reference $O/reference.jsonl \
    --out $O/elim_nusmv_bdd.jsonl > $O/log_elim_nusmv_bdd.txt 2>&1 ) &
wait
echo done
