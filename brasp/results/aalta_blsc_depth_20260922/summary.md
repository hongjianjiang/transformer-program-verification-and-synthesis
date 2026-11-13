# Aalta default vs -blsc vs direct circuit (y_depth, dot_depth)

120 s budget per instance. Aalta: median of 3 runs, export + solve, witnesses replayed (all accepted). Direct: one run, compile + ABC. Times include JVM startup.

| Instance | Aalta default (CDLSC) | Aalta -blsc | Direct circuit |
|---|---:|---:|---:|
| y_depth__k-1 | 0.38 s | 0.39 s | 0.52 s |
| y_depth__k-5 | 0.39 s | 0.39 s | 0.45 s |
| y_depth__k-10 | 0.41 s | 0.39 s | 0.47 s |
| y_depth__k-15 | 0.40 s | 0.41 s | 0.46 s |
| y_depth__k-100 | 2.02 s | 0.51 s | 0.49 s |
| y_depth__k-500 | timeout | 6.74 s | 0.58 s |
| y_depth__k-2000 | skipped | timeout | 0.84 s |
| y_depth__k-8000 | skipped | skipped | 3.35 s |
| y_depth__k-32000 | skipped | skipped | 41.93 s |
| dot_depth__k-1 | 0.39 s | 0.39 s | 0.46 s |
| dot_depth__k-5 | 0.41 s | 0.41 s | 0.47 s |
| dot_depth__k-100 | timeout | 0.60 s | 0.51 s |
| dot_depth__k-200 | skipped | 0.80 s | 0.55 s |
| dot_depth__k-400 | skipped | 1.28 s | 0.60 s |
| dot_depth__k-800 | skipped | 2.75 s | 0.74 s |
| dot_depth__k-1200 | skipped | 4.83 s | 0.79 s |
| dot_depth__k-1600 | skipped | 7.40 s | 0.92 s |
| dot_depth__k-2400 | skipped | 15.15 s | 1.18 s |
| dot_depth__k-3200 | skipped | 25.63 s | 1.55 s |
