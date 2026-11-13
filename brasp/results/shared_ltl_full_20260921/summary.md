# Shared LTL: full-suite validation

53/53 measurements recorded; 53 successful with expected verdicts.
Raw input/latch/AND counts match the archived direct circuit for 53/53 instances.

One repetition per instance, 4 GB JVM heap, 900-second limit, identical ABC settings. This is a validation run, not a paired runtime comparison. Initial development attempts are preserved under initial_attempt; all rows below use the corrected frozen compiler.

| Family | Parameter | Status | Total s | Compile s | Latches | ANDs |
|---|---:|---|---:|---:|---:|---:|
| dot | 100 | nonempty | 0.567 | 0.478 | 100 | 199 |
| dot | 400 | nonempty | 0.736 | 0.607 | 400 | 799 |
| dot | 800 | nonempty | 0.820 | 0.610 | 800 | 1599 |
| dot | 3200 | nonempty | 1.730 | 0.849 | 3200 | 6399 |
| Y | 10 | nonempty | 0.510 | 0.433 | 10 | 0 |
| Y | 12 | nonempty | 0.545 | 0.450 | 12 | 0 |
| Y | 14 | nonempty | 0.570 | 0.472 | 14 | 0 |
| Y | 16 | nonempty | 0.600 | 0.516 | 16 | 0 |
| Y | 20 | nonempty | 0.697 | 0.599 | 20 | 0 |
| Y | 100 | nonempty | 0.627 | 0.542 | 100 | 0 |
| Y | 500 | nonempty | 0.682 | 0.563 | 500 | 0 |
| Y | 8000 | nonempty | 3.657 | 0.929 | 8000 | 0 |
| no2a | 5 | nonempty | 0.515 | 0.440 | 6 | 2 |
| no2a | 8 | nonempty | 0.523 | 0.447 | 9 | 2 |
| no2a | 12 | nonempty | 0.515 | 0.439 | 13 | 2 |
| no2a | 16 | nonempty | 0.541 | 0.461 | 17 | 2 |
| no2a | 24 | nonempty | 0.531 | 0.457 | 25 | 2 |
| no2a | 1000 | nonempty | 0.708 | 0.619 | 1001 | 2 |
| no2a | 4000 | nonempty | 0.870 | 0.735 | 4001 | 2 |
| no2a | 64000 | nonempty | 4.090 | 2.731 | 64001 | 2 |
| mono | 8 | empty | 0.552 | 0.455 | 8 | 75 |
| mono | 9 | empty | 0.545 | 0.467 | 10 | 98 |
| mono | 10 | empty | 0.558 | 0.472 | 11 | 113 |
| mono | 11 | empty | 0.777 | 0.700 | 12 | 129 |
| mono | 12 | empty | 0.608 | 0.503 | 13 | 146 |
| mono | 16 | empty | 0.571 | 0.495 | 16 | 223 |
| mono | 32 | empty | 0.674 | 0.595 | 32 | 711 |
| mono | 128 | empty | 2.144 | 2.058 | 128 | 9015 |
| mono | 256 | empty | 11.535 | 11.441 | 256 | 34423 |
| since | 2 | nonempty | 0.486 | 0.409 | 4 | 19 |
| since | 4 | nonempty | 0.537 | 0.459 | 6 | 44 |
| since | 6 | nonempty | 0.498 | 0.420 | 8 | 69 |
| since | 8 | nonempty | 0.550 | 0.464 | 10 | 106 |
| since | 12 | nonempty | 0.538 | 0.456 | 14 | 176 |
| since | 16 | nonempty | 0.578 | 0.483 | 18 | 278 |
| since | 32 | nonempty | 0.652 | 0.539 | 34 | 814 |
| since | 128 | nonempty | 1.100 | 0.693 | 130 | 9406 |
| since | 256 | nonempty | 2.150 | 0.955 | 258 | 35198 |
| slb | 2 | nonempty | 0.492 | 0.414 | 3 | 5 |
| slb | 4 | nonempty | 0.511 | 0.434 | 5 | 30 |
| slb | 6 | nonempty | 0.527 | 0.450 | 8 | 56 |
| slb | 8 | nonempty | 0.523 | 0.439 | 9 | 80 |
| slb | 12 | nonempty | 0.512 | 0.431 | 14 | 151 |
| slb | 16 | nonempty | 0.590 | 0.494 | 17 | 228 |
| slb | 32 | nonempty | 0.605 | 0.497 | 33 | 716 |
| slb | 128 | nonempty | 1.080 | 0.687 | 129 | 9020 |
| slb | 256 | nonempty | 2.092 | 0.954 | 257 | 34428 |
| marks | 600 | nonempty | 0.837 | 0.650 | 600 | 2398 |
| marks | 1200 | nonempty | 1.089 | 0.753 | 1200 | 4798 |
| marks | 2400 | nonempty | 1.628 | 0.880 | 2400 | 9598 |
| same | 300 | nonempty | 0.740 | 0.595 | 307 | 1224 |
| same | 600 | nonempty | 0.896 | 0.675 | 607 | 2424 |
| same | 1200 | nonempty | 1.154 | 0.750 | 1207 | 4824 |
