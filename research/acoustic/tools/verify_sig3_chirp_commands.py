"""Audit protocol constants and battery-side selection in extracted Signia DLLs.

Read-only; requires dnfile==0.18.0 and dncil==1.0.2. No DLL code is loaded into
a runtime. Enum positions are not a verified conversion from UI slider values.
"""
import argparse
import hashlib
import json
from pathlib import Path
import dnfile
from dncil.cil.body.reader import read_method_body_from_bytes
from sig3_chirp_packet import position_command


def constants(pe):
    labels = {}
    for t in pe.net.mdtables.TypeDef.rows:
        owner = str(t.TypeNamespace) + '.' + str(t.TypeName)
        labels.update({f.row_index: owner + '::' + str(f.row.Name) for f in t.FieldList})
    result = {}
    for c in pe.net.mdtables.Constant.rows:
        if c.Parent.table.name == 'Field' and c.Type in (4, 5, 6, 7, 8, 9, 10, 11):
            result[labels[c.Parent.row_index]] = int.from_bytes(c.Value.value, 'little',
                                                              signed=c.Type in (4, 6, 8, 10))
    return result


def verify(directory):
    acoustic_path = directory / 'WSA.Foundation.Acoustic.dll'
    shared_path = directory / 'WSA.Foundation.Shared.Abstractions.dll'
    acoustic = dnfile.dnPE(str(acoustic_path)); shared = dnfile.dnPE(str(shared_path))
    values = constants(acoustic); sides = constants(shared)
    prefix = 'WSA.Foundation.Acoustic.Extensions.'
    families = {}
    for family, name, count in [('volume', 'VolumeCommand', 16), ('balance', 'SoundBalanceCommand', 16),
                                ('tinnitus', 'TinnitusVolumeCommand', 16), ('program', 'Program', 6)]:
        positions = [values[prefix + name + '::Position%02d' % i] for i in range(count)]
        assert positions == [position_command(family, i) for i in range(count)]
        families[family] = {'positions': positions, 'increase': values[prefix + name + '::Increase'],
                            'decrease': values[prefix + name + '::Decrease']}
    extra = {name: values[prefix + name] for name in
             ('Receiver::Mute', 'Receiver::NotMute', 'StandBy::GoTo', 'StandBy::WakeUp')}
    assert extra == {'Receiver::Mute': 90, 'Receiver::NotMute': 91,
                     'StandBy::GoTo': 80, 'StandBy::WakeUp': 81}
    side_prefix = 'WSA.Foundation.Shared.Abstractions.Models.Side::'
    assert sides[side_prefix + 'Left'] == 0 and sides[side_prefix + 'Right'] == 1
    owner = next(t for t in acoustic.net.mdtables.TypeDef.rows
                 if str(t.TypeName) == 'BatteryExtension' and str(t.TypeNamespace) == prefix[:-1])
    method = next(m.row for m in owner.MethodList if str(m.row.Name) == 'GetBatteryToneId')
    body = read_method_body_from_bytes(acoustic.get_data(method.Rva));ins = body.instructions
    assert [i.opcode.name for i in ins] == ['ldarg.1', 'brfalse.s', 'ldc.i4', 'ret', 'ldc.i4', 'ret']
    assert ins[1].operand == ins[4].offset and ins[2].operand == 129 and ins[4].operand == 128
    return {'acoustic_dll_sha256': hashlib.sha256(acoustic_path.read_bytes()).hexdigest(),
            'shared_dll_sha256': hashlib.sha256(shared_path.read_bytes()).hexdigest(),
            'protocol_positions_not_ui_labels': True, 'families': families, 'extra_commands': extra,
            'battery': {'left': 128, 'right': 129, 'rva': hex(method.Rva),
                        'il_sha256': hashlib.sha256(body.get_instruction_bytes()).hexdigest()}}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('assemblies', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps(verify(args.assemblies), indent=2) + '\n')
    print('PASS: 54 protocol positions, increment/decrement constants, mute/standby and battery-side selection')
