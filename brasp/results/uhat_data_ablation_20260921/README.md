# Training-data and hard-gradient ablation

Eight unresolved languages; one fixed architecture per language (specification
depth + 1, two heads, two terms). Four data conditions: original sampling with
512 draws, and nested unique mixtures of 512, 2048, 8192 words plus the same
exhaustive short-word set. The mixture combines uniform and original generators,
plus a constructor allowing varied valid positive patterns for no2a,
balances sampled labels, and interleaves three length bands; exhausted short
bands contribute fewer examples. Per-length counts and exact datasets are saved.

Each condition has legacy and corrected hard-loss gradients, with paired seeds
0, 1, 2, 1500 steps and batch 64 on GPUs. All seeds run independently; there is
no test-based selection or early stopping. This fixes the number of optimizer
updates, not the number of epochs; larger datasets receive fewer passes.

The original 2000 longer-word evaluations are frozen from dataset seed 0.
A second fixed 2000-word unique diagnostic set broadens the sampling patterns.
Neither test set overlaps training lengths or is used to select a model.
Reported accuracies are finite-sample checks, not equivalence certificates.
The original512 control here uses batch 64, so it is not an exact replay of
the earlier full-batch sweep. Compare conditions within this experiment.
