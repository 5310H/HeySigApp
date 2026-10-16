"""Cross-language regression for the experimental Kotlin renderer; no playback.

The default reference follows the inspected encoder instruction ordering.
--original-dll additionally interprets original waveform CIL with modeled BCL
math and arrays. Neither mode compares a live capture or plays audio.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import wave

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from sig3_chirp_packet import control_bits

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--original-dll', type=Path)
parser.add_argument('--evidence', type=Path)
parser.add_argument('--program-smoke', action='store_true', help='Check all six program commands at the startup profile and address-zero test preset')
parser.add_argument('--volume-smoke', action='store_true', help='Check levels 4/8/9 at the accepted address-12 startup profile')
options = parser.parse_args()
oracle = None
if options.original_dll:
    from waveform_oracle import WaveformOracle
    oracle = WaveformOracle(options.original_dll)
evidence = []


def reference(command, address, xor, rate, base, space, seconds, gain):
    bits = control_bits(command, address, address_xor=xor)
    n = round(seconds / 4 * rate)
    # Original four one-hot rows: A selects F1/F3; B selects F2/F4.
    rows = [[0] * 32 for _ in range(4)]
    for i, (a, b) in enumerate(zip(bits.data_a, bits.data_b)):
        rows[0][4*i + 2*a] = 1
        rows[2][4*i + 2*(1-a)] = 1
        rows[1][4*i + 1 + 2*b] = 1
        rows[3][4*i + 1 + 2*(1-b)] = 1
    edge = [.5 - .5 * math.cos(math.pi * i / 50) for i in range(50)]
    shape = edge + [1.] * (n-100) + edge[::-1]
    waves = [[math.sin(j / rate * (2*math.pi*(base+(f-1.5)*space))) * shape[j] / gain
              for j in range(n)] for f in range(4)]
    signal = []
    for i in range(32):
        assert sum(row[i] for row in rows) == 1
        f = rows[1][i] + 2*rows[2][i] + 3*rows[3][i]
        signal.extend(waves[f])
    signal += [0.] * (12*n)
    # Original swaps backwards into the extended buffer, rather than concatenating.
    for i in range(32*n-1, -1, -1):
        signal[i], signal[i+2*n] = signal[i+2*n], signal[i]
    return [int(s*32767) for s in signal]


with tempfile.TemporaryDirectory(prefix='heysig-battery-') as directory:
    tmp = Path(directory)
    jar = tmp / 'proto.jar'
    subprocess.run(['kotlinc', str(ROOT/'research/acoustic/kotlin/ProtoRequest.kt'),
                    '-include-runtime', '-d', str(jar)], check=True)
    count = 0
    requests = [('left', 128), ('right', 129)] + [(f'program{slot}', 0x40+slot-1) for slot in range(1,7)]
    if options.program_smoke:
        requests = requests[2:]
    if options.volume_smoke:
        requests = [('volume4', 0x1b), ('volume8', 0x17), ('volume9', 0x16)]
    for side, command in requests:
        destinations = [(12, True)] if options.volume_smoke else ([(0, True)] if options.program_smoke else [(0, False), (0, True), (170, True), (255, True)])
        profiles = [(44100,15360,512,.0625,1.2)] if options.program_smoke or options.volume_smoke else [(44100,15360,512,.0625,4.), (48000,12000,1024,.025,2.), (44100,15360,512,.0625,1.2)]
        for address, xor in destinations:
            for rate, base, space, seconds, gain in profiles:
                path = tmp / f'{count}.wav'
                args = [side, str(address), 'on' if xor else 'off', str(rate), str(base), str(space), str(seconds), str(gain), str(path)]
                subprocess.run(['java','-jar',str(jar),*args], check=True, stdout=subprocess.DEVNULL)
                with wave.open(str(path)) as wav:
                    assert (wav.getnchannels(), wav.getsampwidth(), wav.getframerate()) == (1,2,rate)
                    data = wav.readframes(wav.getnframes())
                actual = struct.unpack('<'+'h'*(len(data)//2), data)
                expected = reference(command,address,xor,rate,base,space,seconds,gain)
                assert len(actual) == len(expected)
                # JVM/Python libm may differ at integer truncation boundaries by 1 LSB.
                assert max(abs(a-b) for a,b in zip(actual,expected)) <= 1
                if oracle:
                    original = oracle.pcm(command,address,rate=rate,base=base,spacing=space,pulse_samples=round(seconds/4*rate),gain=gain,address_xor=xor)
                    original_samples = struct.unpack('<'+'h'*(len(original)//2),original)
                    assert len(original_samples) == len(actual)
                    maximum = max(abs(a-b) for a,b in zip(actual,original_samples))
                    assert maximum <= 1
                    evidence.append({'side':side,'address':address,'address_xor':xor,'rate':rate,'base':base,'spacing':space,'seconds':seconds,'gain':gain,'samples':len(actual),'maximum_pcm_difference':maximum,'original_pcm_sha256':hashlib.sha256(original).hexdigest(),'kotlin_pcm_sha256':hashlib.sha256(data).hexdigest()})
                count += 1
    for change in [(0,'both'),(1,'256'),(2,'auto'),(3,'0'),(4,'24000'),(6,'NaN'),(7,'0')]:
        args = ['left','0','on','44100','15360','512','.0625','4',str(tmp/'bad.wav')]
        args[change[0]] = change[1]
        result = subprocess.run(['java','-jar',str(jar),*args], capture_output=True)
        assert result.returncode != 0 and not (tmp/'bad.wav').exists()
    print(f'PASS: {count} Kotlin WAV/reference comparisons and 7 invalid-input checks')

if options.evidence:
    if not oracle: raise ValueError('--evidence requires --original-dll')
    options.evidence.write_text(json.dumps({'scope':'Restricted interpretation of original waveform CIL; BCL math/arrays modeled in Python; no device or capture validation','dll_sha256':oracle.sha256,'method_il_sha256':oracle.hashes,'cases':evidence},indent=2)+'\n')
if oracle: print(f'PASS: {len(evidence)} original-CIL waveform comparisons')
