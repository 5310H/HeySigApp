"""Explicit developer-only licensed sources. No wearer recordings, transcript extraction or playback."""
import argparse, hashlib, json, tarfile, urllib.request, subprocess
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--cache',type=Path,required=True);a=p.parse_args()
a.cache.mkdir(parents=True,exist_ok=True)
def fetch(url,path,limit):
    if path.exists(): return
    total=0
    with urllib.request.urlopen(url,timeout=120) as r,path.with_suffix('.partial').open('wb') as out:
        while block:=r.read(1024*1024):
            total+=len(block)
            if total>limit: raise ValueError('Download limit')
            out.write(block)
    path.with_suffix('.partial').replace(path)
archive=a.cache/'dev-clean.tar.gz'
base='https://openslr.elda.org/resources/12/'
fetch(base+'md5sum.txt',a.cache/'md5sum.txt',10000)
fetch(base+'dev-clean.tar.gz',archive,400_000_000)
expected=next(line.split()[0] for line in (a.cache/'md5sum.txt').read_text().splitlines() if line.split()[-1]=='dev-clean.tar.gz')
assert hashlib.md5(archive.read_bytes()).hexdigest()==expected
assert hashlib.sha256(archive.read_bytes()).hexdigest()=='76f87d090650617fca0cac8f88b9416e0ebf80350acb97b343a85fa903728ab3'
records=[];speakers={}
with tarfile.open(archive,'r:gz') as tar:
    for m in tar:
        if m.name.endswith(('LICENSE.TXT','SPEAKERS.TXT')):
            (a.cache/Path(m.name).name).write_bytes(tar.extractfile(m).read())
        if not m.name.endswith('.flac'): continue
        speaker=m.name.split('/')[-3]
        if speaker not in speakers and len(speakers)>=8: continue
        if speakers.get(speaker,0)>=2: continue
        data=tar.extractfile(m).read();dest=a.cache/Path(m.name).name;dest.write_bytes(data)
        raw=subprocess.check_output(['ffmpeg','-v','error','-nostdin','-i',str(dest),'-t','10','-ac','1','-ar','16000','-f','s16le','pipe:1'])
        if len(raw)<320000: dest.unlink();continue
        speakers[speaker]=speakers.get(speaker,0)+1
        records.append(dict(id=dest.stem,file=dest.name,speaker=speaker,partition='development' if list(speakers).index(speaker)<4 else 'heldout',sha256=hashlib.sha256(data).hexdigest(),source=base+'dev-clean.tar.gz',archiveMember=m.name,license='CC-BY-4.0',attribution='LibriSpeech, V. Panayotov, G. Chen, D. Povey and S. Khudanpur; LibriVox readers; see SPEAKERS.TXT',transform='first ten seconds, mono 16 kHz; no transcripts extracted'))
        if len(records)==16: break
assert len(records)==16
(a.cache/'speech.json').write_text(json.dumps(dict(archiveSha256=hashlib.sha256(archive.read_bytes()).hexdigest(),recordings=records),indent=2)+'\n')
print('Prepared',len(records),'utterances from',len(speakers),'speakers',flush=True)
