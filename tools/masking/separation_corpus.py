"""Reconstruct the frozen licensed mixes AND their known speech/noise components."""
import hashlib,json,subprocess,sys
from pathlib import Path
import numpy as np
from scipy.signal import lfilter
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'environment'))
from scene_corpus import load_manifest,cached_source


class Corpus:
    def __init__(self,speech_cache,background_cache):
        self.speech_cache=speech_cache;self.background_cache=background_cache
        root=Path(__file__).resolve().parents[2]
        self.sources={r['id']:r for r in load_manifest(root/'shared/src/commonTest/fixtures/environment/scene-head/expanded-corpus.json')['recordings']}
        self.speech=json.loads((speech_cache/'speech.json').read_text())['recordings']
        self.cache={}

    @staticmethod
    def normalize(x):return x/max(1e-12,np.sqrt(np.mean(x*x)))

    def source(self,key,speech=False):
        cache_key=(key,speech)
        if cache_key not in self.cache:
            record=next(r for r in self.speech if r['id']==key) if speech else self.sources[key]
            path=self.speech_cache/record['file'] if speech else cached_source(self.background_cache,record)
            assert hashlib.sha256(path.read_bytes()).hexdigest()==record['sha256']
            data=subprocess.check_output(['ffmpeg','-v','error','-nostdin','-i',str(path),'-t','10','-ac','1','-ar','16000','-f','s16le','pipe:1'])
            x=np.frombuffer(data,dtype='<i2').astype(np.float64)/32768
            if not speech and len(x)<48000:
                assert len(x)>=8000
                x=np.tile(x,int(np.ceil(48000/len(x))))
            assert len(x)>=48000
            self.cache[cache_key]=x[:48000]
        return self.cache[cache_key].copy()

    @staticmethod
    def position(x):
        x=lfilter([.35],[1.,-.65],x)
        x[1280:]+=.25*x[:-1280];x[2240:]+=.12*x[:-2240]
        return x

    def make(self,r):
        target=np.zeros(48000)
        kind=r['kind']
        if kind=='mixture':
            target=self.normalize(self.source(r['speech'],True))
            if r['background']=='overlap':
                other=next(c for c in self.speech if c['partition']==r['partition'] and c['speaker']==r['backgroundSpeaker'])
                noise=self.normalize(self.source(other['id'],True))
            else:noise=self.normalize(self.source(r['background']))
            noise*=10**(-r['snr']/20)
            if r['position']!='near':target=self.position(target);noise=self.position(noise)
            # Match evaluate.py: position is a linear transform of the sum.
            gain=10**(r['gainDb']/20)/np.sqrt(np.mean((target+noise)**2))
            target*=gain;noise*=gain
        elif kind=='background-only':noise=self.normalize(self.source(r['background']))*10**(r['gainDb']/20)
        elif kind=='TV-surrogate':target=self.normalize(self.source(r['speech'],True))*.03;noise=np.zeros(48000)
        elif kind=='synthetic-music':
            t=np.arange(48000)/16000;noise=sum(np.sin(2*np.pi*f*t) for f in (261.63,329.63,392))*.03
        elif kind=='impulse':noise=np.zeros(48000);noise[16000:16080]=.9
        elif kind=='quiet-surrogate':
            key='fs-565535' if r['partition']=='development' else 'fsd-348830'
            noise=self.normalize(self.source(key))*10**(-75/20)
        else:raise ValueError(kind)
        pcm=np.clip(np.rint((target+noise)*32768),-32768,32767).astype(np.int16)
        x=pcm.astype(np.float64)/32768
        rms=20*np.log10(max(np.sqrt(np.mean(x*x)),1e-6));peak=20*np.log10(max(np.max(np.abs(x)),1e-6))
        assert abs(rms-r['rms'])<1e-5 and abs(peak-r['peak'])<1e-5,(kind,rms,r['rms'])
        return pcm,target,noise

    def close(self):
        for array in self.cache.values():array.fill(0)
        self.cache.clear()
