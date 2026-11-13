#!/usr/bin/env python3
"""Download FLaRe (Butoi et al., ICLR 2025) at a pinned commit and convert it
to the shared `+,word` / `-,word` learning format.

Every language's tokens are renamed to single letters a, b, c, ... in sorted
token order, so both learners (C-RASP only accepts single-character symbols)
see the same alphabet. `symbols.json` records the mapping back to FLaRe tokens.

Output, per language, under OUT/<language>/:
  train.csv              FLaRe training set (10,000 words, length <= 40)
  validation-short.csv   held-out, same length range as train
  validation-long.csv    held-out, length <= 80
  test.csv               held-out, length <= 500 (length generalization)
  test-short-held-out.csv / test-edit-distance.csv where FLaRe provides them
  symbols.json           {"alphabet": "a,b", "tokens": {"a": "0", "b": "1"}}
and OUT/manifest.json with counts, length ranges and SHA-256 checksums.

Usage:
  python3 benchmarks/learning/fetch_flare.py              # clone + convert
  python3 benchmarks/learning/fetch_flare.py --src DIR    # use an existing checkout
"""
import argparse
import hashlib
import json
from pathlib import Path
import string
import subprocess
import tempfile

REPO = 'https://github.com/rycolab/flare.git'
COMMIT = 'a671eb9932620cc888757612f30845efe3304831'
HERE = Path(__file__).resolve().parent

# Theory-backed expectation for B-RASP (= star-free languages, Yang, Chiang &
# Angluin 2024). C-RASP expectations are left to the experiments.
STAR_FREE = {
    'first': True, 'repeat-01': True, 'even-pairs': True,
    'parity': False, 'cycle-navigation': False, 'modular-arithmetic-simple': False,
}
CHOMSKY = {
    'even-pairs': 'R', 'repeat-01': 'R', 'parity': 'R', 'cycle-navigation': 'R',
    'modular-arithmetic-simple': 'R', 'first': 'R',
    'dyck-2-3': 'DCF', 'majority': 'DCF', 'stack-manipulation': 'DCF', 'marked-reversal': 'DCF',
    'unmarked-reversal': 'CF',
    'marked-copy': 'CS', 'missing-duplicate-string': 'CS', 'odds-first': 'CS',
    'binary-addition': 'CS', 'binary-multiplication': 'CS', 'compute-sqrt': 'CS', 'bucket-sort': 'CS',
}


def read_split(folder):
    words = (folder / 'main.tok').read_text().split('\n')
    labels = (folder / 'labels.txt').read_text().split()
    if words and words[-1] == '':
        words.pop()
    if len(words) != len(labels) or any(y not in ('0', '1') for y in labels):
        raise ValueError(f'malformed split: {folder}')
    return [(w.split(), y == '1') for w, y in zip(words, labels)]


def write_split(path, rows, rename):
    lines = [('+,' if label else '-,') + ''.join(rename[t] for t in tokens) for tokens, label in rows]
    data = '\n'.join(lines) + '\n'
    path.write_text(data)
    lengths = [len(t) for t, _ in rows]
    return {'file': path.name, 'words': len(rows), 'positive': sum(y for _, y in rows),
            'empty_positive': sum(1 for t, y in rows if y and not t),
            'min_len': min(lengths), 'max_len': max(lengths),
            'sha256': hashlib.sha256(data.encode()).hexdigest()}


def convert(src, out):
    out.mkdir(parents=True, exist_ok=True)
    manifest = {'source': REPO, 'commit': COMMIT, 'license': 'MIT', 'languages': {}}
    for lang in sorted(p.name for p in src.iterdir() if (p / 'main.tok').is_file()):
        splits = {'train': read_split(src / lang)}
        for folder in sorted((src / lang / 'datasets').iterdir()):
            splits[folder.name] = read_split(folder)
        tokens = sorted({t for rows in splits.values() for w, _ in rows for t in w})
        if len(tokens) > 26:
            raise ValueError(f'{lang}: more than 26 symbols')
        rename = dict(zip(tokens, string.ascii_lowercase))
        target = out / lang
        target.mkdir(exist_ok=True)
        entry = {'chomsky': CHOMSKY.get(lang), 'star_free': STAR_FREE.get(lang),
                 'alphabet': ','.join(rename[t] for t in tokens), 'splits': {}}
        for name, rows in splits.items():
            entry['splits'][name] = write_split(target / f'{name}.csv', rows, rename)
        (target / 'symbols.json').write_text(json.dumps(
            {'alphabet': entry['alphabet'], 'tokens': {v: k for k, v in rename.items()}}, indent=2) + '\n')
        manifest['languages'][lang] = entry
        print(f"{lang:28} alphabet={entry['alphabet']:12} " +
              ' '.join(f"{n}={s['words']}" for n, s in entry['splits'].items()))
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


def main():
    p = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    p.add_argument('--src', type=Path, help='existing FLaRe checkout (must be at the pinned commit)')
    p.add_argument('--out', type=Path, default=HERE / 'flare')
    args = p.parse_args()
    if args.src:
        head = subprocess.run(['git', '-C', str(args.src), 'rev-parse', 'HEAD'],
                              capture_output=True, text=True).stdout.strip()
        if head and head != COMMIT:
            raise SystemExit(f'{args.src} is at {head}, expected {COMMIT}')
        convert(args.src.resolve(), args.out.resolve())
        return
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(['git', 'clone', '--quiet', '--filter=blob:none', '--no-checkout', REPO, tmp], check=True)
        subprocess.run(['git', '-C', tmp, 'checkout', '--quiet', COMMIT], check=True)
        convert(Path(tmp), args.out.resolve())


if __name__ == '__main__':
    main()
