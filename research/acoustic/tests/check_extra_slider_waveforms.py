"""Compare the built Kotlin balance/tinnitus renderer with original waveform CIL."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import wave

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from waveform_oracle import WaveformOracle

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jar', required=True, type=Path)
parser.add_argument('--original-dll', required=True, type=Path)
parser.add_argument('--evidence', required=True, type=Path)
args = parser.parse_args()
oracle = WaveformOracle(args.original_dll)
cases = []
with tempfile.TemporaryDirectory(prefix='heysig-extra-sliders-') as tmp:
    for request, command in [('balance-4',0x2c),('balance0',0x20),('balance4',0x24),('tinnitus0',0x3f),('tinnitus2',0x3d),('tinnitus4',0x3b)]:
        path = Path(tmp)/(request+'.wav')
        subprocess.run(['java','-cp',str(args.jar),'com.heysig.research.ProtoRequestKt',request,'12','on','44100','15360','512','.0625','1.2',str(path)],check=True,stdout=subprocess.DEVNULL)
        with wave.open(str(path)) as wav:
            assert (wav.getnchannels(),wav.getsampwidth(),wav.getframerate())==(1,2,44100)
            actual=wav.readframes(wav.getnframes())
        expected=oracle.pcm(command,12,rate=44100,base=15360,spacing=512,pulse_samples=689,gain=1.2,address_xor=True)
        assert len(actual)==len(expected)
        a=struct.unpack('<'+'h'*(len(actual)//2),actual);b=struct.unpack('<'+'h'*(len(expected)//2),expected)
        difference=max(abs(x-y) for x,y in zip(a,b));assert difference<=1
        cases.append({'request':request,'command':command,'address':12,'maximum_pcm_difference':difference,'kotlin_pcm_sha256':hashlib.sha256(actual).hexdigest(),'original_pcm_sha256':hashlib.sha256(expected).hexdigest()})
        print('PASS:',request,flush=True)
    for request in ['balance-9','balance8','tinnitus-1','tinnitus16']:
        path=Path(tmp)/'invalid.wav'
        result=subprocess.run(['java','-cp',str(args.jar),'com.heysig.research.ProtoRequestKt',request,'12','on','44100','15360','512','.0625','1.2',str(path)],capture_output=True)
        assert result.returncode and not path.exists()
args.evidence.write_text(json.dumps({'scope':'Original waveform CIL comparison; UI mapping separately established from Signia captures; no live reception in this check','dll_sha256':oracle.sha256,'cases':cases,'invalid_requests_rejected':4},indent=2)+'\n')
print('PASS: six waveforms and four invalid requests')
