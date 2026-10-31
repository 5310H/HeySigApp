#!/usr/bin/env python3
"""Check pinned phone/watch native libraries and 16 KB ELF/ZIP alignment; no runtime claim."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--variants', nargs='+', choices=('debug','release'), default=['debug','release'])
args = parser.parse_args()
litert = json.loads((ROOT/'third_party/litert/integrity.json').read_text())['native']
sherpa = json.loads((ROOT/'third_party/sherpa-onnx/integrity.json').read_text())['native']
for module, variant in ((m,v) for m in ('androidApp','wearApp') for v in args.variants):
    expected = dict(litert, **sherpa) if module == 'androidApp' else litert
    apk = ROOT/f'{module}/build/outputs/apk/{variant}/{module}-{variant}.apk'
    with zipfile.ZipFile(apk) as archive, apk.open('rb') as source:
        assert hashlib.sha256(archive.read('assets/environment/yamnet.tflite')).hexdigest() == '10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de', 'Bundled model mismatch'
        assert hashlib.sha256(archive.read('assets/environment/labels.txt')).hexdigest() == '8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051', 'Bundled labels mismatch'
        libraries = {item.filename: item for item in archive.infolist() if item.filename.endswith('.so')}
        assert set(libraries) == {name.replace('jni/', 'lib/', 1) for name in expected}, 'Unexpected native library inventory'
        for name, item in libraries.items():
            data = archive.read(item)
            assert hashlib.sha256(data).hexdigest() == expected[name.replace('lib/', 'jni/', 1)], 'Native checksum mismatch'
            assert data[:4] == b'\x7fELF' and data[5] == 1, 'Expected little-endian ELF'
            elf64 = data[4] == 2
            offset = struct.unpack_from('<Q' if elf64 else '<I', data, 32 if elf64 else 28)[0]
            size, count = struct.unpack_from('<HH', data, 54 if elf64 else 42)
            loads = 0
            for index in range(count):
                header = offset + index*size
                if struct.unpack_from('<I', data, header)[0] == 1:
                    alignment = struct.unpack_from('<Q' if elf64 else '<I', data, header+(48 if elf64 else 28))[0]
                    assert alignment >= 16384, 'ELF load segment is below 16 KB alignment'
                    loads += 1
            assert loads, 'ELF has no load segments'
            source.seek(item.header_offset)
            header = source.read(30)
            filename, extra = struct.unpack_from('<HH', header, 26)
            assert item.compress_type == zipfile.ZIP_STORED, 'Native library must be uncompressed'
            assert (item.header_offset+30+filename+extra) % 16384 == 0, 'Native ZIP data is not 16 KB aligned'
    print(f'{module} {variant}: {len(libraries)} pinned native libraries; ELF and APK ZIP alignment >=16 KB. Runtime/device acceptance pending.')
