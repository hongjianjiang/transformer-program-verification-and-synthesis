# Shared LTL vs direct summary: paired pilot

144/144 measurements; verdict mismatches: 0.

Three repetitions per route on 24 instances; median total times include JVM startup. Same 4 GB heap, 900-second deadline, ABC script and AIG builder. Route order randomized within repetitions. Background host load was uncontrolled (including a Scala test run during part of this pilot); small timing differences are exploratory.

| Family | Parameter | Direct s | Shared LTL s | Direct latches | Shared LTL latches |
|---|---:|---:|---:|---:|---:|
| Y | 2 | 0.491 | 0.504 | 2 | 2 |
| Y | 8 | 0.568 | 0.511 | 8 | 8 |
| Y | 32 | 0.503 | 0.519 | 32 | 32 |
| dot | 2 | 0.469 | 0.544 | 2 | 2 |
| dot | 8 | 0.471 | 0.518 | 8 | 8 |
| dot | 32 | 0.585 | 0.593 | 32 | 32 |
| marks | 2 | 0.494 | 0.541 | 2 | 2 |
| marks | 8 | 0.722 | 0.776 | 8 | 8 |
| marks | 32 | 0.587 | 0.586 | 32 | 32 |
| mono | 2 | 0.485 | 0.510 | 2 | 2 |
| mono | 8 | 0.603 | 0.671 | 8 | 8 |
| mono | 32 | 0.672 | 1.104 | 32 | 32 |
| no2a | 2 | 0.449 | 0.510 | 3 | 3 |
| no2a | 8 | 0.476 | 0.523 | 9 | 9 |
| no2a | 32 | 0.482 | 0.526 | 33 | 33 |
| same | 2 | 0.506 | 0.553 | 9 | 9 |
| same | 8 | 0.682 | 0.719 | 15 | 15 |
| same | 32 | 0.545 | 0.677 | 39 | 39 |
| since | 2 | 0.480 | 0.524 | 4 | 4 |
| since | 8 | 0.680 | 0.746 | 10 | 10 |
| since | 32 | 0.605 | 0.687 | 34 | 34 |
| slb | 2 | 0.481 | 0.509 | 3 | 3 |
| slb | 8 | 0.692 | 0.790 | 9 | 9 |
| slb | 32 | 0.546 | 0.627 | 33 | 33 |

Complete pairs: 24. Geometric mean LTL/direct runtime ratio: 1.10x.

Latch counts precede ABC simplification. All LTL compilations assert zero query support. The LTL result is a shared DAG, not an expanded formula tree.
