# Larger-model UHAT results

- Architecture: specification depth + 2 layers, 4 heads per layer, 4 Boolean terms.
- Training protocol: 1,500 steps and 3 restarts per language on GPUs.
- Perfect training and longer-word accuracy: 17/26 (original selections: 18/26).
- Formally equivalent extracted programs: 9/26 (original selections: 12/26).
- Nonempty extracted programs: 25/26; `figure2_y__k-8` is empty.
- Summed training time: 18,459.1 s (5 h 7 min 39 s).
- Summed equivalence-checking time: 185.21 s.
- Summed learned/specification emptiness-checking time: 56.82 s.

The larger model newly solves and verifies `figure2_mono_word__sigma-4`, but loses the original exact/equivalent solutions for `figure2_dot__k-4` and `figure2_marks__k-4`. It also changes `figure2_since__sigma-2` and `figure2_slb__sigma-4` from equivalent to inequivalent while retaining perfect finite-data accuracy.
