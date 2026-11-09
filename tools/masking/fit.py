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
def softmax(logits):
    v=np.exp(logits-logits.max(axis=1,keepdims=True));return v/v.sum(axis=1,keepdims=True)
results=[];best=None
with threadpool_limits(limits=1):
    for c in (.001,.01,.1,1.):
        logits=np.zeros((len(X),3))
        for speaker in sorted(set(speakers)):
            test=speakers==speaker;train=~test
            model=LogisticRegression(C=c,max_iter=2000).fit(X[train],y[train]);logits[test]=model.decision_function(X[test])
        for temperature in (1.,2.,4.,8.):
            loss=float(log_loss(y,softmax(logits/temperature),labels=[0,1,2]));results.append(dict(C=c,temperature=temperature,loss=loss))
            if best is None or loss<best['loss']: best=results[-1]
    model=LogisticRegression(C=best['C'],max_iter=2000).fit(X,y)
head='HSGMASK1\n2052\n'+','.join(map(str,model.intercept_/best['temperature']))+'\n'+'\n'.join(','.join(map(str,row/best['temperature'])) for row in model.coef_)+'\n'
(a.work/'masking-head.txt').write_text(head)
(a.work/'fit.json').write_text(json.dumps(dict(selected=best,candidates=results,selection='development-speaker-only CV; backgrounds shared within inner folds; final evaluation excludes both speakers and background recordings; heldout previously viewed initial fit, not used to select C/temperature',headSha256=hashlib.sha256(head.encode()).hexdigest()),indent=2)+'\n')
print(best,'head',hashlib.sha256(head.encode()).hexdigest(),flush=True)
