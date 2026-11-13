"""Shared command line for the two independent Scala projects."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LANGUAGES = ('brasp', 'crasp')


def absolute(value):
    return str(Path(value).expanduser().resolve())


def nonnegative(value):
    number = int(value)
    if number < 0:
        raise argparse.ArgumentTypeError('must be nonnegative')
    return number


def positive(value):
    number = nonnegative(value)
    if number == 0:
        raise argparse.ArgumentTypeError('must be positive')
    return number


def parser():
    p = argparse.ArgumentParser(prog='rasp', description='B-RASP and C-RASP learning and verification')
    commands = p.add_subparsers(dest='action', required=True)
    for name in ('build', 'test'):
        cmd = commands.add_parser(name, help=f'{name} one or both Scala projects')
        cmd.add_argument('language', choices=(*LANGUAGES, 'all'), default='all', nargs='?')
    for language in LANGUAGES:
        lang = commands.add_parser(language, help=f'{language.upper()} tools')
        actions = lang.add_subparsers(dest='operation', required=True)
        learn = actions.add_parser('learn', help='learn from +,word / -,word samples')
        learn.add_argument('--data', required=True, type=absolute)
        learn.add_argument('--alphabet', required=True, help='comma-separated symbols')
        learn.add_argument('--out', required=True, type=absolute)
        learn.add_argument('--seed', type=int, default=0)
        learn.add_argument('--iters', type=nonnegative, default=20000)
        learn.add_argument('--restarts', type=positive, default=4)
        if language == 'brasp':
            learn.add_argument('--slots', type=positive, default=6)
            learn.add_argument('--depth', type=nonnegative, default=2)
            learn.add_argument('--tokens', action='store_true')
            learn.add_argument('--balanced', action='store_true', help='weight errors by class size during search')
        else:
            learn.add_argument('--bools', type=nonnegative, default=4, help='auxiliary Boolean rules, excluding Out')
            learn.add_argument('--counts', type=nonnegative, default=3)
        evaluate = actions.add_parser('eval', help='evaluate a concrete word')
        evaluate.add_argument('input', type=absolute)
        evaluate.add_argument('word', help='quote the empty word as ""')
        score = actions.add_parser('score', help='accuracy of a program on a +,word / -,word dataset')
        score.add_argument('input', type=absolute)
        score.add_argument('data', type=absolute)
        score.add_argument('--json', action='store_true', help='print a JSON summary instead of text')
        translate = actions.add_parser('translate', help='translate to LTL (B-RASP) or Lustre (C-RASP)')
        translate.add_argument('input', type=absolute)
        if language == 'crasp':
            for command in (evaluate, score, translate):
                command.add_argument('--output-rule', default='Out')
        verify = actions.add_parser('verify', help='verify a property using ABC or Kind2')
        relations = ['empty', 'equivalent', 'subset'] if language == 'brasp' else ['check', 'equivalent', 'subset', 'disjoint']
        verify.add_argument('relation', choices=relations)
        verify.add_argument('input', type=absolute, help='left-hand program')
        verify.add_argument('other', nargs='?', type=absolute, help='right-hand program for a binary relation')
        verify.add_argument('--timeout', type=positive, default=120, help='seconds, excluding automatic build')
        if language == 'brasp':
            local_abc = ROOT / 'abc/abc'
            verify.add_argument('--abc', default=os.environ.get('ABC_BIN', str(local_abc) if local_abc.is_file() else 'abc'),
                                help='ABC executable (default: ABC_BIN, workspace abc/abc, or PATH)')
        else:
            verify.add_argument('--output-rule', default='Out')
            verify.add_argument('--other-output-rule', default='Out')
            verify.add_argument('--config', type=absolute, help='Kind2 TOML configuration')
            verify.add_argument('--kind2', help='Kind2 executable; overrides --config (default: KIND2_BIN or kind2)')
    return p


def source_stamp(project):
    files = [project / 'build.sbt']
    for folder, suffixes in [('src/main', ('.scala', '.java')), ('project', ('.sbt', '.scala', '.properties'))]:
        files.extend(f for f in (project / folder).rglob('*')
                     if f.is_file() and f.suffix in suffixes and 'target' not in f.relative_to(project).parts)
    return [(str(f.relative_to(project)), f.stat().st_mtime_ns, f.stat().st_size) for f in sorted(files)]


def classpath(language, force=False):
    project = ROOT / language
    cache = project / 'target/rasp-cli-classpath.json'
    stamp = source_stamp(project)
    if not force and cache.exists():
        stored = json.loads(cache.read_text())
        if stored['stamp'] == json.loads(json.dumps(stamp)) and all(Path(x).exists() for x in stored['classpath'].split(os.pathsep)):
            return stored['classpath']
    result = subprocess.run(['sbt', '-batch', '-no-colors', 'compile', 'export Runtime / fullClasspath'],
                            cwd=project, stdout=subprocess.PIPE, stderr=sys.stderr, text=True)
    print(result.stdout, file=sys.stderr, end='')
    if result.returncode:
        raise RuntimeError(f'{language} build failed (exit {result.returncode})')
    lines = [line.strip().removeprefix('[info] ') for line in result.stdout.splitlines()]
    candidates = [line for line in lines if line.startswith('/') and
                  all(Path(x).exists() for x in line.split(os.pathsep))]
    if not candidates:
        raise RuntimeError('sbt did not return a runtime classpath')
    cp = candidates[-1]
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(json.dumps({'stamp': stamp, 'classpath': cp}))
    return cp


def java(language, main, args):
    return ['java', '-Xss16m', '-cp', classpath(language), main, *map(str, args)]


def learn_command(args):
    if args.action == 'brasp':
        options = []
        for name in ('data', 'alphabet', 'out', 'seed', 'iters', 'restarts', 'slots', 'depth'):
            options.extend(['--' + name, str(getattr(args, name))])
        if args.tokens:
            options.append('--tokens')
        if args.balanced:
            options.append('--balanced')
        return java('brasp', 'brasp.synthesis.RunSynthesis', options)
    return java('crasp', 'cli.DatasetLearning', [args.data, args.alphabet, args.out, args.seed,
                args.iters, args.restarts, args.bools, args.counts])


def read_dataset(path):
    """Parse +,word / -,word lines (the learners' format) into (word, label)."""
    rows = []
    for number, raw in enumerate(Path(path).read_text().splitlines(), 1):
        line = raw.strip()
        if not line:
            continue
        if line[:2] not in ('+,', '-,'):
            raise ValueError(f'{path}:{number}: expected +,word or -,word')
        rows.append((line[2:].strip(), line[0] == '+'))
    return rows


def summarize_scores(rows, predictions):
    """Accuracy overall and per length bucket (powers of two)."""
    if len(rows) != len(predictions):
        raise RuntimeError(f'evaluator returned {len(predictions)} answers for {len(rows)} words')
    buckets = {}
    for (word, label), predicted in zip(rows, predictions):
        n = len(word)
        key = '0' if n == 0 else f'{1 << (n.bit_length() - 1)}-{(1 << n.bit_length()) - 1}'
        total, correct = buckets.get(key, (0, 0))
        buckets[key] = (total + 1, correct + (predicted == label))
    correct = sum(c for _, c in buckets.values())
    order = sorted(buckets, key=lambda k: int(k.split('-')[0]))
    return {'words': len(rows), 'correct': correct, 'accuracy': correct / len(rows) if rows else 1.0,
            'false_positives': sum(1 for (_, y), p in zip(rows, predictions) if p and not y),
            'false_negatives': sum(1 for (_, y), p in zip(rows, predictions) if y and not p),
            'by_length': {k: {'words': buckets[k][0], 'accuracy': buckets[k][1] / buckets[k][0]} for k in order}}


def score_dataset(args):
    rows = read_dataset(args.data)
    with tempfile.NamedTemporaryFile('w', suffix='.txt', delete=False) as handle:
        handle.write(''.join(word + '\n' for word, _ in rows))
        words_file = handle.name
    try:
        if args.action == 'brasp':
            command = java('brasp', 'brasp.synthesis.EvaluateWords', [args.input, words_file])
        else:
            command = java('crasp', 'cli.EvaluateWords', [args.input, args.output_rule, words_file])
        result = subprocess.run(command, cwd=ROOT / args.action, stdout=subprocess.PIPE, stderr=sys.stderr, text=True)
    finally:
        os.unlink(words_file)
    if result.returncode:
        raise RuntimeError(f'evaluator failed (exit {result.returncode})')
    answers = result.stdout.split()
    if any(a not in ('true', 'false') for a in answers):
        raise RuntimeError('evaluator printed something other than true/false')
    summary = summarize_scores(rows, [a == 'true' for a in answers])
    if args.json:
        print(json.dumps(summary))
    else:
        print(f"accuracy {summary['accuracy']:.4f} ({summary['correct']}/{summary['words']}), "
              f"FP {summary['false_positives']}, FN {summary['false_negatives']}")
        for key, bucket in summary['by_length'].items():
            print(f"  len {key:>9}: {bucket['accuracy']:.4f} ({bucket['words']})")
    return 0


def classify_abc(code, text):
    if code != 0:
        return 'UNKNOWN', 3
    # ABC can return exit 0 for command errors. Require an explicit PDR result.
    if re.search(r'\bProperty proved\b', text):
        return 'PROVED', 0
    if re.search(r'Output \d+ of miter .* was asserted|counter-example', text, re.I):
        return 'NOT PROVED', 2
    return 'UNKNOWN', 3


def verify_brasp(args):
    abc = shutil.which(args.abc)
    if not abc:
        raise RuntimeError('ABC not found; pass --abc /path/to/abc or set ABC_BIN')
    cp = classpath('brasp')
    import time
    deadline = time.monotonic() + args.timeout
    with tempfile.TemporaryDirectory(prefix='rasp-verify-') as directory:
        # Relative fixed file name avoids embedding user paths in ABC's command language.
        model = str(Path(directory) / 'model.aig')
        compile_cmd = ['java', '-Xss16m', '-cp', cp, 'brasp.synthesis.VerificationModel',
                       args.relation, args.input, args.other or '-', model]
        compiled = subprocess.run(compile_cmd, timeout=max(.001, deadline - time.monotonic()))
        if compiled.returncode:
            return 1
        solved = subprocess.run([abc, '-c', 'read_aiger model.aig; scleanup; dc2; pdr; print_status'],
                                cwd=directory, capture_output=True, text=True,
                                timeout=max(.001, deadline - time.monotonic()))
        print(solved.stdout + solved.stderr, file=sys.stderr, end='')
        verdict, code = classify_abc(solved.returncode, solved.stdout + solved.stderr)
        print(verdict)
        return code


def crasp_lustre(args):
    sys.path.insert(0, str(ROOT / 'crasp/rasp-translation'))
    try:
        from rasptranslation.api import crasp_to_lustre, compare_to_lustre, Comparison
    except ImportError as error:
        raise RuntimeError('C-RASP translation requires lark: python3 -m pip install "lark>=1.3.1"') from error
    if args.operation == 'translate' or args.relation == 'check':
        return crasp_to_lustre(Path(args.input), args.output_rule)
    comparison = {'equivalent': Comparison.EQUAL, 'subset': Comparison.SUBSET, 'disjoint': Comparison.DISJOINT}[args.relation]
    return compare_to_lustre(Path(args.input), args.output_rule, Path(args.other), args.other_output_rule, comparison)


def classify_kind2(code, text):
    # --exit_code_mode only_errors makes any nonzero exit a backend failure.
    if code != 0:
        return 'UNKNOWN', 3
    try:
        root = ET.fromstring(text)
    except ET.ParseError:
        return 'UNKNOWN', 3
    properties = [p for p in root.findall('.//Property') if p.get('isCandidate') != 'true']
    answers = [(p.findtext('Answer') or '').strip().lower() for p in properties]
    if 'falsifiable' in answers:
        return 'NOT PROVED', 2
    if answers and all(a == 'valid' for a in answers):
        return 'PROVED', 0
    return 'UNKNOWN', 3


def verify_crasp(args):
    configuration = tomllib.loads(Path(args.config).read_text()) if args.config else {}
    executable = args.kind2 or configuration.get('kind2_executable') or os.environ.get('KIND2_BIN', 'kind2')
    executable = shutil.which(executable)
    if not executable:
        raise RuntimeError('Kind2 not found; pass --kind2 /path/to/kind2, --config FILE, or set KIND2_BIN')
    lustre = crasp_lustre(args)
    solved = subprocess.run([executable, '-xml', '--exit_code_mode', 'only_errors', '--timeout', str(args.timeout)],
                            input=lustre, capture_output=True, text=True, timeout=args.timeout + 2)
    # Preserve raw diagnostics and counterexamples on stderr.
    print(solved.stdout + solved.stderr, file=sys.stderr, end='')
    verdict, code = classify_kind2(solved.returncode, solved.stdout)
    print(verdict)
    return code


def main(argv=None):
    p = parser()
    args = p.parse_args(argv)
    try:
        if args.action in ('build', 'test'):
            languages = LANGUAGES if args.language == 'all' else [args.language]
            for language in languages:
                if args.action == 'build':
                    classpath(language, force=True)
                else:
                    code = subprocess.run(['sbt', '-batch', 'test'], cwd=ROOT / language).returncode
                    if code:
                        return code
            return 0
        language = args.action
        if args.operation == 'learn':
            return subprocess.run(learn_command(args), cwd=ROOT / language).returncode
        if args.operation == 'eval':
            command = java(language, 'brasp.Translator', [args.input, '--word', args.word]) if language == 'brasp' else \
                java(language, 'cli.Evaluate', [args.input, args.output_rule, args.word])
            return subprocess.run(command, cwd=ROOT / language).returncode
        if args.operation == 'score':
            return score_dataset(args)
        if args.operation == 'translate':
            if language == 'brasp':
                return subprocess.run(java(language, 'brasp.Translator', [args.input, '--ltl']), cwd=ROOT / language).returncode
            print(crasp_lustre(args))
            return 0
        binary = args.relation not in ('empty', 'check')
        if binary != bool(args.other):
            p.error('this relation requires two programs' if binary else 'this relation accepts only one program')
        if language == 'brasp':
            return verify_brasp(args)
        return verify_crasp(args)
    except subprocess.TimeoutExpired:
        print('UNKNOWN: timeout', file=sys.stderr)
        return 3
    except (OSError, RuntimeError, ValueError) as error:
        print(f'rasp: {error}', file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130


if __name__ == '__main__':
    sys.exit(main())
