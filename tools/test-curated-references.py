"""Per-window annotation integrity and source/participant separation, not audio accuracy."""
import copy,hashlib,json,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools/environment'))
from curate_ami_windows import select_windows,POLICY,validate_plan
from ami_timing_audit import span_activity
from scene_corpus import capture_windows,explicit_windows,load_manifest,DEFAULT_MANIFEST
BASE=DEFAULT_MANIFEST.parent
class CurationChecks(unittest.TestCase):
 def test_first_eligible_and_spacing_with_no_fabrication(self):
  speakers=dict(A=[[0.,2.],[20.,22.]],B=[[2.,4.],[22.,24.]],C=[[4.,6.],[24.,26.]],D=[])
  windows=select_windows(speakers,30,[],POLICY)
  self.assertEqual([(w['startSeconds'],w['durationSeconds']) for w in windows],[(0,10),(15,10)])
  self.assertEqual(len(select_windows(speakers,30,[[0,10]],POLICY)),1)
  self.assertEqual(select_windows(dict(A=[[0.,30.]],B=[],C=[],D=[]),30,[],POLICY),[])
  weakened=copy.deepcopy(POLICY);weakened['minimumSpeakers']=2
  with self.assertRaises(ValueError):select_windows(speakers,30,[],weakened)
 def test_only_annotated_window_is_expanded(self):
  self.assertEqual(capture_windows({},480000),[(offset,seconds) for offset in (0,160000,320000) for seconds in (3,5,10)])
  clip=dict(windows=[dict(offsetSeconds=0,seconds=10)])
  self.assertEqual(capture_windows(clip,160000),[(0,10)])
  with self.assertRaises(ValueError):capture_windows(clip,159999)
  for windows in ([],[dict(offsetSeconds=0,seconds=True)],[dict(offsetSeconds=0,seconds=10)]*2,[dict(offsetSeconds=1,seconds=3)]):
   with self.subTest(windows=windows):
    with self.assertRaises(ValueError):explicit_windows(dict(windows=windows))
 def test_frozen_data_preserves_sources_and_excludes_participants(self):
  manifest=load_manifest(BASE/'curated-corpus.json');old=load_manifest(BASE/'expanded-corpus.json')
  self.assertEqual([c for c in manifest['recordings'] if not c['id'].startswith('ami-')],[c for c in old['recordings'] if not c['id'].startswith('ami-')])
  clips=[c for c in manifest['recordings'] if c['id'].startswith('ami-')];self.assertEqual(len(clips),38)
  fresh=[c for c in clips if c['id'] in manifest['freshValidationIds']];self.assertEqual(len(fresh),11)
  oldpeople={p for c in old['recordings'] for p in c.get('participants',[])}
  self.assertTrue({p for c in fresh for p in c['participants']}.isdisjoint(oldpeople))
  trainpeople={p for c in clips if c['partition']=='development' for p in c['participants']}
  self.assertTrue({p for c in fresh for p in c['participants']}.isdisjoint(trainpeople))
 def test_plan_rejects_identity_leakage_and_invalid_window_geometry(self):
  plan=json.loads((BASE/'curation-plan.json').read_text());validate_plan(plan)
  altered=copy.deepcopy(plan);altered['meetings'][-1]['participantsByChannel']['A']=plan['meetings'][0]['participantsByChannel']['A']
  with self.assertRaises(ValueError):validate_plan(altered)
  altered=copy.deepcopy(plan);altered['meetings'][0]['meeting']='../meeting'
  with self.assertRaises(ValueError):validate_plan(altered)
  for samples in (-1,480001,True):
   with self.assertRaises(ValueError):capture_windows({},samples)
 def test_all_references_reproduce_from_numeric_word_activity(self):
  manifest=load_manifest(BASE/'curated-corpus.json');timings=json.loads((BASE/'curation-word-timings.json').read_text())
  hashes={name:hashlib.sha256((BASE/file).read_bytes()).hexdigest() for name,file in [('planSha256','curation-plan.json'),('timingsSha256','curation-word-timings.json'),('selectionSha256','curated-windows.json')]}
  self.assertEqual(manifest['curation'],hashes)
  channels={(s['meeting'],s['speaker']):s for s in timings['sources']}
  for clip in manifest['recordings']:
   if not clip['id'].startswith('ami-'):continue
   meeting=clip['file'].split('-w')[0];excerpt=clip['audioExcerpt'];reference=clip['windowReference']
   speakers={letter:channels[(meeting,letter)]['intervals'] for letter in 'ABCD'}
   stats=span_activity(speakers,excerpt['startSeconds'],excerpt['durationSeconds'])
   self.assertEqual(stats,reference['activity']);self.assertEqual(explicit_windows(clip),[(0,excerpt['durationSeconds'])])
   self.assertGreaterEqual(sum(v>=.5 for v in stats['perSpeakerWordSeconds'].values()),3)
   self.assertGreaterEqual(1-stats['wordActivityHistogramSeconds'][0]/excerpt['durationSeconds'],.4)
   self.assertLessEqual(stats['wordOverlapFraction'],.2)
   self.assertEqual(reference['planSha256'],hashes['planSha256']);self.assertEqual(reference['timingsSha256'],hashes['timingsSha256'])
if __name__=='__main__':unittest.main()
