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

    def test_attribution_license_requires_explicit_review_fields(self):
        altered=copy.deepcopy(self.manifest)
        clip=next(c for c in altered['recordings'] if not c['id'].startswith('ami-'))
        clip['license']='CC-BY-3.0';clip['licenseUrl']='https://creativecommons.org/licenses/by/3.0/'
        with self.assertRaises(ValueError):self.load(altered)
        clip['attribution']=clip['title']+' by '+clip['author'];clip['transforms']='Decoded WAV and embedding features'
        self.load(altered)
        clip['license']='CC-BY-NC-3.0';clip['licenseUrl']='https://creativecommons.org/licenses/by-nc/3.0/'
        with self.assertRaises(ValueError):self.load(altered)

    def test_expansion_preserves_sources_partitions_and_fresh_evaluation(self):
        expanded=scene.load_manifest(scene.DEFAULT_MANIFEST.with_name('expanded-corpus.json'))
        old={c['id']:c for c in self.manifest['recordings']}
        current={c['id']:c for c in expanded['recordings']}
        self.assertEqual(len(current),len(old)+7)
        self.assertTrue(all(current[k]==v for k,v in old.items()))
        self.assertEqual(set(expanded['freshValidationIds']),{'fsd-401794','fsd-17118'})
        self.assertTrue(set(expanded['freshValidationIds']).isdisjoint(old))
        for fresh in (['fsd-324783'],['fsd-401794','fsd-401794'],['missing']):
            altered=copy.deepcopy(expanded);altered['freshValidationIds']=fresh
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

    def test_fallback_profile_assets_and_development_regression(self):
        assets=ROOT/'audioAndroid/src/main/assets/environment'
        primary=hashlib.sha256((assets/'scene-head.bin').read_bytes()).hexdigest()
        specialist=hashlib.sha256((assets/'small-group-head.bin').read_bytes()).hexdigest()
        self.assertEqual(specialist,'b32f1124b31e4961f52c1153fa8220d3cc10b374292dfdb199b7bcb852e56be8')
        encoder=hashlib.sha256((assets/'yamnet.tflite').read_bytes()).hexdigest()
        profile=hashlib.sha256(('HSGENV2\n'+encoder+'\n'+primary+'\n'+specialist+'\nprimary-preserving-small-group-v1\n').encode()).hexdigest()
        code=(ROOT/'shared/src/commonMain/kotlin/com/heysig/noise/EnvironmentSceneHead.kt').read_text()
        self.assertIn('const val PROFILE_ID="'+profile+'"',code)
        base=ROOT/'shared/src/commonTest/fixtures/environment/scene-head/device/scene-fixtures'
        data=(base/'fallback.json').read_bytes()
        self.assertEqual(hashlib.sha256(data).hexdigest(),'633dc1bb9244e933dca936a176ab5f5e3e64e0d79f5de7614e5acb3299f74421')
        fixture=json.loads(data)
        corpus=scene.load_manifest(scene.DEFAULT_MANIFEST.with_name('curated-corpus.json'))
        clip=next(c for c in corpus['recordings'] if c['id']==fixture['sourceId'])
        self.assertEqual(clip['partition'],'development')
        self.assertEqual(clip['sha256'],fixture['sourceSha256'])
        self.assertEqual(clip['license'],fixture['license'])
        pcm=(base/fixture['file']).read_bytes()
        self.assertEqual(len(pcm),fixture['seconds']*16000*2)
        self.assertEqual(hashlib.sha256(pcm).hexdigest(),fixture['sha256'])
        notice=(assets/'HEAD_NOTICE').read_text()
        for clip in corpus['recordings']:
            if clip['partition']=='development':
                self.assertIn(clip['id'],notice)
                self.assertIn(clip['licenseUrl'],notice)


if __name__=='__main__':unittest.main()
