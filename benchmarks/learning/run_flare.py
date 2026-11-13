#!/usr/bin/env python3
"""Run the B-RASP and C-RASP learners on converted FLaRe languages.

For each (language, learner, seed): learn from the first --train-size words
of train.csv, then score the saved program on every held-out split with
`rasp <learner> score`. One JSON object per run is appended to --results.

C-RASP rejects the empty word by definition, so empty words are dropped from
its training and scoring data; the number dropped is recorded per run.

Example (smoke test):
  python3 benchmarks/learning/run_flare.py --languages first majority \
      --iters 5000 --restarts 2
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RASP = ROOT / 'rasp'
SPLITS = ('validation-short', 'validation-long', 'test')
LEARN_STATUS = {0: 'fits', 2: 'exhausted', 1: 'error'}


def subset(src, dst, limit, drop_empty):
    kept, dropped = [], 0
    for line in src.read_text().splitlines():
        if not line.strip():
            continue
        if drop_empty and line.strip() in ('+,', '-,'):
            dropped += 1
            continue
        kept.append(line)
        if limit and len(kept) >= limit:
            break
    dst.write_text('\n'.join(kept) + '\n')
    return len(kept), dropped


def run_one(args, lang, learner, seed, alphabet, work):
    data = args.data / lang
    drop = learner == 'crasp'
    train = work / f'{lang}.{learner}.train.csv'
    n_train, dropped = subset(data / 'train.csv', train, args.train_size, drop)
    program = args.programs / f'{lang}.{learner}.s{seed}.{learner}'
    program.parent.mkdir(parents=True, exist_ok=True)
    cmd = [str(RASP), learner, 'learn', '--data', str(train), '--alphabet', alphabet, '--out', str(program),
           '--seed', str(seed), '--iters', str(args.iters), '--restarts', str(args.restarts)]
    if learner == 'brasp':
        cmd += ['--slots', str(args.slots), '--depth', str(args.depth)] + (['--balanced'] if args.balanced else [])
    else:
        cmd += ['--bools', str(args.bools), '--counts', str(args.counts)]
    record = {'language': lang, 'learner': learner, 'seed': seed, 'train_words': n_train,
              'empty_dropped_train': dropped, 'iters': args.iters, 'restarts': args.restarts,
              'config': vars(args).get('tag')}
    start = time.monotonic()
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=args.timeout)
        record['learn_status'] = LEARN_STATUS.get(proc.returncode, f'exit {proc.returncode}')
        record['learn_log'] = proc.stderr.strip().splitlines()[-1:] if proc.stderr.strip() else []
    except subprocess.TimeoutExpired:
        record['learn_status'] = 'timeout'
    record['learn_seconds'] = round(time.monotonic() - start, 2)
    if not program.exists() or record['learn_status'] in ('error', 'timeout'):
        return record
    record['program'] = str(program.relative_to(ROOT)) if program.is_relative_to(ROOT) else str(program)
    record['program_lines'] = sum(1 for l in program.read_text().splitlines() if l.strip())
    for split in SPLITS:
        src = data / f'{split}.csv'
        if not src.exists():
            continue
        scored = work / f'{lang}.{learner}.{split}.csv'
        _, dropped_eval = subset(src, scored, 0, drop)
        proc = subprocess.run([str(RASP), learner, 'score', str(program), str(scored), '--json'],
                              capture_output=True, text=True, timeout=args.timeout)
        if proc.returncode:
            record[split] = {'error': proc.stderr.strip().splitlines()[-1:]}
            continue
        summary = json.loads(proc.stdout)
        summary['empty_dropped'] = dropped_eval
        record[split] = summary
    return record


def main():
    p = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    p.add_argument('--data', type=Path, default=HERE / 'flare')
    p.add_argument('--languages', nargs='+', help='default: all in the manifest')
    p.add_argument('--learners', nargs='+', default=['brasp', 'crasp'], choices=['brasp', 'crasp'])
    p.add_argument('--seeds', nargs='+', type=int, default=[1])
    p.add_argument('--train-size', type=int, default=300, help='first N training words (0 = all)')
    p.add_argument('--iters', type=int, default=20000)
    p.add_argument('--restarts', type=int, default=5)
    p.add_argument('--slots', type=int, default=6)
    p.add_argument('--depth', type=int, default=2, help='B-RASP expression depth')
    p.add_argument('--bools', type=int, default=4)
    p.add_argument('--counts', type=int, default=3)
    p.add_argument('--balanced', action=argparse.BooleanOptionalAction, default=True,
                   help='B-RASP class-balanced search objective (default: on)')
    p.add_argument('--tag', help='label stored with each record')
    p.add_argument('--timeout', type=int, default=1800, help='seconds per learner call')
    p.add_argument('--results', type=Path, default=HERE / 'results/flare.jsonl')
    p.add_argument('--programs', type=Path, default=HERE / 'results/programs')
    args = p.parse_args()
    manifest = json.loads((args.data / 'manifest.json').read_text())
    languages = args.languages or sorted(manifest['languages'])
    args.results.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp, args.results.open('a') as out:
        for lang in languages:
            alphabet = manifest['languages'][lang]['alphabet']
            for learner in args.learners:
                for seed in args.seeds:
                    record = run_one(args, lang, learner, seed, alphabet, Path(tmp))
                    out.write(json.dumps(record) + '\n')
                    out.flush()
                    accs = ' '.join(f"{s}={record[s]['accuracy']:.3f}" for s in SPLITS
                                    if isinstance(record.get(s), dict) and 'accuracy' in record[s])
                    print(f"{lang:26} {learner} s{seed} {record['learn_status']:9} "
                          f"{record['learn_seconds']:7.1f}s {accs}", flush=True)


if __name__ == '__main__':
    sys.exit(main())
