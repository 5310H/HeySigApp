"""Compare the offline model against original CIL in a caller-supplied Audio DLL.

Requires dnfile==0.18.0 and dncil==1.0.2. A restricted interpreter admits four
pure bit/checksum methods and the encoder prefix, stopping before pulse encoding.
It cannot execute .NET/Android code, files, audio, or native calls. Original DLLs
stay outside git.
"""
import argparse
import hashlib
import json
from functools import cache, cached_property
from pathlib import Path
import dnfile
from dncil.cil.body.reader import read_method_body_from_bytes
from sig3_chirp_packet import control_bits, crc8

NAMES = ('CalculateCrc8', 'GetBitByIndex',
         'ConstructDataAForControlTone', 'ConstructDataBForControlTone',
         'GenerateControlTone')


class Oracle:
    def __init__(self, path):
        if Path(path).stat().st_size > 8 * 1024 * 1024:
            raise ValueError('DLL exceeds the 8 MiB analysis limit')
        self.source = Path(path).read_bytes()
        self.digest = hashlib.sha256(self.source).hexdigest()
        self.pe = dnfile.dnPE(data=self.source)
        self.methods = {}
        self.tokens = {}
        self.identities = {}
        self.fields = {0x04000000 | n: str(f.Name)
                       for n, f in enumerate(self.pe.net.mdtables.Field.rows, 1)}
        for t in self.pe.net.mdtables.TypeDef.rows:
            if str(t.TypeNamespace) != 'WSA.Foundation.Audio':
                continue
            for m in t.MethodList:
                name = str(m.row.Name)
                token = 0x06000000 | m.row_index
                if (str(t.TypeName), name) in (
                    ('ToneGenerationParameters', 'get_IsXorIngCrcEnabled'),
                    ('PulseEncoder', 'EncodePulse')):
                    if name == 'get_IsXorIngCrcEnabled':
                        getter = read_method_body_from_bytes(self.pe.get_data(m.row.Rva)).instructions
                        if ([i.opcode.name for i in getter] != ['ldarg.0', 'ldfld', 'ret']
                                or self.fields.get(getter[1].operand.value) != '<IsXorIngCrcEnabled>k__BackingField'):
                            raise ValueError('Profile getter is not a plain stored boolean')
                    self.tokens[token] = name
                if not ((str(t.TypeName) == 'Utilities' and name in NAMES[:4])
                        or (str(t.TypeName) == 'AcousticTone' and name == 'GenerateControlTone')):
                    continue
                body = read_method_body_from_bytes(self.pe.get_data(m.row.Rva))
                self.methods[name] = body
                self.tokens[token] = name
                self.identities[name] = {'token': hex(token), 'rva': hex(m.row.Rva),
                    'il_sha256': hashlib.sha256(body.get_instruction_bytes()).hexdigest()}
        if set(self.methods) != set(NAMES):
            raise ValueError('Required pure encoder methods not found')

    def run(self, name, *values):
        body = self.methods[name]
        instructions = body.instructions
        targets = {i.offset: n for n, i in enumerate(instructions)}
        prefix = name == 'GenerateControlTone'
        args = ([{'_utilities': None, '_toneGenerationParameters': values[2],
                  '_pulseEncoder': None}, *values[:2]] if prefix else [None, *values])
        locals_ = {}; stack = []; pc = 0; budget = 4096
        while pc < len(instructions):
            budget -= 1
            if budget <= 0:
                raise ValueError('CIL budget exceeded')
            i = instructions[pc]; op = i.opcode.name; operand = i.operand
            pc += 1
            if op.startswith('ldc.i4'):
                suffix = op.removeprefix('ldc.i4')
                stack.append(int(operand) if suffix in ('', '.s') else -1 if suffix == '.m1' else int(suffix[1:]))
            elif op in ('ldarg', 'ldarg.s', 'ldarg.0', 'ldarg.1', 'ldarg.2', 'ldarg.3'):
                index = operand.index if operand is not None else int(op.rsplit('.', 1)[1])
                stack.append(args[index])
            elif op.startswith('starg'):
                args[operand.index] = stack.pop()
            elif op in ('ldloc', 'ldloc.s', 'ldloc.0', 'ldloc.1', 'ldloc.2', 'ldloc.3'):
                index = operand.index if operand is not None else int(op.rsplit('.', 1)[1])
                stack.append(locals_[index])
            elif op.startswith('stloc'):
                index = operand.index if operand is not None else int(op.rsplit('.', 1)[1])
                locals_[index] = stack.pop()
            elif op == 'dup':
                stack.append(stack[-1])
            elif op == 'ldfld' and prefix:
                field = self.fields.get(operand.value)
                if field not in ('_utilities', '_toneGenerationParameters', '_pulseEncoder'):
                    raise ValueError('Unapproved instance field')
                stack.append(stack.pop()[field])
            elif op == 'newarr':
                if operand.value != (0x01000000 | self.byte_type):
                    raise ValueError('Only byte arrays are admitted')
                size = stack.pop()
                if not 0 <= size <= 8:
                    raise ValueError('Array limit exceeded')
                stack.append([0] * size)
            elif op == 'stelem.i1':
                value = stack.pop(); index = stack.pop(); array = stack.pop()
                array[index] = value & 255
            elif op in ('call', 'callvirt'):
                callee = self.tokens.get(operand.value)
                if prefix and callee == 'get_IsXorIngCrcEnabled':
                    stack.append(stack.pop())  # supplied profile property, no target code
                    continue
                if prefix and callee == 'EncodePulse':
                    b = stack.pop(); a = stack.pop(); stack.pop()
                    if stack: raise ValueError('Unexpected encoder-prefix stack')
                    return locals_[0], tuple(a), tuple(b)
                arity = {'GetBitByIndex': 2, 'CalculateCrc8': 1,
                         'ConstructDataAForControlTone': 3, 'ConstructDataBForControlTone': 3}
                if callee not in arity or (not prefix and callee != 'GetBitByIndex'):
                    raise ValueError('Unapproved CIL call')
                parameters = [stack.pop() for _ in range(arity[callee])][::-1]
                stack.pop()
                stack.append(self.pure(callee, *parameters))
            elif op == 'conv.u1':
                stack.append(stack.pop() & 255)
            elif op in ('add', 'sub', 'and', 'xor', 'shl', 'shr'):
                right = stack.pop(); left = stack.pop()
                if op == 'add': result = left + right
                elif op == 'sub': result = left - right
                elif op == 'and': result = left & right
                elif op == 'xor': result = left ^ right
                elif op == 'shl': result = left << (right & 31)
                else: result = left >> (right & 31)
                stack.append(result)
            elif op in ('br', 'br.s'):
                pc = targets[operand]
            elif op in ('brtrue.s', 'brfalse.s'):
                condition = bool(stack.pop())
                if condition == (op == 'brtrue.s'): pc = targets[operand]
            elif op in ('bgt.s', 'bge.s'):
                right = stack.pop(); left = stack.pop()
                if (left > right if op == 'bgt.s' else left >= right): pc = targets[operand]
            elif op == 'ret':
                result = stack.pop()
                if stack: raise ValueError('Unexpected return stack')
                return result
            else:
                raise ValueError('Unapproved CIL opcode: ' + op)
        raise ValueError('Missing CIL return')

    @cache
    def pure(self, name, *values):
        if name not in NAMES[:4]: raise ValueError('Not a pure helper')
        return self.run(name, *values)

    @cached_property
    def byte_type(self):
        return next(n for n, t in enumerate(self.pe.net.mdtables.TypeRef.rows, 1)
                    if str(t.TypeNamespace) == 'System' and str(t.TypeName) == 'Byte')


def verify(path):
    oracle = Oracle(path)
    original_crc = [oracle.pure('CalculateCrc8', command) for command in range(256)]
    assert original_crc == [crc8(command) for command in range(256)]
    digests = {mode: hashlib.sha256() for mode in (False, True)}
    pairs = 0
    for command in range(256):
        for address in range(256):
            for mode in (False, True):
                checksum, a, b = oracle.run('GenerateControlTone', command, address, mode)
                expected = control_bits(command, address, address_xor=mode)
                assert (checksum, a, b) == (expected.checksum, expected.data_a, expected.data_b), (command, address, mode)
                if mode: pairs += 1
                digests[mode].update(bytes([checksum, *a, *b]))
    if hashlib.sha256(Path(path).read_bytes()).hexdigest() != oracle.digest:
        raise ValueError('DLL changed during analysis')
    return {'dll_sha256': oracle.digest,
            'method_identities': oracle.identities, 'crc_commands': 256,
            'command_address_pairs': pairs, 'frame_checks': 131072,
            'frame_sha256': {'address_xor_on': digests[True].hexdigest(),
                             'address_xor_off': digests[False].hexdigest()}}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dll', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = verify(args.dll)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('PASS: 256 original-CIL CRC cases and 131,072 command/address/mode frame checks')
