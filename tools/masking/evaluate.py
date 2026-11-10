"""Development-only masking mixes, frozen splits, feature extraction and fit. Never device/user audio."""
import argparse, hashlib, json, subprocess, sys, time
from pathlib import Path
import numpy as np
from scipy.signal import lfilter
from ai_edge_litert.interpreter import Interpreter
from sklearn.linear_model import LogisticRegression
from threadpoolctl import threadpool_limits
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'environment'))
from scene_corpus import load_manifest,cached_source

p=argparse.ArgumentParser();p.add_argument('--speech',type=Path,required=True);p.add_argument('--background',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--cancellation-jar',type=Path);a=p.parse_args()
a.output.mkdir(parents=True,exist_ok=True)
root=Path(__file__).resolve().parents[2]
manifest=load_manifest(root/'shared/src/commonTest/fixtures/environment/scene-head/expanded-corpus.json')
speech=json.loads((a.speech/'speech.json').read_text())
labels=(root/'audioAndroid/src/main/assets/environment/labels.txt').read_text().splitlines()
model=root/'audioAndroid/src/main/assets/environment/yamnet.tflite'
assert hashlib.sha256(model.read_bytes()).hexdigest()==manifest['encoderSha256']
engine=Interpreter(model_path=str(model),num_threads=1);engine.allocate_tensors();inp=engine.get_input_details()[0];outs=engine.get_output_details()
def decode(path):
    raw=subprocess.check_output(['ffmpeg','-v','error','-nostdin','-i',str(path),'-t','10','-ac','1','-ar','16000','-f','s16le','pipe:1'])
    return np.frombuffer(raw,dtype='<i2').astype(np.float64)/32768
def normalize(x): return x/max(1e-12,np.sqrt(np.mean(x*x)))
def acoustic(pcm):
    x=pcm.astype(np.float64)/32768; frames=x.reshape(-1,320);energy=np.mean(frames*frames,axis=1)
    q=np.sort(energy);lo=q[int((len(q)-1)*.1)];hi=q[int((len(q)-1)*.9)]
    depth=np.clip(10*np.log10(max(hi,1e-12)/max(lo,1e-12))/60,0,1)
    # 50Hz envelope; 4–16Hz modulation fraction and 40ms pitch autocorrelation.
    envelope=np.sqrt(energy); centered=envelope-envelope.mean();total=np.sum(centered*centered)*len(centered)/2
    powers=sum(abs(np.sum(centered*np.exp(-2j*np.pi*f*np.arange(len(centered))/50)))**2 for f in range(4,17))
    modulation=min(1.,powers/max(1e-12,total))
    voiced=[]
    for start in range(0,len(x)-640+1,1600):
        frame=x[start:start+640];frame=frame-frame.mean();best=0.
        for lag in range(64,321,8):
            left=frame[:-lag];right=frame[lag:];den=np.sqrt(np.sum(left*left)*np.sum(right*right))
            if den>1e-10: best=max(best,float(np.sum(left*right)/den))
        voiced.append(best)
    crossing=np.mean((x[1:]>0)!=(x[:-1]>0))
    return np.array([depth,modulation,np.mean(voiced),crossing])
cancellation = subprocess.Popen(['java','-jar',str(a.cancellation_jar)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=False) if a.cancellation_jar else None
def extract(samples):
    pcm=np.clip(np.rint(samples*32768),-32768,32767).astype(np.int16)
    x=pcm.astype(np.float64)/32768;rms=np.sqrt(np.mean(x*x));peak=np.max(np.abs(x))
    gain=max(1.,min(16.,.05/max(rms,1e-12),.95/max(peak,1e-12)));x=(x*gain).astype(np.float32)
    embeddings=[];events=[]
    for start in range(0,len(x)-15600+1,7680):
        engine.set_tensor(inp['index'],x[start:start+15600]);engine.invoke()
        events.append(engine.get_tensor(outs[0]['index']).reshape(-1).astype(np.float64))
        embeddings.append(engine.get_tensor(outs[1]['index']).reshape(-1).astype(np.float64)+128)
    pooled=np.concatenate([np.mean(embeddings,axis=0),np.std(embeddings,axis=0)]);pooled/=np.linalg.norm(pooled)
    extra=[]
    if cancellation:
        cancellation.stdin.write(pcm.astype('<i2').tobytes());cancellation.stdin.flush()
        extra=list(map(float,cancellation.stdout.readline().decode().strip().split(',')))
        assert len(extra)==6 and all(np.isfinite(extra))
    feat=np.concatenate([pooled*20,acoustic(pcm),extra])
    return feat,np.mean(events,axis=0),20*np.log10(max(rms,1e-6)),20*np.log10(max(peak,1e-6))
backgroundIds={'development':['fs-363712','fs-260121','fsd-201734','fsd-212187','fs-533833'],'heldout':['fsd-414297','fsd-338254','fsd-156091','fsd-423267','fsd-389755']}
sources={c['id']:c for c in manifest['recordings']}; bgs={}
for partition,ids in backgroundIds.items():
    for id in ids:
        c=sources[id];assert c['partition']==('validation' if partition=='heldout' else 'development')
        samples=decode(cached_source(a.background,c));assert len(samples)>=8000,id
        # Short background-only excerpts are explicitly tiled for mixing, never speech sources.
        if len(samples)<48000: samples=np.tile(samples,int(np.ceil(48000/len(samples))))
        bgs[id]=normalize(samples[:48000])
speechPcm={}
for c in speech['recordings']:
    path=a.speech/c['file'];assert hashlib.sha256(path.read_bytes()).hexdigest()==c['sha256']
    speechPcm[c['id']]=normalize(decode(path)[:48000])
records=[];features=[];events=[]
def add(x,record):
    feat,event,rms,peak=extract(x);record.update(rms=rms,peak=peak)
    records.append(record);features.append(feat);events.append(event)
for partition in ('development','heldout'):
    utterances=[c for c in speech['recordings'] if c['partition']==partition]
    for i,c in enumerate(utterances):
        target=speechPcm[c['id']]
        other=next(d for d in utterances if d['speaker']!=c['speaker'])
        for bg in backgroundIds[partition]+['overlap']:
            noise=speechPcm[other['id']] if bg=='overlap' else bgs[bg]
            for snr,reference in ((15,0),(3,1),(-8,2)):
                mix=target+noise*10**(-snr/20)
                for position in ('near','filtered-reverberant'):
                    x=mix.copy()
                    if position!='near':
                        x=lfilter([.35],[1.,-.65],x);x[1280:]+=.25*x[:-1280];x[2240:]+=.12*x[:-2240]
                    x=normalize(x)
                    for gainDb in (-30,-45):
                        add(x*10**(gainDb/20),dict(partition=partition,speaker=c['speaker'],speech=c['id'],background=bg,backgroundSpeaker=other['speaker'] if bg=='overlap' else None,snr=snr,reference=reference,position=position,gainDb=gainDb,pair=f'{c["id"]}/{bg}/{position}/{gainDb}',kind='mixture'))
        print(partition,c['id'],len(records),flush=True)
    # Controls are not used to fit the severity head. All should hold except quiet.
    for bg in backgroundIds[partition]:
        for db in (-30,-45): add(bgs[bg]*10**(db/20),dict(partition=partition,background=bg,reference=-1,kind='background-only',gainDb=db))
    for c in utterances[:2]:
        add(speechPcm[c['id']]*.03,dict(partition=partition,speech=c['id'],reference=-1,kind='TV-surrogate',note='single recorded voice; not a real television recording'))
    t=np.arange(48000)/16000
    add(sum(np.sin(2*np.pi*f*t) for f in (261.63,329.63,392))*.03,dict(partition=partition,reference=-1,kind='synthetic-music'))
    impulse=np.zeros(48000);impulse[16000:16080]=.9
    add(impulse,dict(partition=partition,reference=-1,kind='impulse'))
    quiet=sources['fs-565535' if partition=='development' else 'fsd-348830'];q=decode(cached_source(a.background,quiet))[:48000]
    add(normalize(q)*10**(-75/20),dict(partition=partition,reference=0,kind='quiet-surrogate',note='gain-reduced real ambience; not calibrated quiet SPL'))
if cancellation:
    cancellation.stdin.close();assert cancellation.wait(timeout=30)==0
X=np.stack(features);E=np.stack(events)
np.savez(a.output/'features.npz',features=X,events=E,records=json.dumps(records))
train=np.array([r['partition']=='development' and r['kind']=='mixture' for r in records]);y=np.array([r['reference'] for r in records])
with threadpool_limits(limits=1):
    clf=LogisticRegression(C=1.0,max_iter=2000,solver='lbfgs').fit(X[train],y[train])
head=('HSGMASK2\n' if cancellation else 'HSGMASK1\n')+str(X.shape[1])+'\n'+','.join(map(str,clf.intercept_))+'\n'+'\n'.join(','.join(map(str,row)) for row in clf.coef_)+'\n'
(a.output/'masking-head.txt').write_text(head)
(a.output/'sources.json').write_text(json.dumps(dict(speech=speech,backgrounds=[sources[id] for ids in backgroundIds.values() for id in ids],policy=dict(snrDb=[15,3,-8],confidence=.65,margin=.15,C=1.0,cancellation='six shared Kotlin spectral suppression features' if cancellation else 'none',positions='digital low-pass and echo proxies only',training='development mixtures only; no wearer learning; no controls or heldout fitting')),indent=2)+'\n')
print('FIT',int(train.sum()),'TOTAL',len(records),'head',hashlib.sha256(head.encode()).hexdigest(),flush=True)
