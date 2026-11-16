"""Developer-only fixed TV head. Source/author partitions precede fitting; no user audio."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import numpy as np
from sklearn.linear_model import LogisticRegression
from threadpoolctl import threadpool_limits
from tflite_runtime.interpreter import Interpreter

ROOT=Path(__file__).resolve().parents[2]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',type=Path,required=True)
    p.add_argument('--cache',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
    manifest=json.loads(a.manifest.read_text()); clips=manifest['recordings']
    model=ROOT/'audioAndroid/src/main/assets/environment/yamnet.tflite'
    assert hashlib.sha256(model.read_bytes()).hexdigest()==manifest['encoderSha256']=='b2fc8fc3ff9c960bde60b5fea71335d4cb2a35c4d1b011c3a80927fd64d8a2e4'
    assert {r['group'] for r in clips if r['partition']=='development'}.isdisjoint({r['group'] for r in clips if r['partition']=='validation'})
    engine=Interpreter(model_path=str(model),num_threads=1);engine.allocate_tensors()
    inp=engine.get_input_details()[0];out=engine.get_output_details()[1]
    records=[];features=[]
    for clip in clips:
        assert Path(clip['file']).name==clip['file'] and clip['reference'] in ('TV','OTHER')
        assert clip['license'] in ('CC0-1.0','CC-BY-3.0','CC-BY-4.0')
        path=a.cache/clip['file']
        assert hashlib.sha256(path.read_bytes()).hexdigest()==clip['sha256']
        for offset in clip['offsets']:
            assert type(offset) is int and offset>=0
            raw=subprocess.check_output(['ffmpeg','-v','error','-nostdin','-i',str(path),'-ss',str(offset),'-t','10','-ac','1','-ar','16000','-f','s16le','pipe:1'])
            pcm=np.frombuffer(raw,dtype='<i2');assert len(pcm)==160000,clip['id']
            for gain in (.25,1.0):
                samples=(pcm.astype(np.float64)*gain).astype(np.int16).astype(np.float64)/32768
                rms=np.sqrt(np.mean(samples*samples));peak=np.max(np.abs(samples))
                assert peak>0
                factor=max(1.,min(16.,.05/max(rms,1e-12),.95/peak));samples=(samples*factor).astype(np.float32)
                frames=[]
                for start in range(0,len(samples)-15600+1,7680):
                    engine.set_tensor(inp['index'],samples[start:start+15600]);engine.invoke()
                    frames.append(engine.get_tensor(out['index']).reshape(-1).astype(np.float64)+128)
                frame=np.stack(frames);feature=np.concatenate([frame.mean(axis=0),frame.std(axis=0)]);feature/=np.linalg.norm(feature)
                features.append(feature)
                records.append(dict(clip=clip['id'],author=clip['author'],group=clip['group'],partition=clip['partition'],reference=clip['reference'],offset=offset,gain=gain))
        print(clip['id'],len(records),flush=True)
    X=np.stack(features)*20;y=np.array([int(r['reference']=='TV') for r in records])
    train=np.array([r['partition']=='development' for r in records]);groups=np.array([r['group'] for r in records])
    assert set(y[train])=={0,1} and set(y[~train])=={0,1}
    def weights(mask):
        ids=[(groups[i],y[i]) for i in np.where(mask)[0]]
        counts={c:len(set(groups[mask & (y==c)])) for c in (0,1)}
        return np.array([len(ids)/2/counts[y[i]]/ids.count((groups[i],y[i])) for i in np.where(mask)[0]])
    def fit(mask,c):return LogisticRegression(C=c,max_iter=2000,tol=1e-8).fit(X[mask],y[mask],sample_weight=weights(mask))
    candidates=[];best=None
    with threadpool_limits(1):
        for c in (.01,.1,1.,10.):
            oof=np.zeros(len(y))
            for group in sorted(set(groups[train])):
                test=train & (groups==group);fold=train & ~test
                assert set(y[fold])=={0,1}
                oof[test]=fit(fold,c).predict_proba(X[test])[:,1]
            for low in (.1,.2,.3):
                for high in (.7,.8,.9):
                    predicted=np.where(oof>=high,1,np.where(oof<=low,0,-1))
                    false=int(np.sum(train & (y==0) & (predicted==1)))
                    missed=int(np.sum(train & (y==1) & (predicted==0)))
                    unknown=int(np.sum(train & (predicted==-1)))
                    cost=float(np.sum(weights(train)*np.where(predicted[train]==-1,1,np.where(predicted[train]==y[train],0,np.where(y[train]==0,10,2)))))
                    row=dict(C=c,low=low,high=high,cost=cost,falseTV=false,missedTV=missed,uncertain=unknown)
                    candidates.append(row)
                    if best is None or cost<best['cost']:best=row
        fitted=fit(train,best['C'])
    head='HSGTV1\n'+str(best['low'])+','+str(best['high'])+'\n'+str(fitted.intercept_[0])+'\n'+','.join(map(str,fitted.coef_[0]))+'\n'
    (a.output/'television-head.txt').write_text(head)
    scores=fitted.predict_proba(X)[:,1];predicted=np.where(scores>=best['high'],1,np.where(scores<=best['low'],0,-1))
    summary={}
    for name,mask in [('development',train),('validation',~train)]:
        summary[name]=dict(cases=int(mask.sum()),falseTV=int(np.sum(mask & (y==0) & (predicted==1))),
            missedTV=int(np.sum(mask & (y==1) & (predicted==0))),uncertain=int(np.sum(mask & (predicted==-1))),
            correct=int(np.sum(mask & (predicted==y))))
    report=dict(selected=best,candidates=candidates,summary=summary,encoderSha256=manifest['encoderSha256'],
        headSha256=hashlib.sha256(head.encode()).hexdigest(),manifestSha256=hashlib.sha256(a.manifest.read_bytes()).hexdigest(),
        selection='Leave-source/author-group-out development only; equal weight per group/class. Validation groups excluded from fit and selection. TV authors do not cross partitions; AMI groups use original participant/session split.',
        limitations='Small public corpus, including processed public-domain TV proxies. Correlated windows are not independent setups. Radio/phone playback and physical device acceptance remain separate.',
        cases=[dict(**r,score=float(s),predicted='UNCERTAIN' if q==-1 else 'TV' if q else 'OTHER') for r,s,q in zip(records,scores,predicted)])
    (a.output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    np.savez(a.output/'features.npz',features=np.stack(features),records=json.dumps(records))
    print(json.dumps(dict(selected=best,summary=summary)),flush=True)

if __name__=='__main__':main()
