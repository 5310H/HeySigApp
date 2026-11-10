"""Frozen development corpus, neural separation, actual Kotlin measurements; no playback.

Outputs scalars only. Public licensed source recordings remain in developer caches.
The denoiser is reset per capture; its residual is NOT assumed to be pure noise.
"""
import argparse,csv,hashlib,json,multiprocessing,subprocess,time
from pathlib import Path
import numpy as np
from scipy.signal import lfilter,resample_poly
from neural_separation import Denoiser
from separation_corpus import Corpus

engine=None
def initialize(library):
    global engine
    engine=Denoiser(Path(library))

def case(task):
    index,r,pcm,target,noise=task
    start=time.process_time()
    reference,enhanced,vad,residual_vad=engine.separate(pcm.astype(np.float32)/32768)
    result=dict(index=index,cpuSeconds=time.process_time()-start)
    if r['kind']=='mixture':
        foreground=engine.reference(target);background=engine.reference(noise)
        try:
            s=foreground[3200:].astype(np.float64);n=background[3200:].astype(np.float64)
            x=reference[3200:].astype(np.float64);y=enhanced[3200:].astype(np.float64)
            # The oracle is development-only known clean speech, never available on phone.
            weights=np.repeat(vad[20:].astype(np.float64),160)
            result['oracleWeightedSnrDb']=float(10*np.log10(max(1e-20,np.sum(weights*s*s))/max(1e-20,np.sum(weights*n*n))))
            result['componentReconstructionError']=float(np.linalg.norm(x-s-n)/max(1e-12,np.linalg.norm(x)))
            coefficients=np.linalg.lstsq(np.column_stack([s,n]),y,rcond=None)[0]
            result['foregroundProjectionGainDb']=float(20*np.log10(max(1e-8,abs(coefficients[0]))))
            def si_sdr(signal):
                signal=signal-signal.mean();speech=s-s.mean()
                projected=speech*np.dot(signal,speech)/max(1e-20,np.dot(speech,speech))
                return float(10*np.log10(max(1e-20,np.dot(projected,projected))/max(1e-20,np.sum((signal-projected)**2))))
            result['inputSiSdrDb']=si_sdr(x);result['outputSiSdrDb']=si_sdr(y)
        finally:foreground.fill(0);background.fill(0)
    pcm.fill(0);target.fill(0);noise.fill(0)
    return result,reference,enhanced,vad,residual_vad

def main():
    p=argparse.ArgumentParser();p.add_argument('--speech',type=Path,required=True);p.add_argument('--background',type=Path,required=True)
    p.add_argument('--baseline',type=Path,required=True);p.add_argument('--library',type=Path,required=True)
    p.add_argument('--kotlin-jar',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--workers',type=int,choices=(1,2),default=1);p.add_argument('--limit',type=int)
    a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
    data=np.load(a.baseline);records=json.loads(str(data['records']))
    if a.limit:records=records[:a.limit]
    corpus=Corpus(a.speech,a.background);probe=Denoiser(a.library)
    impulse=np.zeros(48000,dtype=np.float32);impulse[4096]=.1
    actual=probe.reference(impulse)
    expected=resample_poly(lfilter([1,-2,1],[1,-1.99599,.99600],resample_poly(impulse,3,1)),1,3)
    alignment=dict(impulsePeak=int(np.argmax(np.abs(actual))),expectedPeak=int(np.argmax(np.abs(expected))),
        relativeRmsError=float(np.linalg.norm(actual-expected)/np.linalg.norm(expected)),delay48kSamples=960)
    assert alignment['impulsePeak']==alignment['expectedPeak']==4096 and alignment['relativeRmsError']<.002,alignment
    impulse.fill(0);actual.fill(0);expected.fill(0)
    kotlin=subprocess.Popen(['java','-jar',str(a.kotlin_jar)],stdin=subprocess.PIPE,stdout=subprocess.PIPE)
    fields=['index','status','ratioDb','retainedDbfs','residualDbfs','speechActivity','speechScore','residualSpeechScore',
        'oracleWeightedSnrDb','foregroundProjectionGainDb','componentReconstructionError','inputSiSdrDb','outputSiSdrDb','cpuSeconds']
    started=time.monotonic()
    def tasks():
        for index,r in enumerate(records):yield (index,r,*corpus.make(r))
    try:
        with (a.output/'measurements.tsv').open('w') as stream:
            writer=csv.DictWriter(stream,fieldnames=fields,delimiter='\t');writer.writeheader()
            with multiprocessing.get_context('spawn').Pool(a.workers,initialize,(str(a.library),)) as pool:
                for result,*arrays in pool.imap(case,tasks(),chunksize=1):
                    try:
                        kotlin.stdin.write(b'S'+b''.join(x.astype('<f4',copy=False).tobytes() for x in arrays));kotlin.stdin.flush()
                        values=kotlin.stdout.readline().decode().strip().split('\t')
                        assert len(values)==7,values
                        result.update(zip(fields[1:8],values));writer.writerow(result)
                        if (result['index']+1)%48==0:
                            stream.flush();print('Measured',result['index']+1,'of',len(records),flush=True)
                    finally:
                        for x in arrays:x.fill(0)
        kotlin.stdin.close();assert kotlin.wait(timeout=30)==0
    finally:
        corpus.close()
        if kotlin.poll() is None:kotlin.terminate();kotlin.wait(timeout=30)
    manifest=dict(records=len(records),sourceBaselineSha256=hashlib.sha256(a.baseline.read_bytes()).hexdigest(),
        nativeLibrarySha256=hashlib.sha256(a.library.read_bytes()).hexdigest(),
        kotlinJarSha256=hashlib.sha256(a.kotlin_jar.read_bytes()).hexdigest(),alignment=alignment,
        elapsedSeconds=time.monotonic()-started,workers=a.workers,
        normalization='same bounded digital gain as YAMNet, 1..16, target .05 RMS; not SPL calibration',
        separation='matched high-pass and unity FFT synthesis reference, two-frame delay flushed/cropped identically; independent residual VAD pass',
        policy='measurements only; no automatic program commands or installed-model changes')
    (a.output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))

if __name__=='__main__':main()
