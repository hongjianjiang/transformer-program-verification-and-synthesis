# Figure 2 family UHAT study

3 small-parameter languages and 24 architecture cells. The study uses k in {1,2,4,8} and sigma in {2,4}. `mono_word` repairs the BOS-induced empty control by comparing the final symbol only with earlier word symbols.

```sh
sbatch -p cpu -A cpu --qos=cpu --array=1-24%40 --output=results/uhat_lms_20260922/runs/logs/%A_%a.log scripts/uhat_sweep.slurm results/uhat_lms_20260922/grid.txt results/uhat_lms_20260922/runs
python3 scripts/uhat_sweep.py collect --dir results/uhat_lms_20260922/runs --out results/uhat_lms_20260922/sweep.csv
python3 scripts/uhat_sweep.py best --csv results/uhat_lms_20260922/sweep.csv --specs results/uhat_lms_20260922/specs --out results/uhat_lms_20260922/best.csv
python3 scripts/uhat_verify.py --csv results/uhat_lms_20260922/sweep.csv --specs results/uhat_lms_20260922/specs --learned results/uhat_lms_20260922/runs/programs --out results/uhat_lms_20260922/equivalence.csv
```

## GPU run (2026-09-22)

The local CPU run (`train_local.sh`) was replaced by MPI Slurm job 51072498
(`gpu-rtx8000`, one GPU per cell, `--array=1-24%8`, same grid and protocol),
submitted from `slurm-submit.mpi-inf.mpg.de:~/llm_verification/experiments/uhat-lms-gpu-20260922`.
The source snapshot there (`source-snapshot.tar.gz`) has sha256
`a394e43c1b2ce3ebe05347b16fd656347f5bc67f02d5c918d3082c81b3cf26ee`.
