# same(1200): phase timing comparison

Three repetitions per route; all six runs correctly report nonempty. Same frozen compiler, 4 GB Java heap, binary alphabet encoding and ABC script. Sequential randomized pairs, fresh JVM per run, no warmup; host load uncontrolled. Times below are medians in seconds.

| Phase | Direct summary | Improved LTL |
|---|---:|---:|
| Input to 2LTL (common frontend) | 0.1638 | 0.1639 |
| 2LTL to one-variable LTL | 0.0000 | 0.1882 |
| Circuit construction | 0.2239 | 0.1563 |
| Translation + circuit (paired sum) | 0.2239 | 0.3464 |
| Compiler process including JVM startup | 0.6172 | 0.7373 |
| ABC process | 0.3679 | 0.3890 |
| Total wall time | 0.9855 | 1.1332 |

Both circuits contain 1,207 latches and 4,824 AND gates before ABC preprocessing. Individual phase medians need not sum to the median total. Internal phase timings exclude JVM startup and file output. Translation is in-memory to a shared LTL DAG; no text serialization or reparsing. The total includes all compiler and solver work.
