"""Offline source-sensitivity experiment; orchestration only, decisions stay Kotlin."""
import argparse,hashlib,json,re,subprocess
from pathlib import Path
from scene_corpus import load_manifest
p=argparse.ArgumentParser(description=__doc__)
for arg in ('manifest','encoder','head','fit-report','cv-directory','evidence','references','jar','work-directory','output'):
 p.add_argument('--'+arg,type=Path,required=True)
a=p.parse_args();manifest=load_manifest(a.manifest)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
fit=json.loads(a.fit_report.read_text());registry=a.cv_directory/'folds.json';folds=json.loads(registry.read_text());assert folds['version']==1
assert fit['headSha256']==sha(a.head)
assert fit['sourceModelSha256']==folds['encoderSha256']==sha(a.encoder)==manifest['encoderSha256']
assert fit['corpusManifestSha256']==folds['corpusManifestSha256']==sha(a.manifest)
assert folds['C']==fit['C'] and folds['temperature']==fit['temperature']
expected={c['sourceGroup'] for c in manifest['recordings'] if c['partition']=='development'}
assert {f['sourceGroup'] for f in folds['folds']}==expected
assert len(folds['folds'])==len(expected)
components=[(a.head,fit['headSha256'],'full-development-fit')]
for f in folds['folds']:
 assert re.fullmatch(r'fold-\d{3}\.bin',f['file'])
 path=a.cv_directory/f['file'];assert sha(path)==f['sha256']
 components.append((path,f['sha256'],'excluding '+f['sourceGroup']))
assert 3<=len(components)<=32 and len({c[1] for c in components})==len(components)
a.work_directory.mkdir(parents=True,exist_ok=False)
index=['headSha256\treportSha256\tfile'];provenance=[]
for i,(head,hash_,description) in enumerate(components):
 output=a.work_directory/f'model-{i:03d}.tsv'
 command=['java','-cp',str(a.jar),'com.heysig.tools.SceneHeadReportKt',str(a.encoder),str(head),str(a.manifest),str(a.evidence),str(a.references),str(output)]
 result=subprocess.run(command,capture_output=True,text=True,timeout=60)
 if result.returncode:raise RuntimeError(result.stdout+result.stderr)
 index.append('\t'.join((hash_,sha(output),output.name)))
 provenance.append(dict(headSha256=hash_,reportSha256=sha(output),sourceExclusion=description))
 print(i+1,'/',len(components),description,flush=True)
index_path=a.work_directory/'heads.tsv';index_path.write_text('\n'.join(index)+'\n')
subprocess.run(['java','-cp',str(a.jar),'com.heysig.tools.SceneConsensusReportKt',str(index_path),str(a.output)],check=True,timeout=60)
a.output.with_suffix('.json').write_text(json.dumps(dict(version=1,policy='Unanimous class across full head and every source-excluded head at unchanged >=0.65 score; any abstention or disagreement abstains; score is minimum component softmax, not calibrated confidence.',notes='Offline research only. Training sets overlap; votes are not independent. Development results are sensitivity on fitted sources, NOT held-source-out CV. All validation sources have previously been viewed; this is regression evidence, not fresh acceptance.',manifestSha256=sha(a.manifest),evidenceSha256=sha(a.evidence),referencesSha256=sha(a.references),reporterJarSha256=sha(a.jar),foldRegistrySha256=sha(registry),decisionReportSha256=sha(a.output),components=provenance),indent=2)+'\n')
