# C-RASP Synthesis and Verification

Use the [shared workspace CLI](../README.md) for learning, evaluation, translation,
and verification: `../rasp crasp --help`.

## Introduction

This project provides tools for learning, synthesizing, and verifying C-RASP (Constraint-based Regular Automata Synthesis Programs) using simulated annealing and other stochastic local search techniques. C-RASP programs describe automata with constraints and are used for program synthesis, verification, and optimization. The code is written in Scala and uses the sbt build system.

---

## Quickstart

1. **Enter the project:**
   ```sh
   cd rasp-learning
   ```
2. **Compile the code:**
   ```sh
   sbt compile
   ```
3. **Run the Scala REPL:**
   ```sh
   sbt console
   ```

---

## Common Commands

### Membership Query
Test if a string is accepted by a C-RASP program:
```sh
sbt "runMain utils.TestProgram ./examples/tomita-2.crasp ab"
sbt "runMain utils.TestProgram ./cegis/tomita-2_optimize.crasp abab"
```

### Verify Equality
Check if two C-RASP programs are equivalent:
```sh
sbt "runMain utils.Verify equal ./cegis/dyck_optimize.crasp Out ./examples/dyck.crasp Out"
```

To see the Lustre program that this equality check verifies (without running
Kind2), use `translate_equal` with the same arguments. An optional fifth
argument writes it to a file instead of stdout:
```sh
sbt "runMain utils.Verify translate_equal ./cegis/dyck_optimize.crasp Out ./examples/dyck.crasp Out"
sbt "runMain utils.Verify translate_equal ./cegis/dyck_optimize.crasp Out ./examples/dyck.crasp Out dyck_equal.lus"
```

### Verify Acceptance
Check if a C-RASP program accepts a string:
```sh
sbt "runMain utils.Verify accept ./examples/tomita-2.crasp Out abab"
sbt "runMain utils.Verify accept ./cegis/tomita-2_optimize.crasp Out ab"
```

---

## Learning & Verification

### Passive Learning
Learn a C-RASP program from examples:
```sh
sbt "runMain pl \
  --examplesDir examples \
  --targets dyck \
  --minLen 2 --maxLen 30 \
  --nPosTrain 500 --nNegTrain 500 \
  --nPosEval 20 --nNegEval 20 \
  --iters 80000 \
  --out passive_results.csv"
```

### Minimization
Minimization a C-RASP program:
```sh
sbt "runMain optimize \
  --examplesDir examples \
  --targets starabstar \
  --minLen 1 --maxLen 100 \
  --nPosTrain 500 --nNegTrain 500 \
  --nPosEval 20 --nNegEval 20 \
  --iters 80000 \
  --out optimize_results1.csv"
```

### Constraint Learning
Learn a C-RASP program with constraints:
```sh
sbt "runMain cl \
  --examplesDir examples \
  --targets dyck --minLen 1 --maxLen 100 \
  --nPosTrain 500 --nNegTrain 500 \
  --maxRounds 30 \
  --iters 80000 \
  --out cl_results.csv"
```

---

## Translation to Lustre

The `rasp-translation/` package translates a C-RASP program into a Lustre
program, so it can be checked with [Kind2][kind2]. Each C-RASP rule becomes one
Lustre equation, position `i` in the word becomes timestep `i` in the stream,
and prefix counting (`#φ`) becomes a running sum over time.

Setup (from `rasp-translation/`):
```sh
poetry install
```

Verification commands need the path to Kind2 in a config file (see
`rasp-translation/config_example.toml`):
```sh
poetry run main --config=config_example.toml check examples/dyck.crasp D
poetry run main --config=config_example.toml equal examples/tomita-2.crasp Out examples/tomita-2_optimize.crasp Out
poetry run main --config=config_example.toml accept examples/tomita-2.crasp Out "a b"
```

To get the Lustre without running a verifier, use `translate`, which needs no
config. `--property=check` (the default) adds a check node asserting the output
holds at end of stream; `--property=raw` emits only the main node and its
helpers:
```sh
poetry run main translate examples/tomita-2.crasp Out
poetry run main translate examples/dyck.crasp D --property=raw --out=dyck.lus
```

`translate_compare` prints the Lustre program that the `equal`/`subset`/
`disjoint` checks verify: both programs translated side by side (nodes prefixed
`left_` and `right_`) plus a `__COMPARE__` node that runs them on the same
input stream and checks the comparison at end of stream. It also needs no
config:
```sh
poetry run main translate_compare examples/tomita-2.crasp Out examples/tomita-2_optimize.crasp Out
poetry run main translate_compare examples/tomita-2.crasp Out examples/tomita-2_optimize.crasp Out --comparison=subset --out=compare.lus
```

The same translations are available as a Python API, which takes a program as
source text, a `Path`, or an already parsed `CRASP`:
```python
from rasptranslation import crasp_to_lustre, crasp_to_lustre_property, Property

lustre = crasp_to_lustre(open("examples/dyck.crasp").read(), "D")
lustre = crasp_to_lustre(Path("examples/dyck.crasp"), "D", Property.RAW)

# for the alphabet encoding, check node name and eos/eternity ints as well
prop = crasp_to_lustre_property(Path("examples/dyck.crasp"), "D")
```

`compare_to_lustre`, `accept_to_lustre` and `robust_to_lustre` build the Lustre
for the other properties. See `rasp-translation/README.md` for details.

[kind2]: https://kind2-mc.github.io/kind2/

---

## Example Workflow: Learning a C-RASP Program

1. Prepare your example data in the `examples/` directory.
2. Run passive learning or constraint learning as shown above.
3. The output will be saved in a CSV file and/or a `.crasp` file in the appropriate output directory.
4. Use verification commands to check correctness or equivalence.

---

## Project Structure

- `cegis/` — C-RASP automata files
- `examples/` — Example automata and test cases
- `src/` — Scala source code
- `rasp-translation/` — C-RASP to Lustre translation and Kind2 verification (Python)
- `learned_*` — Output directories for learned automata

---

## License

See LICENSE for details.


