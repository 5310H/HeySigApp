"""Model-blind per-window AMI word-activity references. No audio or model APIs."""
import argparse,hashlib,json,re,urllib.request,xml.etree.ElementTree as E
from pathlib import Path
from ami_timing_audit import word_intervals
from curate_ami_windows import select_windows,POLICY,validate_plan
from scene_corpus import DEFAULT_MANIFEST
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--plan',type=Path,default=DEFAULT_MANIFEST.with_name('curation-plan.json'))
p.add_argument('--timings',type=Path,default=DEFAULT_MANIFEST.with_name('curation-word-timings.json'))
p.add_argument('--output',type=Path,required=True)
p.add_argument('--fetch',action='store_true',help='Verify public metadata/XML hashes and extraction; raw XML stays in memory and text is discarded')
a=p.parse_args();plan=json.loads(a.plan.read_text());validate_plan(plan)
assert plan['version']==1 and plan['policy']==POLICY and re.fullmatch('[0-9a-f]{64}',plan['metadataSha256'])
assert 1<=a.timings.stat().st_size<=4_000_000
timings=json.loads(a.timings.read_text());assert timings['version']==1 and timings['sourceIntervalSeconds']==[0,7200]
assert timings['license']=='CC-BY-4.0' and timings['licenseUrl']=='https://creativecommons.org/licenses/by/4.0/' and timings['attribution']
assert 1<=len(plan['meetings'])<=16 and len({m['meeting'] for m in plan['meetings']})==len(plan['meetings'])
identities={}
for m in plan['meetings']:
 assert re.fullmatch(r'[A-Z]{2}[0-9]{4}[a-z]',m['meeting']) and m['partition'] in ('development','validation')
 assert type(m['previouslyViewedParticipants'])==bool
 assert set(m['participantsByChannel'])==set('ABCD') and len(set(m['participantsByChannel'].values()))==4
 for person in m['participantsByChannel'].values():
  assert isinstance(person,str) and person.strip()
  assert identities.setdefault(person,m['partition'])==m['partition'], 'Participant crosses partitions'
metadata_base='https://huggingface.co/datasets/ggfox00000/dia-AMICorpus-all/resolve/a249db5/ami_public_manual_1.6.2/'
assert plan['metadataSource']==metadata_base+'corpusResources/meetings.xml'
def fetch(url):
 with urllib.request.urlopen(url,timeout=60) as response:data=response.read(2_000_001)
 assert len(data)<=2_000_000
 return data
if a.fetch:
 data=fetch(plan['metadataSource']);assert hashlib.sha256(data).hexdigest()==plan['metadataSha256']
 meetings={m.get('observation'):m for m in E.fromstring(data)}
 for m in plan['meetings']:
  metadata=meetings[m['meeting']]
  assert m['participantsByChannel']=={s.get('nxt_agent'):s.get('global_name') for s in metadata if s.tag=='speaker'}
  assert m['durationSeconds']==int(float(metadata.get('duration')))
 del data
 print('Public participant mapping and duration metadata verified',flush=True)
channels={}
assert len(timings['sources'])==4*len(plan['meetings'])
for source in timings['sources']:
 meeting=source['meeting'];speaker=source['speaker'];key=(meeting,speaker)
 assert meeting in {m['meeting'] for m in plan['meetings']} and speaker in 'ABCD' and len(speaker)==1 and key not in channels
 assert source['source']==metadata_base+f'words/{meeting}.{speaker}.words.xml' and re.fullmatch('[0-9a-f]{64}',source['xmlSha256'])
 if a.fetch:
  data=fetch(source['source']);assert hashlib.sha256(data).hexdigest()==source['xmlSha256']
  assert word_intervals(data,0,7200)==source['intervals'];del data
  print(meeting,speaker,'public timestamp source verified',flush=True)
 channels[key]=source['intervals']
selected=[]
for m in plan['meetings']:
 speakers={letter:channels[(m['meeting'],letter)] for letter in 'ABCD'}
 windows=select_windows(speakers,m['durationSeconds'],m['reservedAudioIntervals'],plan['policy'])
 selected.append(dict(meeting=m['meeting'],windows=windows));print(m['meeting'],[(w['startSeconds'],w['durationSeconds']) for w in windows])
result=dict(version=1,planSha256=hashlib.sha256(a.plan.read_bytes()).hexdigest(),timingsSha256=hashlib.sha256(a.timings.read_bytes()).hexdigest(),meetings=selected)
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n')
