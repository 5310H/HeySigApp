#!/usr/bin/env python3
"""Check pinned phone native libraries and 16 KB ELF/ZIP alignment; no runtime claim."""
import hashlib
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
expected = json.loads((ROOT/'third_party/sherpa-onnx/integrity.json').read_text())['native']
for variant in ('debug', 'release'):
    apk = ROOT/f'androidApp/build/outputs/apk/{variant}/androidApp-{variant}.apk'
    with zipfile.ZipFile(apk) as archive, apk.open('rb') as source:
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
    print(f'{variant}: {len(libraries)} pinned native libraries; ELF and APK ZIP alignment >=16 KB. Runtime/device acceptance pending.')
