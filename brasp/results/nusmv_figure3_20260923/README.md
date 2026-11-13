# NuSMV Figure 3 experiment

Pipeline: the existing `--one-variable --ltlf` export, finite-prefix relativization to infinite LTL, then NuSMV 2.7.1. BDD uses `go; check_ltlspec`; SAT-BMC uses `go_bmc; check_ltlspec_bmc_inc -k 10000`. The negated specification is true exactly when the source language is empty. A BMC counterexample proves nonemptiness; no counterexample through the bound is inconclusive.

## Protocol

- The manifest contains the 77 instances currently plotted by `scripts/plot_exp_scaling.py` (the 69-instance run plus eight later additions).
- 900 seconds per repetition including Java startup, export, encoding, and solving; 4 GB Java heap; 64 MB stack for subprocesses.
- Initially three repetitions for conclusive results. At the user's request, the remaining cases use one repetition, starting after BDD `same(300)`. Completed three-repetition medians are retained; each record lists its actual samples and repetition count. Terminal failures are not repeated.
- Initially BDD and BMC were scheduled serially. At the user's request, BMC started concurrently while BDD was checking `same(400)`, using two engine workers. Other pre-existing Python jobs were observed on the machine, so these are not controlled idle-machine timings. Metadata records the protocol change.
- Within a family, larger instances after a timeout or size limit are explicitly marked `skipped`, following the previous Lisa run policy. Skips are not counted as measured timeouts. BMC bound exhaustion does not trigger skipping.
- Every decided result is compared to the existing reference classifications (including empty monotone_past instances). Counterexample words are decoded and reversed back to source order. Source replay is tested on small integration cases; the full experiment does not claim all witnesses were replayed.
- Circuit, Aalta, and Lisa comparison times are imported from existing Figure 3 results rather than rerun.

## Reproduce

From the repository root, after building the compiler and installing NuSMV:

```sh
python3 scripts/run_nusmv_figure3.py --nusmv /path/to/NuSMV --repetitions 1 --jobs 2
MPLBACKEND=Agg python3 scripts/plot_nusmv_figure3.py results/nusmv_figure3_20260923
```

Use `--resume` on the runner to continue an interrupted experiment. The plotter refuses incomplete runs unless `--allow-partial` is explicitly supplied; partial plots have a separate filename.

## Tool provisioning on this machine

The official macOS universal archive was downloaded from
https://nusmv.fbk.eu/distrib/2.7.1/NuSMV-2.7.1-macos-universal.tar.xz
and verified against the published SHA-256:
`92788591309d449c9cd1290e3a0e9c3d7f5ff7e7cdc8f55916be06f605d61256`.
The archive unpacks as `NuSMV-2.7.1-macosx`. Its MacPorts library paths were changed to existing local libraries, then the local executable was ad-hoc signed:

```sh
install_name_tool \
  -change /opt/local/lib/libedit.0.dylib /usr/lib/libedit.3.dylib \
  -change /opt/local/lib/libxml2.2.dylib /usr/lib/libxml2.2.dylib \
  -change /opt/local/lib/libgmp.10.dylib /usr/local/opt/gmp/lib/libgmp.10.dylib \
  tmp/nusmv/NuSMV-2.7.1-macosx/bin/NuSMV
codesign --force --sign - tmp/nusmv/NuSMV-2.7.1-macosx/bin/NuSMV
```

No solver code was modified. `metadata.json` records the executable and compiler hashes, version banner, and platform. `artifacts/<engine>/<input>/` contains exported LTLf, SMV input, variable mapping, commands, and per-repetition logs. Timeout logs can be empty because the shared subprocess helper discards incomplete output; their duration and timeout status are recorded separately.
