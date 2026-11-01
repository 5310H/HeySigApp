"""Reproduce one licensed, test-only fallback-path regression; never accuracy acceptance."""
import argparse,csv,hashlib,json,subprocess
from pathlib import Path
from scene_corpus import load_manifest,cached_source,DEFAULT_MANIFEST

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--cache',type=Path,required=True)
p.add_argument('--decision-report',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
manifest=load_manifest(DEFAULT_MANIFEST.with_name('curated-corpus.json'))
clip=next(c for c in manifest['recordings'] if c['id']=='ami-ES2002a-w000338-5s')
assert clip['partition']=='development' and clip['reference']=='SMALL_GROUP'
with a.decision_report.open() as f:
 rows=[r for r in csv.DictReader(f,delimiter='\t') if (r['clip'],r['seconds'],r['gain'],r['offsetSeconds'])==(clip['id'],'5','1.0','0')]
assert len(rows)==1 and rows[0]['outcome']=='CORRECT' and rows[0]['decision']=='SMALL_GROUP'
score=float(rows[0]['score']);assert .65<=score<=1
source=cached_source(a.cache,clip)
raw=subprocess.check_output(['ffmpeg','-v','error','-nostdin','-i',str(source),'-t','5','-ac','1','-ar','16000','-f','s16le','pipe:1'])
assert len(raw)==5*16000*2
a.output.mkdir(parents=True,exist_ok=True)
(a.output/'small_group_fallback.pcm').write_bytes(raw)
row=dict(file='small_group_fallback.pcm',sha256=hashlib.sha256(raw).hexdigest(),seconds=5,
 expectedEnvironment='SMALL_GROUP',expectedScore=score,primaryExpectedEnvironment='UNCERTAIN',
 source=clip['source'],sourceId=clip['id'],sourceSha256=clip['sha256'],author=clip['author'],
 license=clip['license'],licenseUrl=clip['licenseUrl'],sourceTransforms=clip['transforms'],referenceBasis=clip['referenceBasis'],
 transforms='Complete curated source window, mono 16 kHz PCM16, digital gain 1.0; never played.',
 notes='Previously viewed development/training regression for fallback wiring only; NOT independent accuracy or physical acceptance.')
(a.output/'fallback.json').write_text(json.dumps(row,indent=2)+'\n')
print('PASS: pinned five-second development regression generated; no device or aid access')
