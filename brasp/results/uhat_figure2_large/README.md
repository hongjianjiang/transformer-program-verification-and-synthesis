# Figure 2 larger-model UHAT study

One larger model for each of the 26 languages in `results/uhat_figure2`: specification depth plus 2 layers, 4 heads per layer, and 4 Boolean terms. Training keeps the main study protocol of three restarts and 1,500 steps.

```sh
sbatch -p gpu-rtx8000 --gres=gpu:1 --array=1-26%4 --output=results/uhat_figure2_large/runs/logs/%A_%a.log scripts/uhat_sweep.slurm results/uhat_figure2_large/grid.txt results/uhat_figure2_large/runs
```
