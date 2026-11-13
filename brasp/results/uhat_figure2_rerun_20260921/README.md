# Figure 2 family UHAT study

26 small-parameter languages and 208 architecture cells. The study uses k in {1,2,4,8} and sigma in {2,4}. `mono_word` repairs the BOS-induced empty control by comparing the final symbol only with earlier word symbols.

```sh
sbatch -p cpu -A cpu --qos=cpu --array=1-208%40 --output=results/uhat_figure2_rerun_20260921/runs/logs/%A_%a.log scripts/uhat_sweep.slurm results/uhat_figure2_rerun_20260921/grid.txt results/uhat_figure2_rerun_20260921/runs
python3 scripts/uhat_sweep.py collect --dir results/uhat_figure2_rerun_20260921/runs --out results/uhat_figure2_rerun_20260921/sweep.csv
python3 scripts/uhat_sweep.py best --csv results/uhat_figure2_rerun_20260921/sweep.csv --specs results/uhat_figure2_rerun_20260921/specs --out results/uhat_figure2_rerun_20260921/best.csv
python3 scripts/uhat_verify.py --csv results/uhat_figure2_rerun_20260921/sweep.csv --specs results/uhat_figure2_rerun_20260921/specs --learned results/uhat_figure2_rerun_20260921/runs/programs --out results/uhat_figure2_rerun_20260921/equivalence.csv
```
