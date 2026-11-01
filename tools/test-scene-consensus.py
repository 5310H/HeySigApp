"""Offline consensus report integrity; synthetic votes are not audio accuracy evidence."""
import argparse,hashlib,subprocess,sys,tempfile,unittest
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--jar',type=Path,default=Path('shared/build/scene-head-report.jar'))
a,remaining=p.parse_known_args();JAR=a.jar.resolve()
HEADER='clip\treference\tpartition\tsourceGroup\tseconds\tgain\toffsetSeconds\tdecision\tscore\toutcome\n'
ROW='example\tQUIET\tdevelopment\tsource/group\t3\t1.0\t0\tQUIET\t0.8\tCORRECT\n'
class ConsensusChecks(unittest.TestCase):
 def run_case(self,mutate=None):
  with tempfile.TemporaryDirectory() as temp:
   base=Path(temp);entries=['headSha256\treportSha256\tfile']
   for i in range(3):
    content=(HEADER+ROW).encode();file=base/f'model-{i:03d}.tsv';file.write_bytes(content)
    entries.append(f'{i+1:064x}\t{hashlib.sha256(content).hexdigest()}\t{file.name}')
   if mutate:mutate(base,entries)
   index=base/'heads.tsv';index.write_text('\n'.join(entries)+'\n');output=base/'result.tsv'
   result=subprocess.run(['java','-cp',str(JAR),'com.heysig.tools.SceneConsensusReportKt',str(index),str(output)],capture_output=True,text=True)
   return result,output.read_text() if output.exists() else None
 def test_identical_votes_accept_at_minimum_score(self):
  r,output=self.run_case();self.assertEqual(r.returncode,0,r.stderr);self.assertIn('\tQUIET\t0.8\tCORRECT\n',output)
 def test_changed_report_duplicate_head_and_path_escape_fail(self):
  def changed(base,entries):(base/'model-000.tsv').write_text(HEADER+ROW.replace('0.8','0.9'))
  def duplicate(base,entries):entries[2]=entries[1].split('\t')[0]+'\t'+'\t'.join(entries[2].split('\t')[1:])
  def escape(base,entries):entries[1]=entries[1].replace('model-000.tsv','../model-000.tsv')
  for mutate in (changed,duplicate,escape):
   with self.subTest(mutate=mutate.__name__):
    r,output=self.run_case(mutate);self.assertNotEqual(r.returncode,0);self.assertIsNone(output)
 def test_different_windows_fail_even_with_updated_hash(self):
  def mutate(base,entries):
   content=(HEADER+ROW.replace('source/group','different/group')).encode();(base/'model-000.tsv').write_bytes(content)
   parts=entries[1].split('\t');parts[1]=hashlib.sha256(content).hexdigest();entries[1]='\t'.join(parts)
  r,output=self.run_case(mutate);self.assertNotEqual(r.returncode,0);self.assertIsNone(output)
if __name__=='__main__':unittest.main(argv=[sys.argv[0]]+remaining)
