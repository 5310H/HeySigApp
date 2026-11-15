"""Offline TV-score probe. No microphone, device, network, training or program APIs.

Manifest recording entries: id, file (cache basename), sha256, reference,
source, author, license, windows ([offset seconds, duration seconds]).
References are TV, NON_TV, or CONFOUND; author descriptions are not acceptance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

import numpy as np
from tflite_runtime.interpreter import Interpreter

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text())
    model = ROOT / 'audioAndroid/src/main/assets/environment/yamnet.tflite'
    digest = hashlib.sha256(model.read_bytes()).hexdigest()
    expected = json.loads((ROOT / 'shared/src/commonTest/fixtures/environment/scene-head/corpus.json').read_text())['encoderSha256']
    if digest != expected or manifest['encoderSha256'] != expected:
        raise ValueError('Unexpected encoder')
    labels = (ROOT / 'audioAndroid/src/main/assets/environment/labels.txt').read_text().splitlines()
    selected = ['Television', 'Radio', 'Speech', 'Conversation', 'Music', 'Silence']
    indices = {label: labels.index(label) for label in selected}
    engine = Interpreter(model_path=str(model), num_threads=1)
    engine.allocate_tensors()
    inp = engine.get_input_details()[0]
    out = engine.get_output_details()[0]
    results = []
    seen = set()
    for clip in manifest['recordings']:
        if clip['id'] in seen or clip['reference'] not in ('TV', 'NON_TV', 'CONFOUND'):
            raise ValueError('Invalid identity/reference')
        seen.add(clip['id'])
        if Path(clip['file']).name != clip['file'] or not all(clip[k] for k in ('source', 'author', 'license')):
            raise ValueError('Missing source provenance or unsafe filename')
        path = args.cache / clip['file']
        if hashlib.sha256(path.read_bytes()).hexdigest() != clip['sha256']:
            raise ValueError('Recording checksum mismatch: ' + clip['id'])
        for offset, seconds in clip['windows']:
            if type(offset) is not int or offset < 0 or seconds not in (3, 10):
                raise ValueError('Invalid bounded window')
            raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-nostdin', '-i', str(path),
                '-ss', str(offset), '-t', str(seconds), '-ac', '1', '-ar', '16000', '-f', 's16le', 'pipe:1'])
            pcm = np.frombuffer(raw, dtype='<i2')
            if len(pcm) != seconds * 16000:
                raise ValueError('Incomplete window: ' + clip['id'])
            samples = pcm.astype(np.float64) / 32768
            rms = float(np.sqrt(np.mean(samples * samples)))
            peak = float(np.max(np.abs(samples)))
            if peak == 0:
                raise ValueError('No acoustic evidence')
            # Match EnvironmentSignal.inferenceGain; do not normalize all clips to one level.
            gain = max(1., min(16., .05 / max(rms, 1e-12), .95 / peak))
            samples = (samples * gain).astype(np.float32)
            frames = []
            for start in range(0, len(samples) - 15600 + 1, 7680):
                engine.set_tensor(inp['index'], samples[start:start + 15600])
                engine.invoke()
                scores = engine.get_tensor(out['index']).reshape(-1)
                if scores.size != 521 or not np.all(np.isfinite(scores)) or np.any((scores < 0) | (scores > 1)):
                    raise ValueError('Invalid model scores')
                frames.append({label: float(scores[index]) for label, index in indices.items()})
            results.append(dict(clip=clip['id'], reference=clip['reference'], offsetSeconds=offset,
                seconds=seconds, rmsDbfs=20 * np.log10(max(rms, 1e-6)), inferenceGain=gain,
                mean={label: float(np.mean([f[label] for f in frames])) for label in selected},
                televisionPeak=max(f['Television'] for f in frames), frames=frames))
            print(clip['id'], seconds, 'TV mean', round(results[-1]['mean']['Television'], 6), flush=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(dict(encoderSha256=digest,
        manifestSha256=hashlib.sha256(args.manifest.read_bytes()).hexdigest(),
        interpretation='Exploratory raw scores, not calibrated probabilities. No thresholds fitted. Windows from one recording are not independent sources. No device acceptance.',
        recordings=manifest['recordings'], results=results), indent=2) + '\n')


if __name__ == '__main__':
    main()
