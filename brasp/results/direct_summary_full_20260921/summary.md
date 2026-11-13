# Full 53-instance summary-circuit comparison

318/318 measurements recorded; 53/53 pairs have all three successful repetitions.
Verdict mismatches: 0.

Both routes use realizability reduction, a 4 GB Java heap, the same ABC preprocessing and PDR script, and a 900-second per-run ceiling. Routes are paired and randomized within each repetition. The runner is serial; background host load is uncontrolled. Timings include JVM startup.

On complete pairs, the geometric mean PVWAA/direct runtime ratio is 1.41x. Direct is faster in 53/53 median comparisons.

| Family | Parameter | PVWAA status / reps | Direct status / reps | PVWAA s | Direct s | PVWAA latches | Direct latches |
|---|---:|---|---|---:|---:|---:|---:|
| dot | 100 | nonempty (3/3) | nonempty (3/3) | 0.777 | 0.540 | 1016 | 100 |
| dot | 400 | nonempty (3/3) | nonempty (3/3) | 1.017 | 0.635 | 4016 | 400 |
| dot | 800 | nonempty (3/3) | nonempty (3/3) | 1.366 | 0.731 | 8016 | 800 |
| dot | 3200 | nonempty (3/3) | nonempty (3/3) | 5.046 | 1.588 | 32016 | 3200 |
| Y | 10 | nonempty (3/3) | nonempty (3/3) | 0.551 | 0.501 | 22 | 10 |
| Y | 12 | nonempty (3/3) | nonempty (3/3) | 0.569 | 0.497 | 26 | 12 |
| Y | 14 | nonempty (3/3) | nonempty (3/3) | 0.587 | 0.488 | 30 | 14 |
| Y | 16 | nonempty (3/3) | nonempty (3/3) | 0.549 | 0.495 | 34 | 16 |
| Y | 20 | nonempty (3/3) | nonempty (3/3) | 0.588 | 0.527 | 42 | 20 |
| Y | 100 | nonempty (3/3) | nonempty (3/3) | 0.609 | 0.508 | 202 | 100 |
| Y | 500 | nonempty (3/3) | nonempty (3/3) | 0.814 | 0.569 | 1002 | 500 |
| Y | 8000 | nonempty (3/3) | nonempty (3/3) | 4.147 | 3.606 | 16002 | 8000 |
| no2a | 5 | nonempty (3/3) | nonempty (3/3) | 0.595 | 0.493 | 28 | 6 |
| no2a | 8 | nonempty (3/3) | nonempty (3/3) | 0.686 | 0.517 | 34 | 9 |
| no2a | 12 | nonempty (3/3) | nonempty (3/3) | 0.570 | 0.489 | 42 | 13 |
| no2a | 16 | nonempty (3/3) | nonempty (3/3) | 0.565 | 0.488 | 50 | 17 |
| no2a | 24 | nonempty (3/3) | nonempty (3/3) | 0.614 | 0.491 | 66 | 25 |
| no2a | 1000 | nonempty (3/3) | nonempty (3/3) | 0.832 | 0.607 | 2018 | 1001 |
| no2a | 4000 | nonempty (3/3) | nonempty (3/3) | 1.174 | 0.749 | 8018 | 4001 |
| no2a | 64000 | nonempty (3/3) | nonempty (3/3) | 7.892 | 3.899 | 128018 | 64001 |
| mono | 8 | empty (3/3) | empty (3/3) | 0.650 | 0.535 | 48 | 8 |
| mono | 9 | empty (3/3) | empty (3/3) | 0.648 | 0.541 | 55 | 10 |
| mono | 10 | empty (3/3) | empty (3/3) | 0.658 | 0.535 | 61 | 11 |
| mono | 11 | empty (3/3) | empty (3/3) | 0.669 | 0.533 | 67 | 12 |
| mono | 12 | empty (3/3) | empty (3/3) | 0.661 | 0.543 | 73 | 13 |
| mono | 16 | empty (3/3) | empty (3/3) | 0.686 | 0.546 | 96 | 16 |
| mono | 32 | empty (3/3) | empty (3/3) | 0.933 | 0.662 | 192 | 32 |
| mono | 128 | empty (3/3) | empty (3/3) | 8.773 | 3.289 | 768 | 128 |
| mono | 256 | empty (3/3) | empty (3/3) | 86.251 | 21.446 | 1536 | 256 |
| since | 2 | nonempty (3/3) | nonempty (3/3) | 0.547 | 0.469 | 13 | 4 |
| since | 4 | nonempty (3/3) | nonempty (3/3) | 0.515 | 0.456 | 21 | 6 |
| since | 6 | nonempty (3/3) | nonempty (3/3) | 0.533 | 0.465 | 29 | 8 |
| since | 8 | nonempty (3/3) | nonempty (3/3) | 0.544 | 0.463 | 37 | 10 |
| since | 12 | nonempty (3/3) | nonempty (3/3) | 0.545 | 0.474 | 53 | 14 |
| since | 16 | nonempty (3/3) | nonempty (3/3) | 0.623 | 0.497 | 69 | 18 |
| since | 32 | nonempty (3/3) | nonempty (3/3) | 0.661 | 0.539 | 133 | 34 |
| since | 128 | nonempty (3/3) | nonempty (3/3) | 1.245 | 0.993 | 517 | 130 |
| since | 256 | nonempty (3/3) | nonempty (3/3) | 2.831 | 2.090 | 1029 | 258 |
| slb | 2 | nonempty (3/3) | nonempty (3/3) | 0.517 | 0.472 | 10 | 3 |
| slb | 4 | nonempty (3/3) | nonempty (3/3) | 0.522 | 0.449 | 18 | 5 |
| slb | 6 | nonempty (3/3) | nonempty (3/3) | 0.537 | 0.459 | 27 | 8 |
| slb | 8 | nonempty (3/3) | nonempty (3/3) | 0.550 | 0.469 | 34 | 9 |
| slb | 12 | nonempty (3/3) | nonempty (3/3) | 0.612 | 0.560 | 51 | 14 |
| slb | 16 | nonempty (3/3) | nonempty (3/3) | 0.617 | 0.568 | 66 | 17 |
| slb | 32 | nonempty (3/3) | nonempty (3/3) | 0.764 | 0.647 | 130 | 33 |
| slb | 128 | nonempty (3/3) | nonempty (3/3) | 1.342 | 1.161 | 514 | 129 |
| slb | 256 | nonempty (3/3) | nonempty (3/3) | 2.804 | 2.086 | 1026 | 257 |
| marks | 600 | nonempty (3/3) | nonempty (3/3) | 1.554 | 0.745 | 6006 | 600 |
| marks | 1200 | nonempty (3/3) | nonempty (3/3) | 2.558 | 0.929 | 12006 | 1200 |
| marks | 2400 | nonempty (3/3) | nonempty (3/3) | 5.160 | 1.443 | 24006 | 2400 |
| same | 300 | nonempty (3/3) | nonempty (3/3) | 0.990 | 0.642 | 3015 | 307 |
| same | 600 | nonempty (3/3) | nonempty (3/3) | 1.408 | 0.755 | 6015 | 607 |
| same | 1200 | nonempty (3/3) | nonempty (3/3) | 2.676 | 1.032 | 12015 | 1207 |

Raw latch counts precede ABC simplification. summary.json also records post-ABC sizes where available. These results compare implementations, not an inherent necessity or disadvantage of the PVWAA formalism. The direct route uses temporal-only memory and avoids dual-state duplication; a PVWAA compiler could adopt analogous simplifications. Source inputs, their hashes, the frozen jar, logs and protocol are preserved in this directory.
