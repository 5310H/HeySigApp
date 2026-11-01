"""Public timing extraction and interval math; no scene decisions or user audio."""
import importlib.util,json,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('ami_timing_audit',ROOT/'tools/environment/ami_timing_audit.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class TimingChecks(unittest.TestCase):
 def test_text_ignored_punctuation_and_nonwords_excluded_and_bounds_clipped(self):
  xml=b'<root><w starttime="119" endtime="121">ignored text</w><w starttime="125" endtime="126" punc="true">.</w><vocalsound starttime="130" endtime="131"/><w starttime="149" endtime="151">more text</w></root>'
  self.assertEqual(m.word_intervals(xml),[[0.,1.],[29.,30.]])
  self.assertEqual(m.word_intervals(xml.replace(b'ignored text',b'other words')),[[0.,1.],[29.,30.]])
 def test_same_speaker_overlap_not_double_counted(self):
  speakers=dict(A=[[0.,2.],[1.,3.]],B=[[1.,2.]],C=[[2.,3.]],D=[])
  r=m.summarize(speakers,0,3)
  self.assertEqual(r['speakersWithWords'],3);self.assertEqual(r['perSpeakerWordSeconds'],dict(A=3.,B=1.,C=1.,D=0.))
  self.assertEqual(r['wordActivityHistogramSeconds'],[0.,1.,2.,0.,0.]);self.assertAlmostEqual(r['wordOverlapFraction'],2/3)
  silent=m.summarize(speakers,10,3);self.assertEqual(silent['speakersWithWords'],0);self.assertEqual(silent['wordActivityHistogramSeconds'][0],3.)
  with self.assertRaises(ValueError):m.summarize(speakers,1,3)
  with self.assertRaises(ValueError):m.merge([[1.,float('nan')]],0,3)
 def test_public_fixture_contains_only_metadata_and_bounded_intervals(self):
  path=ROOT/'shared/src/commonTest/fixtures/environment/scene-head/ami-word-timings.json';data=json.loads(path.read_text())
  self.assertEqual(data['license'],'CC-BY-4.0');self.assertTrue(data['attribution']);self.assertEqual(len(data['sources']),16)
  self.assertEqual(len({(s['meeting'],s['speaker']) for s in data['sources']}),16)
  for s in data['sources']:
   self.assertEqual(set(s),{'meeting','speaker','source','xmlSha256','intervals'})
   self.assertEqual(len(s['xmlSha256']),64);m.merge(s['intervals'],0,30)
if __name__=='__main__':unittest.main()
