"""Regress the offline bit model against frozen original-CIL comparison digests."""
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from sig3_chirp_packet import control_bits, crc8, position_command

evidence = json.loads((ROOT / 'fixtures/chirp-validation-evidence.json').read_text())
reference = evidence['packetVerification']
assert evidence['rea']['originalMethodHashesAgree']
assert reference['frame_checks'] == 131072

def polynomial_crc(command):
    value = command << 8
    for degree in range(15, 7, -1):
        if value & (1 << degree):
            value ^= 0x107 << (degree-8)
    return value

for command in range(256):
    assert crc8(command) == polynomial_crc(command)

def decode(bits):
    result = 0
    for bit in bits:
        result = (result << 1) | bit
    return result

digests = {mode: hashlib.sha256() for mode in (False, True)}
for command in range(256):
    for address in range(256):
        for mode in (False, True):
            frame = control_bits(command, address, address_xor=mode)
            assert decode(frame.data_a[::2] + frame.data_b[1::2]) == command
            assert decode(frame.data_a[1::2] + frame.data_b[::2]) == frame.checksum
            digests[mode].update(bytes([frame.checksum, *frame.data_a, *frame.data_b]))
assert digests[True].hexdigest() == reference['frame_sha256']['address_xor_on']
assert digests[False].hexdigest() == reference['frame_sha256']['address_xor_off']
for family, observed in evidence['commandVerification']['families'].items():
    assert [position_command(family, i) for i in range(len(observed['positions']))] == observed['positions']

for bad in (-1, 256, True, 1.5, '1', None):
    for operation in (lambda: crc8(bad), lambda: control_bits(16, bad, address_xor=True)):
        try: operation()
        except ValueError: pass
        else: raise AssertionError('Invalid byte accepted: ' + repr(bad))
for family, bad in [('program', 6), ('volume', 16), ('balance', -1), ('tinnitus', True), ('unknown', 0)]:
    try: position_command(family, bad)
    except ValueError: pass
    else: raise AssertionError('Invalid protocol position accepted')
try: control_bits(16, 0, address_xor=1)
except ValueError: pass
else: raise AssertionError('Profile mode must be explicit boolean')
print('PASS: frozen original-CIL digests, 131,072 bit frames, command ranges, inverse decoding and invalid inputs')
