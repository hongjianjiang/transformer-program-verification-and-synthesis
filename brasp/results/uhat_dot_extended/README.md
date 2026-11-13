# Ldot at k=16 and k=24

Two additional binary-alphabet tasks, using the existing Figure 2 language generator and training protocol.

| k | Layers | Heads per layer | Boolean terms | Training sampled lengths | Longer-word evaluation lengths |
|---|---|---|---|---|---|
| 16 | 18 | 4 | 4 | 16–68 | 69–136 |
| 24 | 26 | 4 | 4 | 24–100 | 101–200 |

Training includes all words through length 6 and the existing sampled examples. Each restart has 1,500 steps; up to three restarts are attempted, with the existing early exit on perfect training accuracy. The seed starts at 0. Training uses minibatches of 32 instead of the earlier full batches to bound memory. Evaluation also uses batches of 32, preserving predictions while bounding memory. The wrapper applies this evaluation batching without modifying the shared trainer.

Submitted as Slurm array 51023135, two concurrent RTX 8000 GPU tasks, each with a 24-hour limit and 16 GB host memory. See submission.json for cluster location. Training outputs go under runs/ and extracted programs under runs/programs/.

The specifications are checked against independent task predicates before results are used. Accuracy and formal verification results are pending.
