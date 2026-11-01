from pathlib import Path
import json,hashlib,csv,subprocess,argparse,numpy as np
from scene_corpus import load_manifest, cached_source, DEFAULT_MANIFEST
p=argparse.ArgumentParser(description="Generate test-only licensed development regressions, not accuracy evidence.")
p.add_argument("--manifest",type=Path,default=DEFAULT_MANIFEST)
p.add_argument("--cache",type=Path,required=True)
p.add_argument("--fit-report",type=Path,required=True)
p.add_argument("--decision-report",type=Path,required=True)
p.add_argument("--output",type=Path,required=True)
a=p.parse_args()
manifest=load_manifest(a.manifest);clips={c['id']:c for c in manifest['recordings']}
rows=list(csv.DictReader(a.decision_report.open(),delimiter='\t'))
fit=json.loads(a.fit_report.read_text());scores={(r['clip'],str(r['seconds']),str(r['gain']),str(r['offsetSeconds'])):r['confidence'] for r in fit['cases']}
base=a.output;base.mkdir(parents=True,exist_ok=True)
fixtures=[]
for label in ('QUIET','CONVERSATION','SMALL_GROUP','RESTAURANT','LARGE_GROUP','NON_SPEECH_NOISE'):
 candidates=[r for r in rows if r['partition']=='development' and r['reference']==label and r['outcome']=='CORRECT']
 row=max(candidates,key=lambda r:float(r['score']));clip=clips[row['clip']]
 path=cached_source(a.cache,clip)
 raw=subprocess.check_output(['ffmpeg','-v','error','-nostdin','-i',str(path),'-t','30','-ac','1','-ar','16000','-f','s16le','pipe:1'])
 start=int(row['offsetSeconds'])*16000;count=int(row['seconds'])*16000
 pcm=(np.frombuffer(raw,dtype='<i2')[start:start+count].astype(np.float64)*float(row['gain'])).astype('<i2')
 assert len(pcm)==count
 name=label.lower()+'.pcm';data=pcm.tobytes();(base/name).write_bytes(data)
 entry=dict(file=name,sha256=hashlib.sha256(data).hexdigest(),seconds=int(row['seconds']),expectedEnvironment=label,
 expectedScore=scores[(row['clip'],row['seconds'],row['gain'],row['offsetSeconds'])],
 source=clip['source'],author=clip['author'],license=clip['license'],licenseUrl=clip['licenseUrl'],sourceId=clip['id'],
 sourceSha256=clip['sha256'],transforms=f"mono 16 kHz PCM16, source offset {row['offsetSeconds']} s, duration {row['seconds']} s, digital gain {row['gain']}",
 notes='Development/training recording; regression only, NOT held-out classifier acceptance.')
 entry['referenceBasis']=clip['referenceBasis']
 entry['sourceTransforms']=clip.get('transforms','Public preview decoded to mono PCM16 for regression')
 fixtures.append(entry)
 print(label,clip['id'],row['seconds'],row['gain'],row['score'])
(base/'manifest.json').write_text(json.dumps({'version':1,'encoderSha256':manifest['encoderSha256'],'headSha256':fit['headSha256'],'recordings':fixtures},indent=2)+'\n')
print('PCM bytes',sum((base/f['file']).stat().st_size for f in fixtures))
