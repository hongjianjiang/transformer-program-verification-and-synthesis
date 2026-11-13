#!/bin/bash
#SBATCH --partition=cpu
#SBATCH --account=cpu
#SBATCH --qos=cpu
#SBATCH --cpus-per-task=1
#SBATCH --mem=6G
#SBATCH --time=00:10:00
#SBATCH --job-name=study-emptiness
set -euo pipefail
cd "$SLURM_SUBMIT_DIR"
exec "${PYTHON:-python3}" -u scripts/real_uhat_emptiness.py \
  --out "$1" \
  --jar bin/brasp-verification.jar \
  --abc bin/abc \
  --index "$SLURM_ARRAY_TASK_ID" \
  --shards 8 \
  --timeout 120
