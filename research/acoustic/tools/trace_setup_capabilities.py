"""Fingerprint manually inspected Signia setup/capability methods, without executing them.

Use the pinned research dependencies and caller-supplied original assemblies.
Tokens apply to the investigated installed version; names guard against token drift.
"""
import argparse
import hashlib
import json
from pathlib import Path

import dnfile
from dncil.cil.body.reader import read_method_body_from_bytes

SELECTED = {
    'RTA.Core.dll': {
        0xb9b: 'Confirm', 0xb9c: 'ItemTapped',
        0x1084: 'MoveNext', 0x11d5: 'UpdateProgramSlots',
        0x14eb: 'MoveNext', 0x158a: 'MoveNext',
    },
    'WSA.Foundation.Persistency.dll': {
        0x1a: 'WithTinnitusProgramAtSlots', 0x30: 'AddTinnitusPrograms',
    },
    'WSA.Foundation.Persistency.Abstractions.dll': {0x31: '.ctor', 0x77: 'TinnitusVolumeControlAvailable'},
    'WSA.Foundation.QrCode.dll': {0x1e: 'MoveNext'},
    'WSA.Foundation.Acoustic.dll': {0x7: '.ctor', 0xb: 'WriteToneAsync', 0x1f2: 'MoveNext', 0x1f6: 'MoveNext'},
    'Component.RemoteControl.dll': {0x83f: 'MoveNext', 0x841: 'MoveNext', 0x887: 'MoveNext'},
    'WSA.Foundation.Bluetooth.dll': {0x961: 'MoveNext', 0xc3f: 'MoveNext'},
}


def fingerprint(directory):
    result = {'scope': 'Static method identities only; semantic conclusions are manual CIL analysis; no phone configuration or receiver firmware examined.', 'assemblies': {}}
    for filename, selected in SELECTED.items():
        source = (directory / filename).read_bytes()
        pe = dnfile.dnPE(data=source)
        methods = []
        for owner in pe.net.mdtables.TypeDef.rows:
            for entry in owner.MethodList:
                if entry.row_index not in selected:
                    continue
                method = entry.row
                if str(method.Name) != selected[entry.row_index]:
                    raise ValueError(f'Unexpected method at {filename}:{entry.row_index:x}')
                body = read_method_body_from_bytes(pe.get_data(method.Rva))
                methods.append({'type': str(owner.TypeNamespace) + '.' + str(owner.TypeName),
                                'method': str(method.Name), 'token': hex(0x06000000 | entry.row_index),
                                'il_sha256': hashlib.sha256(body.get_instruction_bytes()).hexdigest()})
        if len(methods) != len(selected):
            raise ValueError(f'Missing methods in {filename}')
        result['assemblies'][filename] = {'sha256': hashlib.sha256(source).hexdigest(), 'methods': methods}
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('assemblies', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = fingerprint(args.assemblies)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(f"Fingerprinted {sum(len(a['methods']) for a in result['assemblies'].values())} methods")
