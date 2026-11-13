#!/usr/bin/env bash
# Markey agreement: three routes from the same 2LTL input.
#   A. automatic 2LTL -> LTLf export (--one-variable --ltlf) -> Aalta (default, -blsc), Lisa
#   B. idealized translation: hand-written O(n 2^n) LTLf (scripts/markey_fair_ltl.py) -> same solvers
#   C. direct 2LTL -> circuit -> ABC
# 120 s budget per instance, median of 3 runs; after a failure the larger n of that
# solver are skipped. Run serially on one machine.
set -u
cd "$(dirname "$0")/../.."
O=results/markey_routes_20260922
INPUTS=$(ls examples/ltl/markey_agreement__n-*.ltl | sort -t- -k2 -n)
REF=$O/reference.jsonl
for f in $INPUTS; do echo "{\"input\": \"$(basename $f)\", \"status\": \"nonempty\"}"; done > $REF

# A: the automatic export
for mode in default blsc; do
  FLAGS=$([ $mode = blsc ] && echo "-blsc" || true)
  python3 scripts/ltlf_solver_baseline.py $INPUTS --solver aalta --aalta-args="$FLAGS" \
    --timeout 120 --repetitions 3 --witness --skip-after-failure \
    --reference $REF --out $O/A_export_aalta_$mode.jsonl > $O/log_A_aalta_$mode.txt 2>&1
done
python3 scripts/ltlf_solver_baseline.py $INPUTS --solver lisa \
  --timeout 120 --repetitions 3 --skip-after-failure \
  --reference $REF --out $O/A_export_lisa.jsonl > $O/log_A_lisa.txt 2>&1

# B + C: idealized LTLf and the direct circuit, n = 1..12
python3 scripts/markey_fair_ltl.py run --n 1 2 3 4 5 6 7 8 9 10 11 12 \
  --timeout 120 --repetitions 3 --out $O/BC > $O/log_BC.txt 2>&1
echo done

# The paper's routes on the same 2LTL inputs: PVWAA-to-circuit (with and without
# its 500,000 cell-symbol guard), LTL-to-circuit, explicit DFA.
python3 scripts/markey_paper_routes.py $O/BC/inputs --out $O/paper_routes.jsonl > $O/log_paper_routes.txt 2>&1

# Lisa on its Spot route (-nap 1000), as in the scaling figure.
python3 scripts/ltlf_solver_baseline.py $INPUTS --solver lisa --lisa-args="-nap 1000" \
  --timeout 120 --repetitions 3 --skip-after-failure \
  --reference $REF --out $O/A_export_lisa_spot.jsonl > $O/log_A_lisa_spot.txt 2>&1
python3 scripts/markey_fair_ltl.py run --n 1 2 3 4 5 6 7 8 9 10 11 12 --solvers lisa --lisa-args="-nap 1000" \
  --timeout 120 --repetitions 3 --out $O/B_lisa_spot > $O/log_B_lisa_spot.txt 2>&1

# Direct circuit at the paper's 4 GB heap (the BC run above used 16 GB).
python3 scripts/markey_fair_ltl.py run --n 1 2 3 4 5 6 7 8 9 10 11 12 --solvers direct_circuit --heap 4g \
  --timeout 120 --repetitions 3 --out $O/C_direct_4g > $O/log_C_direct_4g.txt 2>&1
