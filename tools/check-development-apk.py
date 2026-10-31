#!/usr/bin/env python3
"""Reject a phone development APK missing required compiled application classes."""
import struct
import sys
import zipfile


def classes(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Unsupported DEX format')
    def uint(offset):
        return struct.unpack_from('<I', data, offset)[0]
    strings_count, strings_offset = uint(56), uint(60)
    strings = []
    for index in range(strings_count):
        offset = uint(strings_offset + index * 4)
        while data[offset] & 128:
            offset += 1
        offset += 1
        strings.append(data[offset:data.index(b'\0', offset)].decode('utf-8', errors='replace'))
    types_offset = uint(68)
    classes_count, classes_offset = uint(96), uint(100)
    return {strings[uint(types_offset + uint(classes_offset + index * 32) * 4)]
            for index in range(classes_count)}


if __name__ == '__main__':
    with zipfile.ZipFile(sys.argv[1]) as apk:
        defined = set().union(*(classes(apk.read(name)) for name in apk.namelist()
                                if name.startswith('classes') and name.endswith('.dex')))
    required = {'Lcom/heysig/app/HomeActivity;', 'Lcom/heysig/app/ManualRuntime;',
                'Lcom/heysig/app/voice/HandsFreeService;', 'Lcom/heysig/app/EnvironmentFeature;',
                'Lcom/heysig/app/voice/ForegroundVoice;', 'Lcom/heysig/audio/Yamnet;'}
    missing = required - defined
    if missing:
        sys.exit('APK missing required classes: ' + ', '.join(sorted(missing)))
    print('PASS: required phone application classes are defined in APK DEX')
