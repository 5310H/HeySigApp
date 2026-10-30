#!/usr/bin/env python3
"""Development-only WAV/model regression; no download or recording on end-user devices.

Install ai-edge-litert==1.4.0 and numpy in an isolated host environment.
Fetch the manifest's four WAVs into --fixtures; source hashes must match.
This writes scalar model evidence, never excerpts or transcripts.
"""
import argparse
import hashlib
import json
from pathlib import Path
import wave

import numpy as np
from ai_edge_litert.interpreter import Interpreter

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--fixtures', type=Path, required=True)
parser.add_argument('--output', type=Path, default=ROOT/'shared/build/environment-evidence.tsv')
args = parser.parse_args()
manifest = json.loads((ROOT/'shared/src/commonTest/fixtures/environment/recordings.json').read_text())
model = ROOT/'audioAndroid/src/main/assets/environment/yamnet.tflite'
assert hashlib.sha256(model.read_bytes()).hexdigest() == manifest['modelSha256']
labels = (model.parent/'labels.txt').read_text().splitlines()
interpreter = Interpreter(model_path=str(model), num_threads=1)
interpreter.allocate_tensors()
source = interpreter.get_input_details()[0]
target = interpreter.get_output_details()[0]
assert list(source['shape']) == [15600] and list(target['shape']) == [1,521]
selected = manifest['labels']
rows = ['clip\tseconds\tgain\t'+'\t'.join(selected)]
for clip in manifest['recordings']:
    path = args.fixtures/clip['file']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == clip['sha256'], path
    with wave.open(str(path)) as wav:
        assert (wav.getnchannels(), wav.getsampwidth(), wav.getframerate()) == (1,2,16000)
        pcm = np.frombuffer(wav.readframes(160000), dtype='<i2').astype(np.float32)/32768
    for seconds in (3,5,10):
        for gain in (0.25,1.0):
            frames = []
            for start in range(0,seconds*16000-15600+1,7680):
                interpreter.set_tensor(source['index'],pcm[start:start+15600]*gain)
                interpreter.invoke()
                scores = interpreter.get_tensor(target['index']).reshape(-1)
                assert len(scores)==521 and np.all(np.isfinite(scores)) and np.all((scores>=0)&(scores<=1))
                frames.append(scores.copy())
                rows.append(f"{clip['file']}\t{seconds}\t{gain}\t"+'\t'.join(f'{float(scores[labels.index(label)]):.8f}' for label in selected))
            mean = np.mean(frames,axis=0)
            print(clip['file'], seconds, gain, [(labels[i], round(float(mean[i]),3)) for i in np.argsort(mean)[-3:][::-1]])
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_text('\n'.join(rows)+'\n')
print('PASS: pinned model signature and finite outputs for 24 prerecorded duration/gain cases. Six-environment accuracy is NOT established.')
