import json,hashlib,subprocess,argparse
from pathlib import Path
from scene_corpus import load_manifest,cached_source,DEFAULT_MANIFEST
import numpy as np
from ai_edge_litert.interpreter import Interpreter
p=argparse.ArgumentParser(description='Offline scene features and exact scalar evidence; no device or aid APIs.')
p.add_argument('--manifest',type=Path,default=DEFAULT_MANIFEST)
p.add_argument('--cache',type=Path,required=True)
p.add_argument('--model',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
manifest=load_manifest(a.manifest)
assert hashlib.sha256(a.model.read_bytes()).hexdigest()==manifest['encoderSha256']
engine=Interpreter(model_path=str(a.model),num_threads=1);engine.allocate_tensors()
x=engine.get_input_details()[0];outputs=engine.get_output_details();y=outputs[1]
records=[];features=[];events=[];event_frames=[];event_offsets=[0];excluded=[]
for clip in manifest['recordings']:
 path=cached_source(a.cache,clip)
 raw=subprocess.check_output(['ffmpeg','-v','error','-nostdin','-i',str(path),'-t','30','-ac','1','-ar','16000','-f','s16le','pipe:1'])
 pcm=np.frombuffer(raw,dtype='<i2')
 if len(pcm)<48000:excluded.append(dict(clip=clip['id'],reason='Source shorter than the minimum three-second window'));continue
 # Separate non-overlapping 10-second source sections; same recording/session group throughout.
 for offset in range(0,len(pcm),160000):
  for seconds in (3,5,10):
   if len(pcm)<offset+seconds*16000:continue
   for gain in (.25,1.0):
    samples=(pcm[offset:offset+seconds*16000].astype(np.float64)*gain).astype(np.int16).astype(np.float64)/32768
    peak=float(np.max(np.abs(samples)));rms=float(np.sqrt(np.mean(samples*samples)))
    if peak==0:continue
    factor=max(1.0,min(16.,.05/rms,.95/peak));samples=(samples*factor).astype(np.float32)
    frames=[];scores=[]
    for start in range(0,len(samples)-15600+1,7680):
     engine.set_tensor(x['index'],samples[start:start+15600]);engine.invoke()
     frames.append(engine.get_tensor(y['index']).reshape(-1).astype(np.float64)+128)
     scores.append(engine.get_tensor(outputs[0]['index']).reshape(-1))
    values=np.stack(frames);feature=np.concatenate([values.mean(axis=0),values.std(axis=0)])
    feature/=np.linalg.norm(feature)
    features.append(feature);events.append(np.mean(scores,axis=0));event_frames.extend(scores);event_offsets.append(len(event_frames))
    records.append(dict(clip=clip['id'],author=clip['author'],sourceGroup=clip['sourceGroup'],reference=clip['reference'],partition=clip['partition'],seconds=seconds,gain=gain,offsetSeconds=offset//16000))
 print(clip['id'],len(records),flush=True)
a.output.parent.mkdir(parents=True,exist_ok=True)
np.savez(a.output,features=np.stack(features),events=np.stack(events),records=json.dumps(records),modelSha256=hashlib.sha256(a.model.read_bytes()).hexdigest(),manifestSha256=hashlib.sha256(a.manifest.read_bytes()).hexdigest(),eventFrames=np.stack(event_frames),eventOffsets=np.array(event_offsets),excluded=json.dumps(excluded))
print('CASES',len(records),flush=True)
