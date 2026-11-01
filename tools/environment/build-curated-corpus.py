from pathlib import Path
import json,hashlib,urllib.request,struct,wave,io,argparse
from scene_corpus import DEFAULT_MANIFEST,load_manifest
from curate_ami_windows import validate_plan,select_windows
p=argparse.ArgumentParser(description="Build a frozen per-window AMI corpus from a model-blind selection; explicit --fetch permits only bounded public excerpts.")
p.add_argument("--plan",type=Path,default=DEFAULT_MANIFEST.with_name("curation-plan.json"))
p.add_argument("--timings",type=Path,default=DEFAULT_MANIFEST.with_name("curation-word-timings.json"))
p.add_argument("--selection",type=Path,default=DEFAULT_MANIFEST.with_name("curated-windows.json"))
p.add_argument("--base-manifest",type=Path,default=DEFAULT_MANIFEST.with_name("expanded-corpus.json"))
p.add_argument("--cache",type=Path,required=True)
p.add_argument("--output",type=Path,required=True)
p.add_argument("--fetch",action="store_true")
a=p.parse_args()
plan=json.loads(a.plan.read_text());selection=json.loads(a.selection.read_text());timings=json.loads(a.timings.read_text())
assert selection['planSha256']==hashlib.sha256(a.plan.read_bytes()).hexdigest()
assert selection['timingsSha256']==hashlib.sha256(a.timings.read_bytes()).hexdigest()
bymeeting=validate_plan(plan);sources={(s['meeting'],s['speaker']):s for s in timings['sources']}
assert timings['version']==1 and timings['sourceIntervalSeconds']==[0,7200] and timings['license']=='CC-BY-4.0' and timings['licenseUrl']=='https://creativecommons.org/licenses/by/4.0/'
assert len(selection['meetings'])==len(bymeeting) and {m['meeting'] for m in selection['meetings']}==set(bymeeting)
for selected in selection['meetings']:
 m=bymeeting[selected['meeting']];speakers={letter:sources[(m['meeting'],letter)]['intervals'] for letter in 'ABCD'}
 assert selected['windows']==select_windows(speakers,m['durationSeconds'],m['reservedAudioIntervals'],plan['policy']), 'Selection differs from frozen word-activity protocol'
original=load_manifest(a.base_manifest);manifest={k:v for k,v in original.items() if k not in ('recordings','freshValidationIds','notes')}
manifest['recordings']=[c for c in original['recordings'] if not c['id'].startswith('ami-')];fresh=[];cache=a.cache;cache.mkdir(parents=True,exist_ok=True)
def ranged(url,start,end):
 request=urllib.request.Request(url+'?range_start='+str(start),headers={'Range':f'bytes={start}-{end}'})
 with urllib.request.urlopen(request,timeout=90) as r:
  assert r.status==206 and r.headers.get('Content-Range','').startswith(f'bytes {start}-{end}/')
  data=r.read(end-start+2)
 assert len(data)==end-start+1
 return data
for selected in selection['meetings']:
 name=selected['meeting'];m=bymeeting[name];url=f'https://huggingface.co/datasets/ggfox00000/dia-AMICorpus-all/resolve/a249db5/amicorpus/{name}/audio/{name}.Mix-Headset.wav'
 offset=None
 for window in selected['windows']:
  start=window['startSeconds'];seconds=window['durationSeconds'];identifier=f'ami-{name}-w{start:06d}-{seconds}s';filename=f'{name}-w{start:06d}-{seconds}s.wav';path=cache/filename
  if not path.exists():
   if not a.fetch:raise ValueError('Missing bounded source excerpt; explicit --fetch is required: '+filename)
   if offset is None:
    header=ranged(url,0,1023);assert header[:4]==b'RIFF' and header[8:12]==b'WAVE';cursor=12;fmt=None
    while cursor+8<len(header):
     tag=header[cursor:cursor+4];size=struct.unpack_from('<I',header,cursor+4)[0]
     if tag==b'fmt ':fmt=struct.unpack_from('<HHIIHH',header,cursor+8)
     if tag==b'data':offset=cursor+8;break
     cursor+=8+size+(size%2)
    else:raise ValueError('Missing bounded WAV header')
    assert fmt==(1,1,16000,32000,2,16)
   pcm=ranged(url,offset+start*32000,offset+(start+seconds)*32000-1);buffer=io.BytesIO()
   with wave.open(buffer,'wb') as f:f.setnchannels(1);f.setsampwidth(2);f.setframerate(16000);f.writeframes(pcm)
   data=buffer.getvalue();temp=path.with_suffix('.partial');temp.write_bytes(data);temp.replace(path)
  data=path.read_bytes()
  with wave.open(io.BytesIO(data),'rb') as f:assert (f.getnchannels(),f.getsampwidth(),f.getframerate(),f.getnframes())==(1,2,16000,seconds*16000)
  clip=dict(id=identifier,source='https://groups.inf.ed.ac.uk/ami/corpus/',download=url,author='AMI Project Consortium',title=f'{name}, annotated {seconds}-second multi-speaker window at {start}s',license=timings['license'],licenseUrl=timings['licenseUrl'],attribution=timings['attribution'],sha256=hashlib.sha256(data).hexdigest(),file=filename,reference='SMALL_GROUP',partition=m['partition'],sourceGroup='AMI/'+name[:-1],splitGroup='AMI/'+name[:-1],participants=[m['participantsByChannel'][letter] for letter in 'ABCD'],referenceBasis='Model-blind word-activity proxy: at least three annotated speakers each >=0.5 s, word coverage >=40%, overlap <=20%. Not independent scene/room annotation or acoustic VAD.',transforms=f'{seconds}-second source excerpt at seconds {start}-{start+seconds}, mono 16 kHz PCM16, from public headset mix; not far-field.',audioExcerpt=dict(startSeconds=start,durationSeconds=seconds),windows=[dict(offsetSeconds=0,seconds=seconds)],windowReference=dict(planSha256=selection['planSha256'],timingsSha256=selection['timingsSha256'],timingXmlSha256BySpeaker={letter:sources[(name,letter)]['xmlSha256'] for letter in 'ABCD'},activity=window['activity']))
  manifest['recordings'].append(clip)
  if m['partition']=='validation' and not m['previouslyViewedParticipants']:fresh.append(identifier)
  print(identifier,m['partition'],len(data),flush=True)
manifest['freshValidationIds']=fresh;manifest['notes']='Per-window AMI word-activity curation. Historical meeting-level AMI references are preserved in previous manifests, not used here. Fixed model-blind policy; two new participant groups reserved before inference. FSD references remain provisional creator descriptions; this is not six-class or physical acceptance.'
manifest['curation']=dict(planSha256=selection['planSha256'],timingsSha256=selection['timingsSha256'],selectionSha256=hashlib.sha256(a.selection.read_bytes()).hexdigest())
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(manifest,indent=2)+'\n')
load_manifest(a.output)
print('SOURCES',len(manifest['recordings']),'FRESH',len(fresh),flush=True)
