"""Offline quiet-return checks. Public corpus only; no fitting, microphone or saved audio."""
import argparse,ast,csv,hashlib,json,subprocess
from pathlib import Path
import numpy as np
from ai_edge_litert.interpreter import Interpreter
from scipy.signal import lfilter
from neural_separation import Denoiser
from separation_corpus import Corpus

p=argparse.ArgumentParser(description=__doc__)
for key in ['speech','background','library','jar','output']:p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();root=Path(__file__).resolve().parents[2];a.output.mkdir(parents=True,exist_ok=True)
assert hashlib.sha256(a.library.read_bytes()).hexdigest()=='3162f711add7592ca92c2349d9a75400d7622133d69412cf89e99ce9cf52fbb2'
manifest=json.loads((root/'shared/src/commonTest/fixtures/environment/scene-head/expanded-corpus.json').read_text())
model=root/'audioAndroid/src/main/assets/environment/yamnet.tflite';assert hashlib.sha256(model.read_bytes()).hexdigest()==manifest['encoderSha256']
engine=Interpreter(model_path=str(model),num_threads=1);engine.allocate_tensors()
class RecordingEngine:
    def __init__(self):self.frames=[]
    def __getattr__(self,key):return getattr(engine,key)
    def get_tensor(self,index):
        result=engine.get_tensor(index)
        if index==engine.get_output_details()[0]['index']:self.frames.append(result.reshape(-1).copy())
        return result
recording=RecordingEngine();ns=dict(np=np,engine=recording,inp=engine.get_input_details()[0],outs=engine.get_output_details(),cancellation=None)
tree=ast.parse((root/'tools/masking/evaluate.py').read_text());definitions=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in {'extract','acoustic'}]
assert len(definitions)==2
exec(compile(ast.Module(body=definitions,type_ignores=[]),'existing-extractor','exec'),ns);extract=ns['extract']
labels=(root/'audioAndroid/src/main/assets/environment/labels.txt').read_text().splitlines()
ordered=['Speech','Conversation','Narration, monologue','Hubbub, speech noise, speech babble','Crowd','Chatter','Silence','Vehicle','Traffic noise, roadway noise','Engine','Tools','Mechanisms','Dishes, pots, and pans','Vacuum cleaner','Water','Wind','Mechanical fan','Music']
corpus=Corpus(a.speech,a.background);denoiser=Denoiser(a.library)
frozen=np.load(root/'shared/build/masking/features.npz');records=json.loads(str(frozen['records']))
pcm,_,_=corpus.make(records[0]);feat,event,_,_=extract(pcm.astype(float)/32768)
assert np.allclose(feat,frozen['features'][0],atol=1e-5) and np.allclose(event,frozen['events'][0],atol=1e-6)
cases=[]
for key in ['fs-565535','fsd-348830','fs-260124']:
    source=next(r for r in manifest['recordings'] if r['id']==key);path=a.background/source['file']
    assert hashlib.sha256(path.read_bytes()).hexdigest()==source['sha256']
    data=subprocess.check_output(['ffmpeg','-v','error','-nostdin','-i',str(path),'-t','7','-ac','1','-ar','16000','-f','s16le','pipe:1'])
    x=np.frombuffer(data,dtype='<i2').astype(float)/32768
    for offset in [0,3]:
        clip=x[offset*16000:(offset+3)*16000];assert len(clip)==48000
        for gain in [.25,1.0]:cases.append((dict(source=key,offset=offset,gain=gain,expected={'fs-565535':'QUIET','fsd-348830':'CONTEXT_DEPENDENT','fs-260124':'NOT_QUIET'}[key]),clip*gain))
voices={}
for r in records:
    if r['partition']=='heldout' and r['kind']=='mixture':voices.setdefault(r['speaker'],r['speech'])
for key in voices.values():
    x=corpus.normalize(corpus.source(key,True))
    for db in [-50,-60]:cases.append((dict(source=key,db=db,expected='NOT_QUIET',kind='soft-speech'),x*10**(db/20)))
t=np.arange(48000)/16000;music=corpus.normalize(sum(np.sin(2*np.pi*f*t) for f in [261.63,329.63,392]))
for db in [-50,-60]:cases.append((dict(source='synthetic-music',db=db,expected='NOT_QUIET'),music*10**(db/20)))
def kotlin(name,*args):subprocess.run(['java','-cp',str(a.jar),'com.heysig.masking.'+name,*map(str,args)],check=True)
worker=subprocess.Popen(['java','-cp',str(a.jar),'com.heysig.masking.SeparationReportKt'],stdin=subprocess.PIPE,stdout=subprocess.PIPE)
rows=[]
try:
    with (a.output/'evidence.tsv').open('w') as ev,(a.output/'frames.tsv').open('w') as fr,(a.output/'measurements.tsv').open('w') as me:
        me.write('index\tstatus\tratioDb\tretainedDbfs\tresidualDbfs\tspeechActivity\tspeechScore\tresidualSpeechScore\n')
        for i,(record,x) in enumerate(cases):
            recording.frames=[];feat,event,rms,peak=extract(x)
            quantized=np.clip(np.rint(x*32768),-32768,32767).astype(np.int16).astype(float);quantized-=quantized.mean()
            low=lfilter([.04],[1,-.96],quantized);lowFraction=float(np.sum(low*low)/max(1e-20,np.sum(quantized*quantized)))
            frames=[[float(v[labels.index(k)]) for k in ordered] for v in recording.frames]
            ev.write(str(i)+'\t'+','.join(map(str,list(feat)+[event[labels.index(k)] for k in ordered]+[rms,peak,lowFraction]))+'\n')
            fr.write(str(i)+'\t'+';'.join(','.join(map(str,f)) for f in frames)+'\n')
            pcm=np.clip(np.rint(x*32768),-32768,32767).astype(np.int16);reference,enhanced,vad,residual=denoiser.separate(pcm.astype(np.float32)/32768)
            worker.stdin.write(b'S'+b''.join(v.astype('<f4').tobytes() for v in [reference,enhanced,vad,residual]));worker.stdin.flush()
            measured=worker.stdout.readline().decode().strip();assert len(measured.split('\t'))==7
            me.write(str(i)+'\t'+measured+'\n')
            rows.append(dict(index=i,**record,rms=rms,peak=peak,lowFrequencyFraction=lowFraction,features=feat[2048:].tolist(),frames=frames,neural=measured.split('\t')))
            for v in [x,pcm,reference,enhanced,vad,residual,quantized,low]:v.fill(0)
    worker.stdin.close();assert worker.wait(timeout=20)==0
finally:
    corpus.close()
    if worker.poll() is None:worker.terminate();worker.wait(timeout=20)
kotlin('QuietReturnReportKt',root/'audioAndroid/src/main/assets/masking/masking-baseline.txt',root/'audioAndroid/src/main/assets/separation/bands.txt',a.output/'evidence.tsv',a.output/'measurements.tsv',a.output/'decisions.tsv',a.output/'frames.tsv')
decisions=list(csv.DictReader((a.output/'decisions.tsv').open(),delimiter='\t'))
for r,d in zip(rows,decisions):r.update(decision=d)
(a.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n')
failures=[r for r in rows if r['expected']!='CONTEXT_DEPENDENT' and (r['decision']['severity']=='QUIET')!=(r['expected']=='QUIET')]
print(json.dumps(dict(cases=len(rows),quiet=sum(r['decision']['severity']=='QUIET' for r in rows),failures=[{k:v for k,v in r.items() if k!='frames'} for r in failures]),indent=2))
