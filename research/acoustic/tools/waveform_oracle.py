"""Bounded interpreter for selected original Audio DLL waveform methods.

No .NET runtime, native calls, audio, or device access. Unsupported calls/opcodes
fail. BCL array/math operations are modeled in Python. Profile values supplied
by caller; does not establish active device configuration.
"""
import hashlib
import math
from pathlib import Path
import dnfile
from dncil.cil.body.reader import read_method_body_from_bytes

class Ref:
    def __init__(self, container, key): self.container, self.key = container, key
    def get(self): return self.container[self.key]
    def set(self, value): self.container[self.key] = value

class Matrix:
    def __init__(self, rows, columns):
        if not 0 < rows <= 4 or not 0 < columns <= 4800: raise ValueError('Matrix bounds')
        self.rows, self.columns = rows, columns
        self.data = [[0] * columns for _ in range(rows)]

class WaveformOracle:
    ALLOWED = {
        'AcousticConstants': {'.cctor'},
        'Utilities': {'CalculateCrc8', 'GetBitByIndex', 'InvertByteValue', 'ConstructDataAForControlTone', 'ConstructDataBForControlTone'},
        'PulseEncoder': {'EncodePulse'},
        'AcousticTone': {'GenerateControlTone', 'ModulateEncodedPulse'},
        'LowPassFilterGenerator': {'CreateLowPassFilter2'},
        'ToneGenerationParameters': {'CalculatedFreqArrays', 'CreateFrequencyScheme', 'CreateLowPassShapeWithoutOverlapping2',
            'get_NumberOfPulse', 'get_PreCalculateFreqArrays', 'get_Gain', 'get_SampleRate', 'get_BaseFrequency', 'get_FrequencyBandSpace', 'get_IsXorIngCrcEnabled'},
    }
    def __init__(self, path):
        raw = Path(path).read_bytes()
        if len(raw) > 8*1024*1024: raise ValueError('DLL size limit')
        self.sha256 = hashlib.sha256(raw).hexdigest()
        pe = dnfile.dnPE(data=raw)
        tables = pe.net.mdtables
        self.labels, self.methods, self.hashes = {}, {}, {}
        for t in tables.TypeDef.rows:
            for f in t.FieldList: self.labels[0x04000000 | f.row_index] = str(f.row.Name)
            for m in t.MethodList:
                name = str(t.TypeName)+'::'+str(m.row.Name)
                self.labels[0x06000000 | m.row_index] = name
                if str(m.row.Name) in self.ALLOWED.get(str(t.TypeName), set()):
                    body = read_method_body_from_bytes(pe.get_data(m.row.Rva))
                    self.methods[name] = body.instructions
                    self.hashes[name] = hashlib.sha256(body.get_instruction_bytes()).hexdigest()
        for i, t in enumerate(tables.TypeRef.rows, 1):
            self.labels[0x01000000 | i] = str(t.TypeNamespace)+'.'+str(t.TypeName)
        for i, r in enumerate(tables.MemberRef.rows, 1):
            prefix = {'TypeRef':0x01000000, 'TypeDef':0x02000000, 'TypeSpec':0x1b000000}.get(r.Class.table.name)
            owner = self.labels.get(prefix | r.Class.row_index, f'TypeSpec:{r.Class.row_index}')
            self.labels[0x0a000000 | i] = owner+'::'+str(r.Name)
        for i, r in enumerate(tables.MethodSpec.rows, 1):
            prefix = 0x06000000 if r.Method.table.name == 'MethodDef' else 0x0a000000
            self.labels[0x2b000000 | i] = self.labels[prefix | r.Method.row_index]
        self.static = {}
        self.run('AcousticConstants::.cctor', [])

    def run(self, name, args, depth=0):
        if depth > 12: raise ValueError('Call depth exceeded')
        instructions = self.methods[name]
        targets = {i.offset:n for n,i in enumerate(instructions)}
        stack, locals_, pc, budget = [], {}, 0, 5000000
        def take(n):
            values = stack[-n:]; del stack[-n:]; return values
        def index(op, operand): return operand.index if operand is not None else int(op.rsplit('.',1)[1])
        while pc < len(instructions):
            budget -= 1
            if budget < 0: raise ValueError('Instruction budget exceeded')
            i = instructions[pc]; pc += 1
            op, operand = i.opcode.name, i.operand
            if op.startswith('ldc.i4'):
                suffix = op[6:]; stack.append(int(operand) if suffix in ('','.s') else -1 if suffix == '.m1' else int(suffix[1:]))
            elif op in ('ldc.r8','ldc.r4'): stack.append(float(operand))
            elif op.startswith('ldarga'): stack.append(Ref(args,index(op,operand)))
            elif op.startswith('ldarg'): stack.append(args[index(op,operand)])
            elif op.startswith('starg'): args[index(op,operand)] = stack.pop()
            elif op.startswith('ldloca'): stack.append(Ref(locals_,index(op,operand)))
            elif op.startswith('ldloc'): stack.append(locals_[index(op,operand)])
            elif op.startswith('stloc'): locals_[index(op,operand)] = stack.pop()
            elif op == 'dup': stack.append(stack[-1])
            elif op == 'pop': stack.pop()
            elif op == 'ldfld': stack.append(stack.pop()[self.labels[operand.value]])
            elif op == 'ldsfld': stack.append(self.static[self.labels[operand.value]])
            elif op == 'stsfld': self.static[self.labels[operand.value]] = stack.pop()
            elif op == 'ldlen': stack.append(len(stack.pop()))
            elif op == 'newarr':
                size = stack.pop()
                if self.labels[operand.value] not in ('System.Byte','System.Double') or not 0 <= size <= 422400: raise ValueError('Array bounds/type')
                stack.append([0]*size)
            elif op.startswith('ldelem'):
                array,j = take(2); stack.append(array[j])
            elif op in ('stelem.i1','stelem.r8'):
                array,j,value = take(3); array[j] = value & 255 if op == 'stelem.i1' else value
            elif op == 'ldind.r8': stack.append(stack.pop().get())
            elif op == 'stind.r8': ref,value = take(2); ref.set(value)
            elif op.startswith('conv.'):
                value = stack.pop()
                if op == 'conv.r8': value = float(value)
                elif op == 'conv.u1': value = int(value)&255
                elif op == 'conv.i2': value = ((int(value)+32768)&65535)-32768
                elif op == 'conv.i4': value = int(value)
                else: raise ValueError('Unsupported conversion: '+op)
                stack.append(value)
            elif op == 'ceq':
                a,b = take(2); stack.append(int(a == b))
            elif op in ('add','sub','mul','div','and','xor','shl','shr'):
                a,b = take(2)
                value = {'add':lambda:a+b,'sub':lambda:a-b,'mul':lambda:a*b,
                    'div':lambda:a/b if isinstance(a,float) or isinstance(b,float) else int(a/b),
                    'and':lambda:a&b,'xor':lambda:a^b,'shl':lambda:a<<(b&31),'shr':lambda:a>>(b&31)}[op]()
                stack.append(value)
            elif op in ('br','br.s'): pc = targets[operand]
            elif op.startswith(('brtrue','brfalse')):
                if bool(stack.pop()) == op.startswith('brtrue'): pc = targets[operand]
            elif op.startswith(('blt','bge','bgt')):
                a,b = take(2)
                if (a<b if op.startswith('blt') else a>=b if op.startswith('bge') else a>b): pc = targets[operand]
            elif op in ('call','callvirt','newobj'):
                callee = self.labels[operand.value]
                if callee in self.methods:
                    arity = {'CalculateCrc8':2,'GetBitByIndex':3,'InvertByteValue':2,
                        'ConstructDataAForControlTone':4,'ConstructDataBForControlTone':4,
                        'EncodePulse':3,'GenerateControlTone':3,'ModulateEncodedPulse':2,
                        'CreateLowPassFilter2':3}.get(callee.split('::')[1],1)
                    result = self.run(callee,take(arity),depth+1); stack.append(result)
                elif callee in ('TypeSpec:3::.ctor','TypeSpec:4::.ctor'):
                    stack.append(Matrix(*take(2)))
                elif callee in ('TypeSpec:3::Get','TypeSpec:4::Get'):
                    matrix,row,column = take(3); stack.append(matrix.data[row][column])
                elif callee in ('TypeSpec:3::Set','TypeSpec:4::Set'):
                    matrix,row,column,value = take(4); matrix.data[row][column] = value
                elif callee == 'TypeSpec:4::Address':
                    matrix,row,column = take(3); stack.append(Ref(matrix.data[row],column))
                elif callee == 'System.Array::GetLength':
                    matrix,dimension = take(2)
                    if dimension not in (0,1): raise ValueError('Invalid dimension')
                    stack.append(matrix.rows if dimension == 0 else matrix.columns)
                elif callee == 'System.Array::Resize':
                    ref,size = take(2); array = ref.get()
                    if not 0 <= size <= 422400: raise ValueError('Resize bounds')
                    ref.set(array[:size]+[0]*max(0,size-len(array)))
                elif callee == 'System.Array::ConstrainedCopy':
                    source,start,destination,offset,count = take(5)
                    if min(start,offset,count)<0 or start+count>len(source) or offset+count>len(destination): raise ValueError('Copy bounds')
                    destination[offset:offset+count] = source[start:start+count]
                elif callee in ('System.Math::Sin','System.Math::Cos'):
                    stack.append((math.sin if callee.endswith('Sin') else math.cos)(stack.pop()))
                else: raise ValueError('Unsupported call: '+callee)
            elif op == 'ret':
                if name.endswith('::.cctor'):
                    if stack: raise ValueError('Static constructor stack')
                    return
                result = stack.pop()
                if stack: raise ValueError('Return stack: '+name)
                return result
            else: raise ValueError('Unsupported opcode: '+op)
        raise ValueError('Missing return')

    def pcm(self, command, address, *, rate, base, spacing, pulse_samples, gain, address_xor):
        if command not in (128,129,*range(16,70)) or not 0 <= address <= 255:
            raise ValueError('Only battery, program and numeric-slider requests admitted')
        if not 100 <= pulse_samples <= 4800 or gain < 1: raise ValueError('Profile bounds')
        profile = {'<NumberOfPulse>k__BackingField':pulse_samples,
            '<SampleRate>k__BackingField':rate,'<BaseFrequency>k__BackingField':base,
            '<FrequencyBandSpace>k__BackingField':spacing,'<Gain>k__BackingField':gain,
            '<IsXorIngCrcEnabled>k__BackingField':address_xor,'_lowPassFilterGenerator':{}}
        profile['<PreCalculateFreqArrays>k__BackingField'] = self.run('ToneGenerationParameters::CalculatedFreqArrays',[profile])
        instance = {'_utilities':{},'_pulseEncoder':{'_utilities':{}},'_toneGenerationParameters':profile}
        return bytes(self.run('AcousticTone::GenerateControlTone',[instance,command,address]))
