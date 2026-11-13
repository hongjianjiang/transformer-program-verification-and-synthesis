# Figure 2 family UHAT study

24 small-parameter languages and 192 architecture cells. The study uses k in {1,2,4,8} and sigma in {2,4}. `mono_word` repairs the BOS-induced empty control by comparing the final symbol only with earlier word symbols.

```sh
sbatch -p cpu -A cpu --qos=cpu --array=1-192%40 --output=results/uhat_table1_retrain_20260923/runs/logs/%A_%a.log scripts/uhat_sweep.slurm results/uhat_table1_retrain_20260923/grid.txt results/uhat_table1_retrain_20260923/runs
python3 scripts/uhat_sweep.py collect --dir results/uhat_table1_retrain_20260923/runs --out results/uhat_table1_retrain_20260923/sweep.csv
python3 scripts/uhat_sweep.py best --csv results/uhat_table1_retrain_20260923/sweep.csv --specs results/uhat_table1_retrain_20260923/specs --out results/uhat_table1_retrain_20260923/best.csv
python3 scripts/uhat_verify.py --csv results/uhat_table1_retrain_20260923/sweep.csv --specs results/uhat_table1_retrain_20260923/specs --learned results/uhat_table1_retrain_20260923/runs/programs --out results/uhat_table1_retrain_20260923/equivalence.csv
```
