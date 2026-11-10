"""Rebuild six licensed, never-played JNI regressions. Not accuracy or physical acceptance."""
import argparse,csv,hashlib,json,subprocess
from pathlib import Path
import numpy as np
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--speech',type=Path,required=True);p.add_argument('--background',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
data=np.load(a.work/'features.npz');records=json.loads(str(data['records']));sources=json.loads((a.work/'sources.json').read_text())
decisions=list(csv.DictReader((a.work/'decisions.tsv').open(),delimiter='\t'))
def decode(path):
    raw=subprocess.check_output(['ffmpeg','-v','error','-nostdin','-i',str(path),'-t','10','-ac','1','-ar','16000','-f','s16le','pipe:1'])
    return np.frombuffer(raw,dtype='<i2').astype(np.float64)/32768
def norm(x): return x/np.sqrt(np.mean(x*x))
a.output.mkdir(parents=True,exist_ok=True);result=[]
for part in ('development','heldout'):
    start=next(i for i,r in enumerate(records) if r['partition']==part and r['kind']=='mixture')
    for offset in (0,4,8):
        i=start+offset;r=records[i];assert r['position']=='near' and r['gainDb']==-30
        s=next(c for c in sources['speech']['recordings'] if c['id']==r['speech'])
        b=next(c for c in sources['backgrounds'] if c['id']==r['background'])
        speechPath=a.speech/s['file'];backgroundPath=a.background/b['file']
        for path,source in ((speechPath,s),(backgroundPath,b)): assert hashlib.sha256(path.read_bytes()).hexdigest()==source['sha256']
        target=norm(decode(speechPath)[:48000]);bg=decode(backgroundPath)
        if len(bg)<48000: bg=np.tile(bg,int(np.ceil(48000/len(bg))))
        noise=norm(bg[:48000]);mix=norm(target+noise*10**(-r['snr']/20))*10**(-30/20)
        pcm=np.clip(np.rint(mix*32768),-32768,32767).astype('<i2').tobytes();name=f'{part}-{r["reference"]}.pcm';(a.output/name).write_bytes(pcm)
        result.append(dict(file=name,sha256=hashlib.sha256(pcm).hexdigest(),sourceIndex=i,reference=r['reference'],snr=r['snr'],severity=decisions[i]['severity'],score=float(decisions[i]['score']),rms=r['rms'],features=data['features'][i].tolist()))
(a.output/'manifest.json').write_text(json.dumps(dict(headSha256=hashlib.sha256((a.work/'masking-head.txt').read_bytes()).hexdigest(),baselineSha256=sources.get('baselineSha256'),fixtures=result),indent=2)+'\n')
(a.output/'NOTICE').write_text('Developer instrumentation fixtures only. Never played.\nLibriSpeech (c) 2014 Vassil Panayotov, CC BY 4.0. LibriVox readers credited in the accompanying source manifest.\nBackground sources and authors: see sources.json. CC0/CC BY 3.0/4.0 as stated per source.\nTransforms: first three seconds, mono 16kHz PCM16, foreground/background normalized and mixed at +15/+3/-8dB SNR, then matched overall -30dBFS RMS.\nSource references, license links and original hashes are retained. No wearer recordings.\n')
(a.output/'sources.json').write_text(json.dumps(sources,indent=2)+'\n')
print('Generated',len(result),'pinned native fixtures')
