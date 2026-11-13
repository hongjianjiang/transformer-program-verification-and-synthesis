# Direct summary baseline: paired local pilot

24 inputs, four routes, three repetitions: **288 measurements**. All conclusive verdicts match the declared language. A 120-second total deadline applies to each run. Route order is randomized within repetitions; the runner is serial, but background machine load is uncontrolled.

| Route | Solved instances | Size refusals | Other outcomes |
|---|---:|---:|---:|
| support | 21/24 | 3 | 0 |
| realizable | 24/24 | 0 | 0 |
| direct-support | 21/24 | 3 | 0 |
| direct-realizable | 24/24 | 0 | 0 |

## Parameter 32 (realizability reduction enabled)

Raw counts are before ABC preprocessing; times include JVM startup, translation, preprocessing, and PDR.

| Family | PVWAA latches / ANDs | Direct latches / ANDs | PVWAA median s | Direct median s |
|---|---:|---:|---:|---:|
| dot | 336 / 1106 | 32 / 63 | 0.640 | 0.489 |
| Y | 66 / 0 | 32 / 0 | 0.560 | 0.452 |
| no2a | 82 / 40 | 33 / 2 | 0.582 | 0.489 |
| slb | 130 / 3111 | 33 / 716 | 0.646 | 0.530 |
| mono | 192 / 3152 | 32 / 711 | 0.874 | 0.618 |
| since | 133 / 3243 | 34 / 814 | 0.640 | 0.546 |
| marks | 326 / 1220 | 32 / 126 | 0.661 | 0.536 |
| same | 335 / 1268 | 39 / 152 | 0.647 | 0.505 |

The geometric mean of paired PVWAA/direct total-time ratios is **1.22x**; the direct route is faster on **24/24** median comparisons. These mostly short runs do not establish a robust large-instance speedup.

## Interpretation

The direct compiler matches the reduced PVWAA route on completion and uses fewer raw latches. This demonstrates that an explicit PVWAA stage is not necessary to obtain the summary-based verification benefits on this suite. Both support-only routes encounter size refusals; symbol-correlation reduction is useful in either formulation.

The compiler differences include temporal-only memory, no dual-state duplication, reachable-definition traversal, and sharing of equal temporal subformulas. The results therefore do not establish that the PVWAA formalism inherently requires larger circuits. An optimized PVWAA backend could adopt analogous simplifications.

See saved logs for post-ABC statistics; environment.json and source_snapshot.tar.gz preserve build provenance. This pilot does not overwrite the paper measurements.
