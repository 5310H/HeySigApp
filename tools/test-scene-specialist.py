"""Integrity of research fallback inputs; synthetic rows do not establish audio accuracy."""
import subprocess,tempfile,unittest
from pathlib import Path

CLASSPATH='shared/build/scene-specialist-check.jar:shared/build/scene-head-report.jar'
HEADER='clip\treference\tpartition\tsourceGroup\tseconds\tgain\toffsetSeconds\tdecision\tscore\toutcome\n'
PRIMARY='example\tSMALL_GROUP\tvalidation\tsource/group\t3\t1.0\t0\tUNCERTAIN\t0.0\tABSTAIN\n'
SPECIALIST=PRIMARY.replace('UNCERTAIN\t0.0\tABSTAIN','SMALL_GROUP\t0.8\tCORRECT')

class SpecialistChecks(unittest.TestCase):
 def run_case(self,primary=PRIMARY,specialist=SPECIALIST):
  with tempfile.TemporaryDirectory() as temp:
   base=Path(temp);first=base/'primary.tsv';second=base/'specialist.tsv';output=base/'output.tsv'
   first.write_text(HEADER+primary);second.write_text(HEADER+specialist)
   result=subprocess.run(['java','-cp',CLASSPATH,'com.heysig.tools.SceneSpecialistReportKt',str(first),str(second),str(output)],capture_output=True,text=True)
   return result,output.read_text() if output.exists() else None
 def test_fallback_uses_actual_matching_report(self):
  result,output=self.run_case();self.assertEqual(result.returncode,0,result.stderr)
  self.assertEqual(output,HEADER+SPECIALIST);self.assertEqual(result.stdout.count('Input SHA-256'),2)
 def test_different_reference_window_and_duplicate_fail(self):
  for row in (SPECIALIST.replace('source/group','other/group'),SPECIALIST.replace('\t3\t','\t5\t'),SPECIALIST+SPECIALIST):
   with self.subTest(row=row):
    result,output=self.run_case(specialist=row);self.assertNotEqual(result.returncode,0);self.assertIsNone(output)
 def test_invalid_scores_outcomes_and_windows_fail(self):
  for row in (SPECIALIST.replace('0.8','NaN'),SPECIALIST.replace('0.8','0.64'),SPECIALIST.replace('CORRECT','WRONG'),SPECIALIST.replace('\t0\tSMALL','\t1\tSMALL')):
   with self.subTest(row=row):
    result,output=self.run_case(specialist=row);self.assertNotEqual(result.returncode,0);self.assertIsNone(output)

if __name__=='__main__':unittest.main()
