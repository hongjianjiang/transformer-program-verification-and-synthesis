#!/usr/bin/env bash
# Lisa forced onto its Spot route (-nap 1000; the default MONA route aborts at sigma=12) on the hand-written mono/since/slb instances of Figure 3 (alphabet sizes up to 256):
# 900 s budget (export + solve), one run per instance; verdicts checked against the
# expected verdicts of ltl_scaling_study.ltl_formulas (mono empty, since/slb nonempty).
#
# Programs run one at a time, smallest first within each language. Once a program fails
# (timeout, size limit, error), the larger programs of that language are recorded as
# "skipped" instead of run. Records already in log_lisa.txt are reused, so the script resumes.
cd "$(dirname "$0")/../.."
O=results/lisa_spot_alphabet_families_900s_20260921
python3 - "$O" <<'EOF'
import json, subprocess, sys
from pathlib import Path

out = Path(sys.argv[1])
log = out / "log_lisa.txt"
done = {}
if log.exists():
    for line in log.read_text().splitlines():
        if line.startswith("{"):
            record = json.loads(line)
            done[record["input"]] = record

failed = set()
records = []
for line in (out / "instances.tsv").read_text().splitlines():
    task, run_id, path = line.split("\t")
    family = task.split("__")[0]
    name = Path(path).name
    if name in done and done[name].get("status") != "skipped":
        record = done[name]
    elif family in failed:
        record = {"input": name, "solver": "lisa", "status": "skipped",
                  "reason": "a smaller program of this language already failed"}
    else:
        result = subprocess.run(
            [sys.executable, "scripts/ltlf_solver_baseline.py", path, "--solver", "lisa",
             "--timeout", "900", "--repetitions", "1", "--lisa-args=-nap 1000", "--reference", str(out / "reference.jsonl")],
            capture_output=True, text=True)
        lines = [l for l in result.stdout.splitlines() if l.startswith("{")]
        record = json.loads(lines[-1]) if lines else {
            "input": name, "solver": "lisa", "status": "error", "detail": result.stderr.strip().splitlines()[-3:]}
    if record["status"] not in {"empty", "nonempty", "skipped"}:
        failed.add(family)
    records.append(record)
    if name not in done:
        with log.open("a") as handle:
            handle.write(json.dumps(record) + "\n")
    print(json.dumps(record), flush=True)

(out / "records_lisa.jsonl").write_text("".join(json.dumps(r) + "\n" for r in records))
EOF
