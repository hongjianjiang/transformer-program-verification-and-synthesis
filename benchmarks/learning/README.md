# Learning benchmarks: FLaRe

[FLaRe](https://github.com/rycolab/flare) (Butoi et al., ICLR 2025; MIT
license) is a formal-language *recognition* benchmark: 18 languages, each with
a 10,000-word training set (length ≤ 40) and fixed held-out splits:
`validation-short` (≤ 40), `validation-long` (≤ 80) and `test` (≤ 500, a
length-generalization split).

```sh
python3 benchmarks/learning/fetch_flare.py      # clone pinned commit, convert (~28 MB)
python3 benchmarks/learning/run_flare.py --languages first majority parity
```

`fetch_flare.py` writes `flare/<language>/{train,validation-short,...}.csv`
in the shared `+,word` / `-,word` format. Tokens are renamed to single letters
`a, b, c, ...` so both learners see the same alphabet (C-RASP requires
single-character symbols); `flare/<language>/symbols.json` maps them back. The
committed `flare/manifest.json` pins the source commit and records SHA-256
checksums of every generated file.

`run_flare.py` learns from the first `--train-size` training words and scores
each saved program on the held-out splits with `rasp <learner> score`,
appending one JSON record per run to `results/flare.jsonl`.

C-RASP rejects the empty word by definition. Four languages (`dyck-2-3`,
`even-pairs`, `repeat-01`, `unmarked-reversal`) contain it as a positive
example, so the runner drops empty words from C-RASP training and scoring
and records how many it dropped.

Expected outcomes from theory (B-RASP recognizes exactly the star-free
languages):

| Language | Class | Star-free | B-RASP expected |
| --- | --- | --- | --- |
| `first`, `repeat-01`, `even-pairs` | regular | yes | learnable |
| `parity`, `cycle-navigation`, `modular-arithmetic-simple` | regular | no | not expressible |
| all others | non-regular | no | not expressible |

A perfect held-out score is evidence, not proof; use `rasp ... verify
equivalent` against a reference program to establish equivalence.
