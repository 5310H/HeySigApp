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
parser.add_argument('--modules', nargs='+', choices=('androidApp','wearApp'), default=['androidApp','wearApp'])
args = parser.parse_args()
litert = json.loads((ROOT/'third_party/litert/integrity.json').read_text())['native']
sherpa = json.loads((ROOT/'third_party/sherpa-onnx/integrity.json').read_text())['native']
rnnoise = json.loads((ROOT/'third_party/rnnoise/integrity.json').read_text())['native']
for module, variant in ((m,v) for m in args.modules for v in args.variants):
    expected = dict(litert, **sherpa, **rnnoise) if module == 'androidApp' else dict(litert, **rnnoise)
    apk = ROOT/f'{module}/build/outputs/apk/{variant}/{module}-{variant}.apk'
    with zipfile.ZipFile(apk) as archive, apk.open('rb') as source:
        assert hashlib.sha256(archive.read('assets/environment/yamnet.tflite')).hexdigest() == 'b2fc8fc3ff9c960bde60b5fea71335d4cb2a35c4d1b011c3a80927fd64d8a2e4', 'Bundled encoder mismatch'
        assert hashlib.sha256(archive.read('assets/environment/scene-head.bin')).hexdigest() == '0500150700fc75a8fc4b4c59f0cd284fea0b8d61d9b5fe6de074b24358233bfd', 'Bundled scene head mismatch'
        assert hashlib.sha256(archive.read('assets/environment/small-group-head.bin')).hexdigest() == 'b32f1124b31e4961f52c1153fa8220d3cc10b374292dfdb199b7bcb852e56be8', 'Bundled Small Group head mismatch'
        assert hashlib.sha256(archive.read('assets/environment/HEAD_NOTICE')).hexdigest() == '05ae0943f40d8858a9f9e33385084054fc4ad3263cc297ee2c57ec2dad10eb8b', 'Bundled scene attribution mismatch'
        assert hashlib.sha256(archive.read('assets/environment/NOTICE')).hexdigest() == 'e237d4cee44da23a7ac5297a86765f8bc9e00b528bec16920f0066df98f1fcc2', 'Bundled monitoring profile notice mismatch'
        assert not any(name.startswith('assets/scene-fixtures/') for name in archive.namelist()), 'Test recordings packaged in app'
        assert hashlib.sha256(archive.read('assets/environment/labels.txt')).hexdigest() == '8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051', 'Bundled labels mismatch'
        if module in ('androidApp', 'wearApp'):
            assert 'assets/masking/masking-head.txt' not in archive.namelist(), 'Retired spectral head packaged'
            assert not any(name.startswith('assets/masking-fixtures/') for name in archive.namelist()), 'Masking test audio packaged'
            for folder, manifest in [('masking', 'third_party/masking/integrity.json'), ('separation', 'third_party/rnnoise/integrity.json')]:
                pins = json.loads((ROOT/manifest).read_text())
                for name, expected_hash in pins.get('artifacts', pins.get('assets', {})).items():
                    assert hashlib.sha256(archive.read(f'assets/{folder}/{name}')).hexdigest() == expected_hash, f'Unexpected {folder}/{name}'
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
