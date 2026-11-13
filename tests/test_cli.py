import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from cli import rasp


class CliTests(unittest.TestCase):
    def test_learning_dispatch_preserves_paths_and_arguments(self):
        for language in rasp.LANGUAGES:
            args = rasp.parser().parse_args([language, 'learn', '--data', 'data with spaces.csv',
                                            '--alphabet', 'a,b', '--out', 'output file.' + language,
                                            '--seed', '42', '--iters', '10'])
            with patch.object(rasp, 'classpath', return_value='/classes:/libs/runtime.jar'):
                command = rasp.learn_command(args)
            self.assertIn(str(Path('data with spaces.csv').resolve()), command)
            self.assertIn(str(Path('output file.' + language).resolve()), command)
            self.assertIn('42', command)
            self.assertIn('10', command)
            self.assertIn('brasp.synthesis.RunSynthesis' if language == 'brasp' else 'cli.DatasetLearning', command)

    def test_binary_relation_validation_happens_before_backend(self):
        with patch.object(rasp, 'verify_brasp') as backend, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                rasp.main(['brasp', 'verify', 'subset', 'left.brasp'])
            backend.assert_not_called()

    def test_abc_does_not_treat_successful_process_as_proof(self):
        self.assertEqual(rasp.classify_abc(0, 'Error: cannot open file'), ('UNKNOWN', 3))
        self.assertEqual(rasp.classify_abc(1, 'Property proved.'), ('UNKNOWN', 3))
        self.assertEqual(rasp.classify_abc(0, 'Property proved. Time = 0.1'), ('PROVED', 0))
        self.assertEqual(rasp.classify_abc(0, 'Output 0 of miter "model" was asserted in frame 2.'), ('NOT PROVED', 2))

    def test_kind2_requires_explicit_property_answers(self):
        def xml(answer):
            return f'<Results><Property><Answer>{answer}</Answer></Property></Results>'
        self.assertEqual(rasp.classify_kind2(0, xml('valid')), ('PROVED', 0))
        self.assertEqual(rasp.classify_kind2(0, xml('falsifiable')), ('NOT PROVED', 2))
        self.assertEqual(rasp.classify_kind2(0, xml('unknown')), ('UNKNOWN', 3))
        self.assertEqual(rasp.classify_kind2(0, '<Results/>'), ('UNKNOWN', 3))
        self.assertEqual(rasp.classify_kind2(0, 'not XML'), ('UNKNOWN', 3))
        self.assertEqual(rasp.classify_kind2(1, xml('valid')), ('UNKNOWN', 3))
        self.assertEqual(rasp.classify_kind2(0, '<Results><Property isCandidate="true"><Answer>valid</Answer></Property></Results>'), ('UNKNOWN', 3))

    def test_score_summary_counts_errors_and_length_buckets(self):
        rows = [('', True), ('a', False), ('ab', True), ('abc', True), ('abcd', False)]
        summary = rasp.summarize_scores(rows, [True, True, True, False, False])
        self.assertEqual((summary['correct'], summary['false_positives'], summary['false_negatives']), (3, 1, 1))
        self.assertEqual(list(summary['by_length']), ['0', '1-1', '2-3', '4-7'])
        self.assertEqual(summary['by_length']['2-3'], {'words': 2, 'accuracy': 0.5})
        with self.assertRaises(RuntimeError):
            rasp.summarize_scores(rows, [True])

    def test_dataset_reader_keeps_empty_word_and_rejects_bad_lines(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'd.csv'
            path.write_text('+,\n\n-,ab\n')
            self.assertEqual(rasp.read_dataset(path), [('', True), ('ab', False)])
            path.write_text('ab\n')
            with self.assertRaises(ValueError):
                rasp.read_dataset(path)

    def test_exit_code_from_learner_is_preserved(self):
        with patch.object(rasp, 'learn_command', return_value=['java', 'learner']), \
             patch.object(rasp.subprocess, 'run') as run:
            run.return_value.returncode = 2
            self.assertEqual(rasp.main(['crasp', 'learn', '--data', 'x', '--alphabet', 'a', '--out', 'y']), 2)

    def test_source_stamp_detects_deletion_and_ignores_build_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'build.sbt').write_text('settings')
            source = root / 'src/main/scala/Main.scala'
            source.parent.mkdir(parents=True)
            source.write_text('object Main')
            first = rasp.source_stamp(root)
            generated = root / 'project/target/generated.scala'
            generated.parent.mkdir(parents=True)
            generated.write_text('generated')
            self.assertEqual(rasp.source_stamp(root), first)
            source.unlink()
            self.assertNotEqual(rasp.source_stamp(root), first)

    def test_bad_budget_is_rejected(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            rasp.parser().parse_args(['brasp', 'learn', '--data', 'x', '--alphabet', 'a', '--out', 'y', '--restarts', '0'])


if __name__ == '__main__':
    unittest.main()
