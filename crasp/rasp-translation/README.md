# C-RASP to Lustre Translator

A translator for C-RASP (Counting RASP) program property checking to Lustre.

## Setup

From project directory

```bash
poetry install
```

For verification, [Kind2][kind2] must be installed.

## Configure

See `config_example.toml` for an example config file. In particular, the path
to Kind2 needs configuring. E.g.

    kind2_executable = "/usr/bin/kind2"

## Run

```bash
poetry run main --config=config_example.toml equal examples/tomita-2.crasp Out examples/tomita-2_optimize.crasp Out
```

poetry run main --config=config_example.toml accept examples/tomita-2.crasp Out "a b"
poetry run main --config=config_example.toml accept examples/tomita-2_optimize.crasp Out "a b"

## Translate only

To get the Lustre without running a verifier, use `translate`. It needs no
config, as Kind2 is not called.

```bash
# Lustre with a check node asserting the output holds at end of stream
poetry run main translate examples/tomita-2.crasp Out

# just the main node and its helpers, no property
poetry run main translate examples/dyck.crasp D --property=raw --out=dyck.lus
```

`translate_compare` prints the Lustre program that the `equal`/`subset`/
`disjoint` checks verify: both programs translated side by side (nodes prefixed
`left_` and `right_`) plus a `__COMPARE__` node that runs them on the same
input stream and checks the comparison at end of stream. `--comparison`
defaults to `equal`:

```bash
poetry run main translate_compare examples/tomita-2.crasp Out examples/tomita-2_optimize.crasp Out
poetry run main translate_compare examples/tomita-2.crasp Out examples/tomita-2_optimize.crasp Out --comparison=subset --out=compare.lus
```

The same translations are available as a Python API:

```python
from rasptranslation import crasp_to_lustre, crasp_to_lustre_property, Property

lustre = crasp_to_lustre(open("examples/dyck.crasp").read(), "D")
lustre = crasp_to_lustre(Path("examples/dyck.crasp"), "D", Property.RAW)

# for the alphabet encoding, check node name, and eos/eternity ints as well
prop = crasp_to_lustre_property(Path("examples/dyck.crasp"), "D")
```

A program can be given as source text, a `Path`, or an already parsed `CRASP`.
`compare_to_lustre`, `accept_to_lustre` and `robust_to_lustre` build the Lustre
for the other properties (equality/subset/disjointness, word acceptance and
robustness). See `rasptranslation/api.py`.

[kind2]: https://kind2-mc.github.io/kind2/
