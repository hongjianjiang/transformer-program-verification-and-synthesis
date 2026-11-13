# RASP learning and verification

Use `./rasp` to learn, evaluate, translate, and verify **B-RASP** and **C-RASP**
from this workspace. The two implementations retain their own Scala versions
and internal APIs; the shared command line lives in `cli/rasp.py`.

Requirements: Python 3.11+, Java, and sbt. C-RASP translation and verification
also require `lark>=1.3.1` in the Python environment running `rasp`.
Verification requires ABC for B-RASP or Kind2 (with its SMT solver) for C-RASP.
No solver is required for learning or concrete evaluation.

```sh
./rasp --help
./rasp build all                 # optional: commands build automatically
./rasp brasp learn --help
./rasp crasp verify --help
```

Builds are cached under each project's `target/`. Changes to source or build
files trigger recompilation on the next Scala command. Build logs go to stderr;
program output and backend exit codes are not hidden by sbt. All user-supplied
input and output paths are relative to the caller's working directory. The
launcher itself can be invoked by absolute path from another directory.

## Learn from examples

Both languages accept the same dataset format: one `+,word` or `-,word` per
line, where `+,` and `-,` represent the empty word. Blank lines are ignored;
contradictory labels and unknown alphabet symbols are rejected.

```sh
./rasp brasp learn --data brasp/examples/synthesis/contains_a.csv \
  --alphabet a,b --out /tmp/contains_a.brasp --seed 42 --slots 3

./rasp crasp learn --data brasp/examples/synthesis/contains_a.csv \
  --alphabet a,b --out /tmp/contains_a.crasp --seed 42 --bools 4 --counts 3
```

Common options: `--seed`, `--iters` (per restart), `--restarts`, `--data`,
`--alphabet`, and `--out`. B-RASP also has `--slots`, `--depth`, and `--tokens`
(for whitespace-separated symbols). C-RASP uses `--bools` (auxiliary Boolean
rules excluding `Out`) and `--counts`; its dataset adapter supports
single-character alphabets and excludes the internal `rp(...)` primitive, which
is not supported by the text/Lustre front ends. Both save their best candidate on budget exhaustion.
A perfect sample fit is **not** a proof of equivalence.

## Evaluate and translate

```sh
./rasp brasp eval /tmp/contains_a.brasp ab
./rasp crasp eval /tmp/contains_a.crasp ab
./rasp brasp eval /tmp/contains_a.brasp ""
./rasp brasp translate /tmp/contains_a.brasp > /tmp/contains_a.ltl
./rasp crasp translate /tmp/contains_a.crasp > /tmp/contains_a.lus
```

C-RASP `eval` and `translate` accept `--output-rule NAME` (default `Out`).
Evaluation uses each learner's existing semantics: B-RASP inserts BOS and can
accept the empty word; C-RASP uses `classifyWith` at the last ordinary position,
adds no EOS, and rejects the empty word. Its formal Lustre translation retains
the existing end-of-stream property semantics. B-RASP translation also keeps
its existing automatic `.ltl` copy under `brasp/ltl/`.

## Verify

B-RASP compiles the bad language to a direct summary circuit and runs ABC PDR.
For `empty`, the bad language is the input; for `subset`, it is left minus
right; for `equivalent`, it is their symmetric difference.

```sh
./rasp brasp verify empty brasp/examples/brasp/contains_ab.brasp --abc /path/to/abc
./rasp brasp verify equivalent reference.brasp learned.brasp --abc /path/to/abc
./rasp brasp verify subset candidate.brasp specification.brasp --abc /path/to/abc

./rasp crasp verify equivalent reference.crasp learned.crasp --kind2 /path/to/kind2
./rasp crasp verify subset candidate.crasp specification.crasp --config kind2.toml
./rasp crasp verify check reference.crasp --output-rule Out --kind2 /path/to/kind2
```

C-RASP uses the existing Lustre translation API and reads explicit Kind2 XML
property answers. `check` means the chosen output is always true at end of
stream; `disjoint` is also available. Binary comparisons accept
`--output-rule` and `--other-output-rule`. The languages cannot be compared
across one another by this CLI. C-RASP comparison requires matching alphabets.

Set `ABC_BIN` or `KIND2_BIN` instead of passing executable paths each time.
The CLI also automatically finds a workspace-local ABC at `abc/abc`.
To build that installation from the official source:

```sh
git clone --depth 1 https://github.com/berkeley-abc/abc.git abc
make -C abc -j4 ABC_USE_NO_READLINE=1
```

A Kind2 config is TOML with `kind2_executable = "/path/to/kind2"`.
`--timeout SECONDS` defaults to 120 (B-RASP compilation and solving, excluding
build; C-RASP solving). Verdicts go to stdout; solver diagnostics, including
raw counterexamples, go to stderr. Absence of a counterexample is never treated
as a proof unless the backend explicitly reports one.

| Exit | Learning | Verification |
| --- | --- | --- |
| 0 | Fits all samples | PROVED |
| 1 | Input/build/runtime error | Input/build/compiler error |
| 2 | Budget exhausted with sample errors | NOT PROVED |
| 3 | — | UNKNOWN or timeout |

## Layout and existing workflows

| Location | Purpose |
| --- | --- |
| `rasp`, `cli/` | Shared launcher and command routing |
| `brasp/src/main/scala/synthesis/` | B-RASP learner and verification circuit adapter |
| `crasp/src/main/scala/LS/` | Existing C-RASP local search and CEGIS |
| `crasp/src/main/scala/cli/` | Dataset learning and evaluation adapters |
| `crasp/rasp-translation/` | C-RASP parsing and Lustre translation |
| `tests/` | Shared CLI regression tests |

Existing experiment runners, constraint learning, and optimization remain
available through their original entry points. See [C-RASP documentation](crasp/README.md)
and [B-RASP documentation](brasp/README.md) for those specialized workflows.
For verification through the current sources, use the shared CLI above;
some historical B-RASP benchmark scripts target an older translator interface.

```sh
python3 -m unittest discover -s tests -v
./rasp test all
```
# transforer-program-verification-and-synthesis
