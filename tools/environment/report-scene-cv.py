"""Run exact Kotlin decisions for development-only held-source-out diagnostic heads."""
import argparse,csv,hashlib,json,re,subprocess
from pathlib import Path
from scene_corpus import load_manifest
p=argparse.ArgumentParser(description=__doc__)
for arg in ('manifest','encoder','cv-directory','evidence','references','jar','output'):
 p.add_argument('--'+arg,type=Path,required=True)
a=p.parse_args();manifest=load_manifest(a.manifest)
folds=json.loads((a.cv_directory/'folds.json').read_text())
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert folds['version']==1 and folds['encoderSha256']==sha(a.encoder)==manifest['encoderSha256']
assert folds['corpusManifestSha256']==sha(a.manifest)
expected={c['sourceGroup'] for c in manifest['recordings'] if c['partition']=='development'}
assert {f['sourceGroup'] for f in folds['folds']}<=expected
assert len({f['sourceGroup'] for f in folds['folds']})==len(folds['folds'])
with a.references.open() as f:refs=list(csv.DictReader(f,delimiter='\t'))
assert {r['sourceGroup'] for r in refs if r['partition']=='development'}=={f['sourceGroup'] for f in folds['folds']}
a.output.parent.mkdir(parents=True,exist_ok=True);header=None;rows=[]
for fold in folds['folds']:
 assert re.fullmatch(r'fold-\d{3}\.bin',fold['file'])
 head=a.cv_directory/fold['file'];assert sha(head)==fold['sha256']
 output=a.cv_directory/(head.stem+'-decisions.tsv')
 command=['java','-cp',str(a.jar),'com.heysig.tools.SceneHeadReportKt',str(a.encoder),str(head),str(a.manifest),str(a.evidence),str(a.references),str(output),fold['sourceGroup']]
 result=subprocess.run(command,capture_output=True,text=True,timeout=60)
 if result.returncode:raise RuntimeError(result.stdout+result.stderr)
 lines=output.read_text().splitlines()
 if header is None:header=lines[0]
 assert header==lines[0] and len(lines)-1==fold['heldOutVariants']
 rows.extend(lines[1:]);print(fold['sourceGroup'],len(lines)-1,'held-source-out decisions',flush=True)
assert len(rows)==sum(r['partition']=='development' for r in refs)
a.output.write_text(header+'\n'+'\n'.join(rows)+'\n')
print('Combined',len(rows),'development-only cross-validation variants; not unbiased independent acceptance')
