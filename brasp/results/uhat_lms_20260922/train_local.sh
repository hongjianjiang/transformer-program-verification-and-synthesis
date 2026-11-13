#!/usr/bin/env bash
# Train every grid cell locally (CPU), 4 at a time, with the Figure 2 protocol of
# scripts/uhat_sweep.slurm: 1500 steps, 3 restarts, full batch.
cd "$(dirname "$0")/../.."
O=results/uhat_lms_20260922
cat $O/grid.txt | xargs -P 4 -L 1 bash -c '
  spec=$0; layers=$1; heads=$2; terms=$3
  stem="$(basename "$spec" .brasp)__l${layers}_h${heads}_t${terms}"
  [ -f "'$O'/runs/$stem.json" ] && exit 0
  OMP_NUM_THREADS=2 python3 -u -m uhat.train --brasp "$spec" --layers "$layers" --heads "$heads" --terms "$terms" \
    --steps 1500 --restarts 3 --batch 0 --device cpu --quiet \
    --out "'$O'/runs/programs/$stem.brasp" --json-out "'$O'/runs/$stem.json" > "'$O'/runs/logs/$stem.log" 2>&1
  echo "$stem done: $(python3 -c "import json;d=json.load(open(\"'$O'/runs/$stem.json\"));print(d[\"train_accuracy\"],d[\"test_accuracy\"],d[\"seconds\"])" 2>/dev/null || echo failed)"'
echo all done
