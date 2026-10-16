"""Decode one control tone from a PCM16 WAV using the explicitly selected profile.

Requires numpy. This is a postmix signal comparison, not original-buffer recovery.
Rejects empty/weak/ambiguous tone sequences instead of guessing an address.
"""
import argparse
import hashlib
import json
from pathlib import Path
import wave
import numpy as np
from sig3_chirp_packet import control_bits


def decode(path, command, source_rate, pulse_samples, frequencies):
    raw = path.read_bytes()
    with wave.open(str(path)) as wav:
        if wav.getsampwidth() != 2 or wav.getnchannels() not in (1, 2):
            raise ValueError('Expected mono/stereo PCM16 WAV')
        rate = wav.getframerate()
        signal = np.frombuffer(wav.readframes(wav.getnframes()), dtype='<i2').reshape(-1, wav.getnchannels()).mean(axis=1)
    onset = np.flatnonzero(np.abs(signal) > 100)
    if not len(onset):
        raise ValueError('No signal above onset threshold')
    onset = int(onset[0])
    pulse = pulse_samples * rate / source_rate
    if pulse < 650 or onset + 31*pulse + 600 > len(signal):
        raise ValueError('Capture/profile does not admit 32 full analysis windows')
    basis = np.exp(-2j*np.pi*np.asarray(frequencies)[:, None]*np.arange(500)/rate)
    slots, ratios = [], []
    for index in range(32):
        start = round(onset + index*pulse) + 100
        magnitudes = np.abs(basis @ signal[start:start+500])
        ranked = sorted(magnitudes)
        ratio = float(ranked[-1] / max(ranked[-2], 1))
        if ratio < 3:
            raise ValueError(f'Ambiguous frequency at pulse {index}')
        ratios.append(ratio)
        slots.append(int(np.argmax(magnitudes)))
    matches = []
    for xor in (False, True):
        for address in range(256):
            bits = control_bits(command, address, address_xor=xor)
            expected = [f for i in range(8) for f in (
                2 if bits.data_a[i] else 0, 3 if bits.data_b[i] else 1,
                0 if bits.data_a[i] else 2, 1 if bits.data_b[i] else 3)]
            if expected == slots:
                matches.append({'address_xor': xor, 'qualified_address': address})
    if len(matches) != 1:
        raise ValueError(f'Expected one exact candidate; found {len(matches)}')
    return {'capture_sha256': hashlib.sha256(raw).hexdigest(), 'sample_rate': rate,
            'expected_command': command, 'source_rate': source_rate, 'pulse_samples': pulse_samples,
            'frequencies_hz': frequencies, 'onset_sample': onset, 'frequency_slots': slots,
            'minimum_frequency_peak_ratio': min(ratios), 'exact_pulse_matches': 32,
            **matches[0], 'scope': 'Known-profile postmix tone sequence; not generator PCM byte equality'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('wav', type=Path)
    parser.add_argument('--command', type=lambda s: int(s, 0), required=True)
    parser.add_argument('--source-rate', type=int, required=True)
    parser.add_argument('--pulse-samples', type=int, required=True)
    parser.add_argument('--frequencies', type=int, nargs=4, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = decode(args.wav, args.command, args.source_rate, args.pulse_samples, args.frequencies)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(f"32 matching pulses: command {args.command:#x}, address {result['qualified_address']}, XOR={result['address_xor']}")
