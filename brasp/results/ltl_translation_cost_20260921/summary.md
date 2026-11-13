# 2LTL-to-LTL translation costs

Three repetitions per route and instance, sequential randomized paired runs. Same frozen compiler, 4 GB heap and ABC settings. All times are medians in seconds. Total includes the common frontend, JVM startup, file output and ABC. Internal translation timings cover in-memory elimination to a shared LTL DAG; no text serialization.

| Instance | LTL translation | Direct circuit construction | LTL circuit construction | Direct total | LTL total |
|---|---:|---:|---:|---:|---:|
| mono(256) | 9.337 | 16.403 | 0.327 | 16.978 | 10.224 |
| no2a(64000) | 0.804 | 1.237 | 1.206 | 3.086 | 3.951 |
| Y(8000) | 0.286 | 0.339 | 0.266 | 3.341 | 3.586 |
| marks(2400) | 0.246 | 0.294 | 0.196 | 1.507 | 1.628 |

Longer variable elimination does not imply a slower total route: specialization and simplification before circuit construction can offset the translation cost. Individual phase medians need not sum to median total. Background host load is uncontrolled.
