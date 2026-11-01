import argparse,json,hashlib,struct
from pathlib import Path
import numpy as np
from scene_corpus import load_manifest,DEFAULT_MANIFEST,LABELS
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import log_loss
from threadpoolctl import threadpool_limits
p=argparse.ArgumentParser(description='Fit an exploratory scene head with source-group CV on development only.')
p.add_argument('--features',type=Path,required=True)
p.add_argument('--manifest',type=Path,default=DEFAULT_MANIFEST)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--cv-output',type=Path,help='New directory for development-only leave-source-group-out heads')
a=p.parse_args()
manifest=load_manifest(a.manifest)
archive=np.load(a.features,allow_pickle=False);assert str(archive['manifestSha256'])==hashlib.sha256(a.manifest.read_bytes()).hexdigest();records=json.loads(str(archive['records']));features=archive['features'];events=archive['events']
assert str(archive['modelSha256'])==manifest['encoderSha256'], 'Feature encoder does not match corpus'
assert events.shape==(len(records),521) and np.all(np.isfinite(events)) and np.all((events>=0)&(events<=1))
labels=list(LABELS)
assert features.shape==(len(records),2048) and 1<=len(records)<=4096
assert np.all(np.isfinite(features)) and np.all((features>=0)&(features<=1))
assert np.allclose(np.linalg.norm(features,axis=1),1.,atol=1e-8)
by_id={c['id']:c for c in manifest['recordings']}
for row in records:
    clip=by_id[row['clip']]
    assert all(row[key]==clip[key] for key in ('reference','partition','author','sourceGroup'))
y=np.array([labels.index(r['reference']) for r in records]);groups=np.array([r['sourceGroup'] for r in records]);split=np.array([r['partition'] for r in records]);train=split=='development'
assert all(set(groups[train & (y==c)]).__len__()>=2 for c in range(6)), 'Need >=2 independent development groups per class'
assert set(groups[train]).isdisjoint(set(groups[~train]))
assert set(y[train])==set(range(6)) and set(y[~train])==set(range(6)), 'Need usable recordings in every scene and partition'
def sample_weights(mask):
 ids=[(records[i]['sourceGroup'],int(y[i])) for i in np.where(mask)[0]]
 # Each source-group/class contributes one total unit; correlated windows do not add weight.
 classes={c:len(set(groups[mask & (y==c)])) for c in range(6)}
 count=len(set(ids));return np.array([count/6/classes[y[i]]/ids.count((records[i]['sourceGroup'],int(y[i]))) for i in np.where(mask)[0]])
def fit(mask,C):return LogisticRegression(C=C,max_iter=2000,tol=1e-7).fit(features[mask],y[mask],sample_weight=sample_weights(mask))
proposals=[]
with threadpool_limits(1):
 for C in (1.,10.,100.):
  oof=np.zeros((sum(train),6));train_ids=np.where(train)[0];position={i:k for k,i in enumerate(train_ids)}
  for group in sorted(set(groups[train])):
   held=train & (groups==group);fold=train & (groups!=group)
   assert len(set(y[fold]))==6
   model=fit(fold,C);scores=model.decision_function(features[held]);oof[[position[i] for i in np.where(held)[0]]]=scores
  for temperature in (.5,1.,2.,4.):
   scores=oof/temperature;scores-=scores.max(axis=1,keepdims=True);prob=np.exp(scores);prob/=prob.sum(axis=1,keepdims=True)
   loss=log_loss(y[train],prob,sample_weight=sample_weights(train),labels=np.arange(6))
   proposals.append((loss,C,temperature))
  print('C',C,'OOF top1',np.mean(oof.argmax(axis=1)==y[train]),flush=True)
 loss,C,temp=min(proposals);model=fit(train,C)
centroids=[]
for c in range(6):
 mask=train & (y==c);center=np.average(features[mask],axis=0,weights=sample_weights(train)[y[train]==c]);center/=np.linalg.norm(center);centroids.append(center)
centroids=np.stack(centroids)
model.coef_=model.coef_.astype(np.float32).astype(np.float64)
model.intercept_=model.intercept_.astype(np.float32).astype(np.float64)
centroids=centroids.astype(np.float32).astype(np.float64)
raw=model.decision_function(features)/temp;raw-=raw.max(axis=1,keepdims=True);prob=np.exp(raw);prob/=prob.sum(axis=1,keepdims=True)
model_hash=str(archive['modelSha256']);data=bytearray(b'HSGENV1\n'+bytes.fromhex(model_hash)+struct.pack('<iiifff',1024,6,1,temp,.65,.15))
for c in range(6):data+=struct.pack('<f',model.intercept_[c])+model.coef_[c].astype('<f4').tobytes()+centroids[c].astype('<f4').tobytes()
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_bytes(data)
report={'C':C,'temperature':temp,'developmentGroupCVLogLoss':loss,'sourceModelSha256':model_hash,'corpusManifestSha256':str(archive['manifestSha256']),'featuresArchiveSha256':hashlib.sha256(a.features.read_bytes()).hexdigest(),'headSha256':hashlib.sha256(data).hexdigest(),'cases':[],'notes':'Exploratory supervised head. Provisional creator/scene references; NOT physical or independent six-class qualification.'}
for i,r in enumerate(records):
 order=np.argsort(prob[i])[::-1];winner=int(order[0]);confidence=float(prob[i,winner]);margin=float(prob[i,winner]-prob[i,order[1]]);similarity=float(features[i]@centroids[winner])
 report['cases'].append(dict(r,winner=labels[winner],confidence=confidence,margin=margin,similarity=similarity))
for part in ('development','validation'):
 rows=[r for r in report['cases'] if r['partition']==part]
 print(part,'cases',len(rows),'top1',sum(r['winner']==r['reference'] for r in rows),flush=True)
 for name in labels:
  rr=[r for r in rows if r['reference']==name];print(name,len(rr),'top1',sum(r['winner']==name for r in rr),flush=True)
a.output.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n')
print('SELECTED C',C,'temperature',temp,'HEAD',report['headSha256'],flush=True)

if a.cv_output is not None:
 a.cv_output.mkdir(parents=True,exist_ok=False)
 folds=[]
 with threadpool_limits(1):
  for index,group in enumerate(sorted(set(groups[train]))):
   mask=train & (groups!=group);fold_model=fit(mask,C)
   blob=bytearray(b'HSGENV1\n'+bytes.fromhex(model_hash)+struct.pack('<iiifff',1024,6,1,temp,.65,.15))
   for c in range(6):
    center=np.average(features[mask & (y==c)],axis=0,weights=sample_weights(mask)[y[mask]==c]);center/=np.linalg.norm(center)
    blob+=struct.pack('<f',fold_model.intercept_[c])+fold_model.coef_[c].astype('<f4').tobytes()+center.astype('<f4').tobytes()
   filename=f'fold-{index:03d}.bin';(a.cv_output/filename).write_bytes(blob)
   folds.append(dict(sourceGroup=group,file=filename,sha256=hashlib.sha256(blob).hexdigest(),heldOutVariants=int(sum(train & (groups==group)))))
 (a.cv_output/'folds.json').write_text(json.dumps(dict(version=1,C=C,temperature=temp,encoderSha256=model_hash,corpusManifestSha256=str(archive['manifestSha256']),folds=folds,notes='Development-only diagnostic, not nested unbiased validation: C and temperature were selected using these development groups. Every fold excludes its source group from coefficients AND centroids.'),indent=2)+'\n')
 print('Wrote',len(folds),'development-only CV heads',flush=True)
