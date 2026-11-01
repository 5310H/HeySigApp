#!/usr/bin/env python3
"""Development-only pinned corpus scoring. No microphone, device, or aid APIs.

Use the isolated ai-edge-litert==1.4.0 environment. FFmpeg converts public CC0
previews into bounded mono 16 kHz PCM in memory. Only scalar evidence is written.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
from urllib.parse import urlparse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ENVIRONMENTS = {'QUIET', 'CONVERSATION', 'SMALL_GROUP', 'RESTAURANT', 'LARGE_GROUP', 'NON_SPEECH_NOISE'}


def load_manifest(path):
    manifest = json.loads(path.read_text())
    if manifest.get('version') != 1 or not re.fullmatch(r'[0-9a-f]{64}', manifest.get('modelSha256', '')):
        raise ValueError('Unsupported manifest or missing model hash')
    recordings = manifest['recordings']
    if not recordings:
        raise ValueError('Empty corpus')
    identifiers = set()
    hashes = set()
    filenames = set()
    partitions = {}
    for clip in recordings:
        if not re.fullmatch(r'[A-Za-z0-9_-]+', clip['id']) or clip['id'] in identifiers:
            raise ValueError('Invalid or repeated recording ID')
        identifiers.add(clip['id'])
        if clip['reference'] not in ENVIRONMENTS or clip['partition'] not in ('development', 'validation'):
            raise ValueError('Invalid reference or partition')
        if not all(clip.get(key, '').strip() for key in ('author', 'sourceGroup', 'title', 'referenceBasis')):
            raise ValueError('Missing provenance or reference basis')
        if any(any(c in value for c in '\t\r\n') for value in clip.values() if isinstance(value, str)):
            raise ValueError('Provenance must fit a single TSV field')
        if clip['license'] != 'CC0-1.0' or clip['licenseUrl'] != 'https://creativecommons.org/publicdomain/zero/1.0/':
            raise ValueError('This corpus tool accepts reviewed CC0 sources only')
        for field, host in (('source', 'freesound.org'), ('download', 'cdn.freesound.org')):
            parsed = urlparse(clip[field])
            if parsed.scheme != 'https' or parsed.hostname != host or parsed.username or parsed.password or parsed.port not in (None, 443):
                raise ValueError('Unapproved corpus URL')
        if not re.fullmatch(r'[0-9a-f]{64}', clip['sha256']) or not re.fullmatch(r'[0-9]+\.mp3', clip['file']):
            raise ValueError('Invalid source pin or cache filename')
        if clip['sha256'] in hashes or clip['file'] in filenames:
            raise ValueError('Duplicate recording bytes or cache filename')
        hashes.add(clip['sha256'])
        filenames.add(clip['file'])
        for kind, value in (('author', clip['author']), ('group', clip['sourceGroup']), ('hash', clip['sha256'])):
            key = kind, value
            if key in partitions and partitions[key] != clip['partition']:
                raise ValueError('Recording/creator/session leaks across development and validation')
            partitions[key] = clip['partition']
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, default=ROOT/'shared/src/commonTest/fixtures/environment/corpus/manifest.json')
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT/'shared/build/environment-corpus/evidence.tsv')
    parser.add_argument('--fetch', action='store_true', help='Explicitly download missing pinned public previews to the developer cache')
    parser.add_argument('--regressions', type=Path, help='Explicitly write compact paired Quiet regression evidence to this directory')
    args = parser.parse_args()
    manifest = load_manifest(args.manifest)
    import numpy as np
    from ai_edge_litert.interpreter import Interpreter
    model = ROOT/'third_party/environment/yamnet-original.tflite'
    if hashlib.sha256(model.read_bytes()).hexdigest() != manifest['modelSha256']:
        raise ValueError('Unexpected model bytes')
    labels = (ROOT/'audioAndroid/src/main/assets/environment/labels.txt').read_text().splitlines()
    engine = Interpreter(model_path=str(model), num_threads=1)
    engine.allocate_tensors()
    source, target = engine.get_input_details()[0], engine.get_output_details()[0]
    if list(source['shape']) != [15600] or list(target['shape']) != [1, 521] or len(labels) != 521:
        raise ValueError('Unexpected model signature')
    args.cache.mkdir(parents=True, exist_ok=True)
    rows = ['clip\tseconds\tgain\t'+'\t'.join(labels)]
    verification_rows = [rows[0]]
    references = ['clip\treference\tpartition\tsourceGroup\tauthor\tsha256\treferenceBasis']
    summaries = []
    for clip in manifest['recordings']:
        path = args.cache/clip['file']
        if not path.exists():
            if not args.fetch:
                raise ValueError('Missing cached source; use --fetch explicitly')
            with urllib.request.urlopen(clip['download'], timeout=90) as response:
                data = response.read(20_000_001)
            if len(data) > 20_000_000 or hashlib.sha256(data).hexdigest() != clip['sha256']:
                raise ValueError('Source exceeds bound or hash mismatch')
            path.write_bytes(data)
        if hashlib.sha256(path.read_bytes()).hexdigest() != clip['sha256']:
            raise ValueError('Cached source hash mismatch: '+clip['id'])
        decoded = subprocess.run(['ffmpeg', '-v', 'error', '-nostdin', '-i', str(path), '-t', '30',
                                  '-ac', '1', '-ar', '16000', '-f', 's16le', 'pipe:1'],
                                 capture_output=True, check=True, timeout=60).stdout
        if not 96000 <= len(decoded) <= 960000 or len(decoded) % 2:
            raise ValueError('Recording too short or decoding exceeds bound')
        pcm = np.frombuffer(decoded, dtype='<i2').astype(np.float32)/32768
        references.append('\t'.join(clip[k] for k in ('id', 'reference', 'partition', 'sourceGroup', 'author', 'sha256', 'referenceBasis')))
        for seconds in (3, 5, 10):
            if len(pcm) < seconds*16000:
                continue  # Never pad or loop a short recording to fabricate a longer test.
            for gain in (0.25, 1.0):
                frames = []
                # Match the Android PCM conversion before deriving the second view.
                samples = (pcm[:seconds*16000]*gain*32768).astype(np.int16).astype(np.float32)/32768
                peak = float(np.max(np.abs(samples)))
                rms = float(np.sqrt(np.mean(samples.astype(np.float64)**2)))
                verification_gain = max(1.0, min(16.0, 0.05/rms, 0.95/peak)) if peak else 1.0
                for start in range(0, seconds*16000-15600+1, 7680):
                    engine.set_tensor(source['index'], samples[start:start+15600])
                    engine.invoke()
                    scores = engine.get_tensor(target['index']).reshape(-1)
                    if not np.all(np.isfinite(scores)) or not np.all((scores >= 0) & (scores <= 1)):
                        raise ValueError('Invalid model scores')
                    frames.append(scores.copy())
                    rows.append(f'{clip["id"]}\t{seconds}\t{gain}\t'+'\t'.join(f'{float(x):.8f}' for x in scores))
                    engine.set_tensor(source['index'], (samples[start:start+15600].astype(np.float64)*verification_gain).astype(np.float32))
                    engine.invoke()
                    verified = engine.get_tensor(target['index']).reshape(-1)
                    if not np.all(np.isfinite(verified)) or not np.all((verified >= 0) & (verified <= 1)):
                        raise ValueError('Invalid verification scores')
                    verification_rows.append(f'{clip["id"]}\t{seconds}\t{gain}\t'+'\t'.join(f'{float(x):.8f}' for x in verified))
                mean = np.mean(frames, axis=0)
                top = [(labels[int(i)], float(mean[i])) for i in np.argsort(mean)[-5:][::-1]]
                summaries.append({'clip': clip['id'], 'seconds': seconds, 'gain': gain, 'reference': clip['reference'],
                                  'partition': clip['partition'], 'pcmSha256': hashlib.sha256(decoded).hexdigest(),
                                  'verificationGain': verification_gain, 'topEvents': top})
                print(clip['id'], seconds, gain, top[:3])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text('\n'.join(rows)+'\n')
    args.output.with_name('verification.tsv').write_text('\n'.join(verification_rows)+'\n')
    args.output.with_name('references.tsv').write_text('\n'.join(references)+'\n')
    args.output.with_suffix('.json').write_text(json.dumps({'modelSha256': manifest['modelSha256'],
        'manifestSha256': hashlib.sha256(args.manifest.read_bytes()).hexdigest(),
        'decoder': subprocess.check_output(['ffmpeg', '-version'], text=True).splitlines()[0], 'cases': summaries}, indent=2)+'\n')
    if args.regressions:
        write_quiet_regressions(rows, verification_rows, args.regressions, manifest)
    print(f'Wrote {len(summaries)} duration/gain cases from {len(manifest["recordings"])} recordings. Creator references are provisional; NOT qualification.')


def write_quiet_regressions(rows, verification_rows, directory, manifest):
    # Four known false Quiet cases plus all six genuine room-tone cases.
    def selected(row):
        return row[0] == 'fs-565535' or (row[2] == '0.25' and (
            row[0] == 'fs-260124' or (row[0] == 'fs-608996' and row[1] == '3')))
    labels = rows[0].split('\t')[3:]
    required = ['Speech', 'Conversation', 'Narration, monologue', 'Hubbub, speech noise, speech babble',
                'Crowd', 'Chatter', 'Silence', 'Vehicle', 'Traffic noise, roadway noise', 'Engine', 'Tools',
                'Mechanisms', 'Dishes, pots, and pans', 'Vacuum cleaner', 'Water', 'Wind', 'Mechanical fan', 'Music']
    indices = [0, 1, 2] + [3+labels.index(label) for label in required]
    directory.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for name, evidence in [('quiet-original.tsv', rows), ('quiet-verification.tsv', verification_rows)]:
        compact = ['\t'.join(evidence[0].split('\t')[i] for i in indices)]
        for line in evidence[1:]:
            row = line.split('\t')
            if selected(row):
                compact.append('\t'.join(row[i] for i in indices))
        data = ('\n'.join(compact)+'\n').encode()
        (directory/name).write_bytes(data)
        hashes[name] = hashlib.sha256(data).hexdigest()
    (directory/'quiet-integrity.json').write_text(json.dumps({
        'modelSha256': manifest['modelSha256'], 'evidenceSha256': hashes,
        'notes': 'PCM16 quantized gain variants; fixed bounded RMS/headroom verification, not acoustic calibration.',
        'sources': [{key: clip[key] for key in ('id', 'sha256')} for clip in manifest['recordings']
                    if clip['id'] in ('fs-565535', 'fs-260124', 'fs-608996')]}, indent=2)+'\n')


if __name__ == '__main__':
    main()
