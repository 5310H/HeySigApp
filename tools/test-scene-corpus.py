#!/usr/bin/env python3
"""Reject source/participant leakage and retain production/test separation."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('scene_corpus',ROOT/'tools/environment/scene_corpus.py')
scene=importlib.util.module_from_spec(spec);spec.loader.exec_module(scene)


class CorpusChecks(unittest.TestCase):
    def setUp(self):
        self.manifest=json.loads(scene.DEFAULT_MANIFEST.read_text())

    def load(self,manifest):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'manifest.json';path.write_text(json.dumps(manifest))
            return scene.load_manifest(path)

    def test_reviewed_corpus(self):
        self.assertEqual(len(self.load(self.manifest)['recordings']),28)

    def test_rejects_creator_participant_and_session_leakage(self):
        development=next(c for c in self.manifest['recordings'] if c['partition']=='development' and c['id'].startswith('ami-'))
        target=next(i for i,c in enumerate(self.manifest['recordings']) if c['partition']=='validation' and c['id'].startswith('ami-'))
        for key,value in [('participants',development['participants']),('sourceGroup',development['sourceGroup']),('splitGroup',development['splitGroup'])]:
            with self.subTest(key=key):
                altered=copy.deepcopy(self.manifest);altered['recordings'][target][key]=value
                with self.assertRaises(ValueError):self.load(altered)
        altered=copy.deepcopy(self.manifest)
        target=next(c for c in altered['recordings'] if c['partition']=='validation' and not c['id'].startswith('ami-'))
        target['author']='SpliceSound'
        with self.assertRaises(ValueError):self.load(altered)

    def test_rejects_duplicate_audio_and_unreviewed_paths_licenses(self):
        first=self.manifest['recordings'][0]
        for key,value in [('sha256',first['sha256']),('file','../recording.wav'),('license','unreviewed'),('source','http://freesound.org/s/1/')]:
            with self.subTest(key=key):
                altered=copy.deepcopy(self.manifest);altered['recordings'][1][key]=value
                with self.assertRaises(ValueError):self.load(altered)

    def test_native_recordings_are_pinned_and_separate(self):
        base=ROOT/'shared/src/commonTest/fixtures/environment/scene-head/device/scene-fixtures'
        manifest=json.loads((base/'manifest.json').read_text())
        self.assertEqual({r['expectedEnvironment'] for r in manifest['recordings']},set(scene.LABELS))
        self.assertEqual(hashlib.sha256((ROOT/'audioAndroid/src/main/assets/environment/scene-head.bin').read_bytes()).hexdigest(),manifest['headSha256'])
        sources={c['id']:c for c in self.manifest['recordings']}
        for row in manifest['recordings']:
            self.assertEqual(sources[row['sourceId']]['partition'],'development')
            self.assertEqual(row['sourceSha256'],sources[row['sourceId']]['sha256'])
            data=(base/row['file']).read_bytes()
            self.assertEqual(len(data),row['seconds']*16000*2)
            self.assertEqual(hashlib.sha256(data).hexdigest(),row['sha256'])
        self.assertFalse((ROOT/'audioAndroid/src/main/assets/scene-fixtures').exists())


if __name__=='__main__':unittest.main()
