"""Audit existing public AMI windows using word timestamps, never word text or scene relabeling."""
import argparse,hashlib,json,re,urllib.request
from pathlib import Path
from ami_timing_audit import summarize,word_intervals,merge
from scene_corpus import DEFAULT_MANIFEST,load_manifest
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--manifest',type=Path,default=DEFAULT_MANIFEST.with_name('expanded-corpus.json'))
p.add_argument('--timings',type=Path,default=DEFAULT_MANIFEST.with_name('ami-word-timings.json'))
p.add_argument('--output',type=Path,required=True)
p.add_argument('--fetch',action='store_true',help='Explicitly re-fetch public XML, verify source hashes/timing extraction, and discard all text')
a=p.parse_args();manifest=load_manifest(a.manifest)
if not 1<=a.timings.stat().st_size<=1_000_000:raise ValueError('Timing metadata bound')
metadata=json.loads(a.timings.read_text())
assert metadata['version']==1 and metadata['sourceIntervalSeconds']==[120,150]
assert metadata['license']=='CC-BY-4.0' and metadata['licenseUrl']=='https://creativecommons.org/licenses/by/4.0/' and metadata['attribution']
clips={c['id'][4:]:c for c in manifest['recordings'] if c['id'].startswith('ami-')}
assert len(metadata['sources'])==4*len(clips)
channels={}
for source in metadata['sources']:
 meeting=source['meeting'];speaker=source['speaker']
 assert meeting in clips and re.fullmatch(r'[A-Z]{2}[0-9]{4}[a-z]',meeting) and speaker in 'ABCD' and len(speaker)==1
 key=(meeting,speaker);assert key not in channels
 url=f'https://huggingface.co/datasets/ggfox00000/dia-AMICorpus-all/resolve/a249db5/ami_public_manual_1.6.2/words/{meeting}.{speaker}.words.xml'
 assert source['source']==url and re.fullmatch('[0-9a-f]{64}',source['xmlSha256'])
 merge(source['intervals'],0,30) # validate all bounds, including intervals outside shorter audit windows
 if a.fetch:
  with urllib.request.urlopen(url,timeout=60) as response:data=response.read(2_000_001)
  assert hashlib.sha256(data).hexdigest()==source['xmlSha256'], 'Changed public annotation'
  assert word_intervals(data)==source['intervals'], 'Changed timestamp extraction'
  del data
  print(meeting,speaker,'public source hash/extraction verified',flush=True)
 channels[key]=source['intervals']
results=[]
for meeting,clip in clips.items():
 speakers={speaker:channels[(meeting,speaker)] for speaker in 'ABCD'}
 for offset in (0,10,20):
  for seconds in (3,5,10):
   results.append(dict(clip=clip['id'],partition=clip['partition'],offsetSeconds=offset,seconds=seconds,**summarize(speakers,offset,seconds)))
summary={}
for partition in ('development','validation'):
 rows=[r for r in results if r['partition']==partition]
 summary[partition]=dict(uniqueTimingWindows=len(rows),fewerThanThreeSpeakersWithWords=sum(r['speakersWithWords']<3 for r in rows),
 fewerThanThreeWithHalfSecond=sum(r['speakersWithAtLeastHalfSecond']<3 for r in rows))
 print(partition,summary[partition])
result=dict(version=1,manifestSha256=hashlib.sha256(a.manifest.read_bytes()).hexdigest(),timingsSha256=hashlib.sha256(a.timings.read_bytes()).hexdigest(),
 notes='Public annotation audit only. Word intervals are not acoustic voice activity, group size, room occupancy or independent scene truth. No reference labels changed. Gain variants share timing windows; raw audio and word text are never read/saved by the offline audit.',summary=summary,windows=results)
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n')
