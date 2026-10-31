"""Host-only zero-shot probe. Never emits hearing-aid commands or qualifies a model."""
import ast,sys,types,json,hashlib,subprocess,time,argparse,os
from pathlib import Path
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--model-cache',type=Path,required=True)
parser.add_argument('--text-cache',type=Path,required=True)
parser.add_argument('--audio-cache',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
ROOT=Path(__file__).resolve().parents[2]
BASE=args.model_cache
os.environ.setdefault('HF_HOME',str(BASE/'huggingface-cache'))
record=json.loads((ROOT/'docs/acceptance/data/tinyclap-zero-shot-2026-10-31.json').read_text())
for name,pin in record['provenance'].items():
    assert hashlib.sha256((BASE/name).read_bytes()).hexdigest()==pin['sha256'],name
import numpy as np
import torch
from torch import nn
import torch.nn.functional as F
from transformers import BertConfig,BertModel,BertTokenizer
torch.set_num_threads(1)
# Only the original network definitions; training/download/framework code is not run.
stub=types.ModuleType('torchinfo');stub.summary=lambda *args,**kwargs:None;sys.modules['torchinfo']=stub
spec=__import__('importlib.util',fromlist=['']).spec_from_file_location('phinet',BASE/'phinet.py')
phinet=__import__('importlib.util',fromlist=['']).module_from_spec(spec);spec.loader.exec_module(phinet)
class LocalAutoModel:
    @staticmethod
    def from_pretrained(name):
        assert name=='bert-base-uncased'
        return BertModel(BertConfig.from_json_file(str(BASE/'config.json')))
namespace=dict(torch=torch,nn=nn,F=F,np=np,PhiNet=phinet.PhiNet,AutoModel=LocalAutoModel)
source=ast.parse((BASE/'modules.py').read_text())
keep={'get_model_from_str','get_audio_encoder','Projection','PhiNet','AudioEncoder','TextEncoder'}
source.body=[node for node in source.body if isinstance(node,(ast.ClassDef,ast.FunctionDef)) and node.name in keep]
exec(compile(source,'tinyCLAP-selected-definitions','exec'),namespace)
text=namespace['TextEncoder'](1024,'bert-base-uncased',768)
pins=json.loads((args.text_cache/'manifest.json').read_text())
digest=hashlib.sha256()
for key,entry in sorted(pins.items()):
    digest.update(key.encode()+b'\0'+bytes.fromhex(entry['sha256']))
assert digest.hexdigest()==record['teacherTextTensorDigest']
state={}
for key,entry in pins.items():
    path=args.text_cache/entry['file']
    assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256']
    if key.endswith('position_ids'):continue
    state[key.removeprefix('caption_encoder.')]=torch.from_numpy(np.load(path))
text.load_state_dict(state,strict=True);text.eval();del state
student=namespace['AudioEncoder']('phinet_alpha_1.50_beta_0.75_t0_6_N_7',2048,1024,527)
weights=torch.load(BASE/'student.ckpt',map_location='cpu',weights_only=True)
print('STUDENT ROOT',list(weights)[:6],flush=True)
student.load_state_dict(weights,strict=True);student.eval()
labels={
 'QUIET':'a quiet room with minimal background noise',
 'CONVERSATION':'two people having a one on one conversation',
 'SMALL_GROUP':'a small group of several people talking',
 'RESTAURANT':'people talking in a busy restaurant with clattering dishes and competing conversations',
 'LARGE_GROUP':'a large crowd of people with many overlapping conversations',
 'NON_SPEECH_NOISE':'traffic machinery or household appliance noise without speech',
}
tokenizer=BertTokenizer(vocab_file=str(BASE/'vocab.txt'),do_lower_case=True)
with torch.no_grad():
    vectors=text(tokenizer(['this is the sound of '+value for value in labels.values()],max_length=100,padding='max_length',truncation=True,return_tensors='pt'))
    vectors=F.normalize(vectors,dim=1)
np.save(BASE/'text-vectors.npy',vectors.numpy())
del text,pins
from torchlibrosa.stft import Spectrogram,LogmelFilterBank
spec=Spectrogram(n_fft=1024,hop_length=320,win_length=1024,window='hann',center=True,pad_mode='reflect',freeze_parameters=True)
mel=LogmelFilterBank(sr=44100,n_fft=1024,n_mels=64,fmin=50,fmax=14000,ref=1.0,amin=1e-10,top_db=None,freeze_parameters=True)
manifest_path=ROOT/'shared/src/commonTest/fixtures/environment/corpus/manifest.json'
assert hashlib.sha256(manifest_path.read_bytes()).hexdigest()==record['corpusManifestSha256']
manifest=json.loads(manifest_path.read_text())
cases=[]
for clip in manifest['recordings']:
    path=args.audio_cache/clip['file']
    assert hashlib.sha256(path.read_bytes()).hexdigest()==clip['sha256']
    raw=subprocess.check_output(['ffmpeg','-v','error','-nostdin','-i',str(path),'-t','10','-ac','1','-ar','44100','-f','s16le','pipe:1'])
    pcm=np.frombuffer(raw,dtype='<i2').astype(np.float32)/32768
    for seconds in (3,5,10):
        if len(pcm)<seconds*44100:continue
        for gain in (0.25,1.0):
            wave=torch.from_numpy(pcm[:seconds*44100]*gain)[None]
            started=time.monotonic()
            with torch.no_grad():
                features=mel(spec(wave))
                embedding,_,_=student(features)
                similarities=(F.normalize(embedding,dim=1)@vectors.T)[0].numpy()
            winner=list(labels)[int(similarities.argmax())]
            case=dict(clip=clip['id'],seconds=seconds,gain=gain,reference=clip['reference'],partition=clip['partition'],
                      winner=winner,similarities=dict(zip(labels,map(float,similarities))),wallMs=(time.monotonic()-started)*1000)
            cases.append(case)
            print(clip['id'],seconds,gain,'reference',clip['reference'],'winner',winner,'cosine',np.round(similarities,3),flush=True)
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_text(json.dumps({'labels':labels,'cases':cases,'notes':'Zero-shot cosine similarities; not calibrated probabilities. Provisional references; NOT qualification.'},indent=2))
print('TOP-1',sum(case['reference']==case['winner'] for case in cases),'/',len(cases),'NOT qualification',flush=True)
