"""Check evaluation separation and rejection of corrupted scalar evidence."""
import csv,json,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MANIFEST=ROOT/'shared/src/commonTest/fixtures/environment/scene-head/expanded-corpus.json'
REPORT=ROOT/'docs/acceptance/data/scene-expansion-candidate-2026-11-01.tsv'
class ReportChecks(unittest.TestCase):
 def run_report(self,rows=None,viewed=False):
  with tempfile.TemporaryDirectory() as tmp:
   base=Path(tmp);report=REPORT
   if rows is not None:
    report=base/'input.tsv'
    with report.open('w') as f:
     writer=csv.DictWriter(f,fieldnames=list(rows[0]),delimiter='\t');writer.writeheader();writer.writerows(rows)
   output=base/'output.json'
   command=['python3',str(ROOT/'tools/environment/summarize-scene-report.py'),'--manifest',str(MANIFEST),'--report',str(report),'--output',str(output)]
   if viewed:command.append('--all-validation-viewed')
   result=subprocess.run(command,capture_output=True,text=True)
   return result,json.loads(output.read_text()) if output.exists() else None
 def test_new_evaluation_is_separate_from_previously_viewed(self):
  process,report=self.run_report();self.assertEqual(process.returncode,0,process.stderr)
  self.assertEqual(report['evaluations']['freshValidation']['total'],dict(variants=16,recordings=2,sourceGroups=2,CORRECT=6,WRONG=0,ABSTAIN=10))
  self.assertEqual(report['evaluations']['previouslyViewedValidation']['total']['WRONG'],9)
 def test_followup_marks_all_evaluation_as_viewed(self):
  process,report=self.run_report(viewed=True);self.assertEqual(process.returncode,0,process.stderr)
  self.assertEqual(report['evaluations']['freshValidation']['total']['variants'],0)
  self.assertEqual(report['evaluations']['previouslyViewedValidation']['total']['variants'],124)
  self.assertEqual(report['evaluations']['previouslyViewedValidation']['total']['WRONG'],9)
 def test_duplicate_and_mislabeled_evidence_fails(self):
  with REPORT.open() as f:original=list(csv.DictReader(f,delimiter='\t'))
  for field,value in [('partition','development' if original[-1]['partition']=='validation' else 'validation'),('score','nan'),('outcome','WRONG'),('gain','0.5')]:
   rows=[dict(r) for r in original];rows[-1][field]=value
   with self.subTest(field=field):
    result,report=self.run_report(rows);self.assertNotEqual(result.returncode,0);self.assertIsNone(report)
  result,report=self.run_report(original+[original[-1]]);self.assertNotEqual(result.returncode,0);self.assertIsNone(report)
 def test_integrated_fallback_preserves_primary_evidence(self):
  base=ROOT/'docs/acceptance/data'
  with (base/'window-curation-baseline-2026-11-01.tsv').open() as f:primary=list(csv.DictReader(f,delimiter='\t'))
  with (base/'primary-preserving-fallback-curated-2026-11-01.tsv').open() as f:combined=list(csv.DictReader(f,delimiter='\t'))
  self.assertEqual(len(primary),len(combined));additions=[]
  for before,after in zip(primary,combined):
   self.assertEqual([before[k] for k in list(before)[:7]],[after[k] for k in list(after)[:7]])
   if before['decision']!='UNCERTAIN' or float(before['score'])>0:self.assertEqual(before,after)
   elif after['decision']!='UNCERTAIN':
    self.assertEqual(after['decision'],'SMALL_GROUP');self.assertGreaterEqual(float(after['score']),.65);additions.append(after)
  self.assertEqual(sum(r['partition']=='validation' for r in additions),4)
  self.assertTrue(all(r['outcome']=='CORRECT' for r in additions))
if __name__=='__main__':unittest.main()
