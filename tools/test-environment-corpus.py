#!/usr/bin/env python3
"""Reject corpus leakage and provenance corruption without model dependencies."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('corpus', ROOT/'tools/check-environment-corpus.py')
corpus = importlib.util.module_from_spec(spec)
spec.loader.exec_module(corpus)


class ManifestChecks(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((ROOT/'shared/src/commonTest/fixtures/environment/corpus/manifest.json').read_text())

    def load(self, manifest):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'manifest.json'
            path.write_text(json.dumps(manifest))
            return corpus.load_manifest(path)

    def test_reviewed_manifest(self):
        self.assertEqual(len(self.load(self.manifest)['recordings']), 7)

    def test_rejects_leakage_and_unreviewed_sources(self):
        for field, value in [('author', 'SpliceSound'), ('sourceGroup', 'SpliceSound/crowd-murmur'),
                             ('sha256', self.manifest['recordings'][1]['sha256']),
                             ('file', '../source.mp3'), ('download', 'https://example.com/audio.mp3'),
                             ('license', 'unknown'), ('referenceBasis', 'reviewed\nOTHER\tQUIET')]:
            with self.subTest(field=field):
                altered = copy.deepcopy(self.manifest)
                altered['recordings'][-1][field] = value
                with self.assertRaises(ValueError):
                    self.load(altered)

    def test_rejects_same_partition_duplicate_recording(self):
        altered = copy.deepcopy(self.manifest)
        altered['recordings'][1]['sha256'] = altered['recordings'][0]['sha256']
        with self.assertRaises(ValueError):
            self.load(altered)


if __name__ == '__main__':
    unittest.main()
