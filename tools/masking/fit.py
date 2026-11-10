"""Select regularization and uncertainty temperature using development-speaker folds only."""
import argparse,json,hashlib
from pathlib import Path
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import log_loss
from threadpoolctl import threadpool_limits
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);a=p.parse_args()
data=np.load(a.work/'features.npz');records=json.loads(str(data['records']))
indices=np.array([i for i,r in enumerate(records) if r['partition']=='development' and r['kind']=='mixture'])
X=data['features'][indices];y=np.array([records[i]['reference'] for i in indices]);speakers=np.array([records[i]['speaker'] for i in indices])
assert X.shape[1] in (2052,2058), 'Unknown masking feature format'
def softmax(logits):
    v=np.exp(logits-logits.max(axis=1,keepdims=True));return v/v.sum(axis=1,keepdims=True)
results=[];best=None
def scaled_features(scale):
    scaled=X.copy()
    if scaled.shape[1]==2058: scaled[:,2052:]*=scale
    return scaled
with threadpool_limits(limits=1):
    for scale in ((1.,4.,16.) if X.shape[1]==2058 else (1.,)):
        scaled=scaled_features(scale)
        for c in (.001,.01,.1,1.):
            logits=np.zeros((len(X),3))
            for speaker in sorted(set(speakers)):
                test=speakers==speaker;train=~test
                model=LogisticRegression(C=c,max_iter=2000).fit(scaled[train],y[train]);logits[test]=model.decision_function(scaled[test])
            for temperature in (1.,2.,4.,8.):
                loss=float(log_loss(y,softmax(logits/temperature),labels=[0,1,2]));results.append(dict(C=c,temperature=temperature,cancellationScale=scale,loss=loss))
                if best is None or loss<best['loss']: best=results[-1]
    model=LogisticRegression(C=best['C'],max_iter=2000).fit(scaled_features(best['cancellationScale']),y)
weights=model.coef_.copy()/best['temperature']
# Fold development-selected feature scaling into weights; device scalars stay bounded 0..1.
if X.shape[1]==2058: weights[:,2052:]*=best['cancellationScale']
head=('HSGMASK2\n2058\n' if X.shape[1]==2058 else 'HSGMASK1\n2052\n')+','.join(map(str,model.intercept_/best['temperature']))+'\n'+'\n'.join(','.join(map(str,row)) for row in weights)+'\n'
(a.work/'masking-head.txt').write_text(head)
(a.work/'fit.json').write_text(json.dumps(dict(selected=best,candidates=results,selection='development-speaker-only CV; backgrounds shared within inner folds; final evaluation excludes both speakers and background recordings; heldout previously viewed initial fit, not used to select C/temperature/cancellationScale',headSha256=hashlib.sha256(head.encode()).hexdigest()),indent=2)+'\n')
print(best,'head',hashlib.sha256(head.encode()).hexdigest(),flush=True)
