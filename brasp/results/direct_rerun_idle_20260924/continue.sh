#!/usr/bin/env bash
# since sigma=3000 dropped from the rerun at the user's request. Wait for
# since 2000 to finish, stop run.sh before it starts since 3000 (killing the
# harness and any solver it spawned, so no orphan keeps running), then finish
# Direct + ABC on slb 1000/2000 and run Elimination + ABC on the other 11.
set -u
cd "$(dirname "$0")/../.."
O=results/direct_rerun_idle_20260924
until grep -q "since_same_letter__sigma-2000" $O/direct/records.jsonl 2>/dev/null; do sleep 5; done
pkill -f "direct_rerun_idle_20260924/run.sh"; pkill -f "scripts/direct_circuit.py"
sleep 2; pkill -f "brasp.CircuitStudy.*since_same_letter__sigma-3000"; pkill -f "abc -c read_aiger /var/folders/.*/direct-circuit-"
sleep 2
python3 scripts/direct_circuit.py $(cut -f3 $O/instances_rest_direct.tsv) --timeout 900 --repetitions 3 --heap 4g \
  --keep-going --reference $O/reference.jsonl --out $O/direct >> $O/log_direct.txt 2>&1
python3 scripts/circuit_one_variable_baseline.py --jar results/elim_cover_20260924/brasp-verification-cover-asciifix.jar \
  --manifest $O/instances_no_since3000.tsv --reference $O/reference.jsonl --abc ../abc/abc --timeout 900 --repetitions 1 \
  --heap 4g --stack 512m --budget 1000000000000 --out $O/elim_abc.jsonl > $O/log_elim_abc.txt 2>&1
echo done
