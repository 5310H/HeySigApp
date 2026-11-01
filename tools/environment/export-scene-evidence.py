"""Export model observations for the exact shared Kotlin decision reporter."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import numpy as np
from scene_corpus import load_manifest,DEFAULT_MANIFEST

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--features',type=Path,required=True)
parser.add_argument('--manifest',type=Path,default=DEFAULT_MANIFEST)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
manifest=load_manifest(args.manifest)
archive=np.load(args.features,allow_pickle=False)
assert str(archive['manifestSha256'])==hashlib.sha256(args.manifest.read_bytes()).hexdigest()
assert str(archive['modelSha256'])==manifest['encoderSha256']
records=json.loads(str(archive['records']))
features=archive['features'];frames=archive['eventFrames'];offsets=archive['eventOffsets']
labels=(Path(__file__).resolve().parents[2]/'audioAndroid/src/main/assets/environment/labels.txt').read_text().splitlines()
required=['Speech','Conversation','Narration, monologue','Hubbub, speech noise, speech babble','Crowd','Chatter','Silence',
          'Vehicle','Traffic noise, roadway noise','Engine','Tools','Mechanisms','Dishes, pots, and pans','Vacuum cleaner',
          'Water','Wind','Mechanical fan','Music']
indices=[labels.index(label) for label in required]
assert features.shape==(len(records),2048) and offsets.shape==(len(records)+1,)
assert offsets[0]==0 and offsets[-1]==len(frames) and frames.shape[1]==521
data=bytearray(b'HSGFEA1\n'+bytes.fromhex(manifest['encoderSha256'])+bytes.fromhex(str(archive['manifestSha256']))+struct.pack('<i',len(records)))
references=['index\tclip\treference\tpartition\tsourceGroup\tseconds\tgain\toffsetSeconds']
for i,row in enumerate(records):
    observations=frames[offsets[i]:offsets[i+1]][:,indices]
    assert len(observations)==(row['seconds']*16000-15600)//7680+1
    data+=features[i].astype('<f8').tobytes()+struct.pack('<i',len(observations))+observations.astype('<f8').tobytes()
    references.append('\t'.join(map(str,[i]+[row[key] for key in ('clip','reference','partition','sourceGroup','seconds','gain','offsetSeconds')])))
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_bytes(data)
args.output.with_suffix('.tsv').write_text('\n'.join(references)+'\n')
print('Exported',len(records),'scalar cases; no waveform samples')
