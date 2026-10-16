"""Fingerprint the statically traced battery path in caller-supplied assemblies.

Requires the same pinned dnfile/dncil dependencies as verify_sig3_chirp_dll.py.
Produces method identities/hashes and the encoder gain table; does not execute DLLs or access a phone.
The findings in notes/proto-request-trace-2026-10-14.md are manual CIL analysis.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path
import dnfile
from dncil.cil.body.reader import read_method_body_from_bytes

SELECTED = {
    'RTA.Core.dll': {
        ('<PlayBatteryStatusSound>d__31', 'MoveNext'),
        ('<PlayBatteryStatusSound>d__26', 'MoveNext'),
        ('<SaveBasicHearingSystem>d__20', 'MoveNext'),
        ('FoundationConfigurationManager', 'DefaultConfiguration'),
        ('BrandConverter', 'ConvertToBrand'),
        ('<SetBasicHearingSystem>d__10', 'MoveNext'),
    },
    'WSA.Foundation.Acoustic.dll': {
        ('BatteryExtension', 'WriteToneAsync'),
        ('BatteryExtension', 'GetBatteryToneId'),
        ('<WriteStatusToneAsync>d__6', 'MoveNext'),
        ('Device', '.ctor'), ('Device', 'WriteToneAsync'),
        ('HearingSystem', 'WriteToneAsync'),
        ('<GetPersistedArcAddress>d__17', 'MoveNext'),
        ('BrandParser', 'GetBrandIdentifier'),
    },
    'RTA.Maui.Signia.dll': {('Bootstrapper', 'RegisterAcousticClasses')},
    'WSA.Foundation.Audio.dll': {
        ('ToneGenerationConfigurator', 'set_BroadcastLevel'),
        ('AcousticConstants', 'get_GainArray'),
        ('<SendToneAsync>d__6', 'MoveNext'),
        ('ToneGenerationParameters', 'InitializeToneGeneration'),
        ('AcousticTone', 'GenerateControlTone'),
        ('PulseEncoder', 'EncodePulse'),
        ('AudioTrackGenerator', 'GenerateAudioTrack'),
        ('AudioTrackGenerator', 'GetStream'),
        ('<PlayToneAsync>d__6', 'MoveNext'),
        ('TonePlayingParameters', 'SetDefaultValues'),
    },
}


def fingerprint(directory):
    result = {'scope': 'Static method fingerprints and gain table; no runtime configuration or playback validation', 'assemblies': {}}
    for filename, selected in SELECTED.items():
        source = (directory / filename).read_bytes()
        pe = dnfile.dnPE(data=source)
        methods = []
        gain_field = None
        found = set()
        for t in pe.net.mdtables.TypeDef.rows:
            for m in t.MethodList:
                identity = str(t.TypeName), str(m.row.Name)
                if identity not in selected:
                    continue
                if identity in found:
                    raise ValueError(f'Ambiguous method: {identity}')
                found.add(identity)
                body = read_method_body_from_bytes(pe.get_data(m.row.Rva))
                if identity == ('AcousticConstants', 'get_GainArray'):
                    instructions = body.instructions
                    if instructions[0].opcode.name != 'ldc.i4.3':
                        raise ValueError('Unexpected gain array length')
                    gain_field = next(i.operand.value & 0xffffff for i in instructions if i.opcode.name == 'ldtoken')
                methods.append({'type': identity[0], 'method': identity[1],
                    'token': hex(0x06000000 | m.row_index), 'rva': hex(m.row.Rva),
                    'il_sha256': hashlib.sha256(body.get_instruction_bytes()).hexdigest()})
        if found != selected:
            raise ValueError(f'Missing methods in {filename}: {selected-found}')
        if gain_field is not None:
            row = next(r for r in pe.net.mdtables.FieldRva.rows if r.Field.row_index == gain_field)
            result['encoder_gain_divisors'] = list(struct.unpack('<3d', pe.get_data(row.Rva,24)))
        result['assemblies'][filename] = {'sha256': hashlib.sha256(source).hexdigest(), 'methods': methods}
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('assemblies', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.output.write_text(json.dumps(fingerprint(args.assemblies), indent=2) + '\n')
    print(f'Fingerprinted {sum(len(a["methods"]) for a in json.loads(args.output.read_text())["assemblies"].values())} traced methods')
