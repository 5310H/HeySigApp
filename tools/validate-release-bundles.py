#!/usr/bin/env python3
"""Validate local release candidates, manifest separation and upload signatures.

This does not authorize or perform a Play upload, or certify Play readiness.
"""
import argparse
import base64
import hashlib
import json
import struct
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
BUNDLETOOL_SHA256 = 'a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def run(command, env=None):
    result = subprocess.run(command, capture_output=True, env=env)
    if result.returncode:
        # Never include signing command output or environment in errors.
        raise RuntimeError(f'{command[0]} validation failed (exit {result.returncode})')
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundletool', type=Path, required=True)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    require(hashlib.sha256(args.bundletool.read_bytes()).hexdigest() == BUNDLETOOL_SHA256, 'bundletool checksum mismatch')
    signing = dict(line.split('=', 1) for line in (ROOT / 'private/signing/upload.properties').read_text().splitlines() if '=' in line)
    environment = dict(os.environ, HEYSIG_KEY_PASSWORD=signing['storePassword'])
    certificate = run(['keytool','-exportcert','-keystore',str(ROOT/signing['storeFile']),
        '-storepass:env','HEYSIG_KEY_PASSWORD','-alias',signing['keyAlias']], env=environment)
    expected_signer = hashlib.sha256(certificate).hexdigest()
    reports = []
    for module, application, target, code in [('androidApp','com.heysig.app',36,1_000_011),('wearApp','com.heysig.app',35,2_000_006)]:
        bundle = ROOT / module / f'build/outputs/bundle/release/{module}-release.aab'
        run(['java','-jar',str(args.bundletool),'validate',f'--bundle={bundle}'])
        verified = run(['jarsigner','-J-Duser.language=en','-verify',str(bundle)]).decode()
        require('jar verified.' in verified, f'{module}: JAR signature not verified')
        pem = run(['keytool','-printcert','-rfc','-jarfile',str(bundle)]).decode()
        match = re.search(r'-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----',pem,re.S)
        require(match and hashlib.sha256(base64.b64decode(match[1])).hexdigest() == expected_signer, f'{module}: unexpected signer')
        manifest_bytes = run(['java','-jar',str(args.bundletool),'dump','manifest',f'--bundle={bundle}','--module=base'])
        manifest = ET.fromstring(manifest_bytes)
        require(manifest.get('package') == application, f'{module}: wrong application ID')
        require(int(manifest.get(ANDROID+'versionCode','0')) == code, f'{module}: wrong form-factor version code')
        sdk = manifest.find('uses-sdk')
        require(sdk is not None and int(sdk.get(ANDROID+'targetSdkVersion','0')) == target, f'{module}: target SDK must be {target}')
        app = manifest.find('application')
        require(app is not None and app.get(ANDROID+'debuggable','false') == 'false', f'{module}: missing application or debuggable release')
        require(app.get(ANDROID+'testOnly','false') == 'false', f'{module}: test-only release')
        require(app.get(ANDROID+'usesCleartextTraffic') == 'false', f'{module}: cleartext must be explicitly disabled')
        require(app.get(ANDROID+'allowBackup') == 'false', f'{module}: app backup must remain disabled')
        require(app.get(ANDROID+'dataExtractionRules') is not None, f'{module}: data extraction exclusions missing')
        if module == 'androidApp':
            require(app.get(ANDROID+'enableOnBackInvokedCallback') == 'true', 'Phone: predictive Back must be explicitly enabled for older supported runtimes')
        for element in manifest.iter():
            require(not any(name in element.get(ANDROID+'name','') for name in ('WatchBridgeSetup','ProtoActivity','PlaybackSmoke','Api36Smoke')), 'Development component in bundle manifest')
        require(manifest.find('instrumentation') is None, f'{module}: instrumentation packaged in release')
        require(not any(item.get(ANDROID+'name') in (
            'com.google.android.gms.wearable.MESSAGE_RECEIVED',
            'com.google.android.gms.wearable.REQUEST_RECEIVED',
            'com.google.android.gms.wearable.BIND_LISTENER') for item in manifest.iter('action')),
            f'{module}: background watch listener is not part of this foreground design')
        permissions = [item.get(ANDROID+'name') for item in manifest.findall('uses-permission')]
        expected_permissions = {
            'android.permission.RECORD_AUDIO', 'android.permission.FOREGROUND_SERVICE',
            'android.permission.FOREGROUND_SERVICE_MICROPHONE', 'android.permission.POST_NOTIFICATIONS',
            'android.permission.WAKE_LOCK', 'android.permission.VIBRATE'
        } if module == 'androidApp' else {
            'android.permission.VIBRATE', 'android.permission.RECORD_AUDIO',
            'android.permission.FOREGROUND_SERVICE', 'android.permission.FOREGROUND_SERVICE_MICROPHONE',
            'android.permission.FOREGROUND_SERVICE_SPECIAL_USE', 'android.permission.POST_NOTIFICATIONS',
            'android.permission.WAKE_LOCK', 'android.permission.ACCESS_NETWORK_STATE'
        }
        if module in ('androidApp','wearApp'):
            # AndroidX Core's app-private compatibility permission is signature-only,
            # not a runtime grant or new microphone/background capability.
            internal_permission = application + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
            expected_permissions.add(internal_permission)
            # bundletool dumps the signature enum as numeric 0x00000002.
            require(any(item.get(ANDROID+'name') == internal_permission and
                        item.get(ANDROID+'protectionLevel') in ('signature', '2', '0x2', '0x00000002')
                        for item in manifest.findall('permission')),
                    'Phone: AndroidX internal permission must remain signature-only')
        if module == 'androidApp':
            expected_permissions.update({'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK', 'android.permission.SCHEDULE_EXACT_ALARM', 'android.permission.RECEIVE_BOOT_COMPLETED'})
        require(set(permissions) == expected_permissions, f'{module}: unexpected release permissions {permissions}')
        with zipfile.ZipFile(bundle) as archive:
            names = archive.namelist()
            require(not any(name.endswith(('.keystore','.jks','pairing.json')) for name in names), 'Private configuration packaged')
            native = {name for name in names if name.endswith('.so')}
            if module == 'androidApp':
                integrity = json.loads((ROOT/'third_party/sherpa-onnx/integrity.json').read_text())
                expected_native = {'base/lib/'+name.removeprefix('jni/'): digest for name,digest in integrity['native'].items()}
                require(native == set(expected_native), 'Unexpected or missing wake native libraries/ABIs')
                for name in native:
                    data = archive.read(name)
                    require(hashlib.sha256(data).hexdigest() == expected_native[name], f'Unexpected native bytes: {name}')
                    bits = data[4]
                    offset = struct.unpack_from('<Q' if bits == 2 else '<I', data, 32 if bits == 2 else 28)[0]
                    size,count = struct.unpack_from('<HH',data,54 if bits == 2 else 42)
                    for index in range(count):
                        program = struct.unpack_from('<IIQQQQQQ' if bits == 2 else '<IIIIIIII',data,offset+size*index)
                        if program[0] == 1:
                            require(program[-1] >= 16384, f'Native library lacks 16 KB ELF alignment: {name}')
                for name,digest in integrity['assets'].items():
                    require(hashlib.sha256(archive.read('base/assets/handsfree/'+name)).hexdigest() == digest, f'Wake asset checksum mismatch: {name}')
                services = {item.get(ANDROID+'name'): item for item in app.findall('service')}
                foreground = services.get('com.heysig.app.voice.HandsFreeService')
                require(foreground is not None and foreground.get(ANDROID+'exported') == 'false' and foreground.get(ANDROID+'foregroundServiceType') in ('microphone','0x00000080'), 'Hands-free service must be private and microphone-typed')
                tile = services.get('com.heysig.app.voice.HandsFreeTile')
                require(tile is not None and tile.get(ANDROID+'permission') == 'android.permission.BIND_QUICK_SETTINGS_TILE', 'Quick Settings tile must be system-permission protected')
            else:
                require(not native, 'Watch unexpectedly contains native dependencies')
            dex = b''.join(archive.read(name) for name in names if name.endswith('.dex'))
            require(b'WatchBridgeSetup' not in dex and b'ProtoActivity' not in dex, 'Development classes packaged')
            require(b'heysig-watch-loopback' not in dex and b'pairing.json' not in dex, 'Development relay packaged')
            require(b'heysig_phone_v1' in dex, f'{module}: production companion capability missing')
        output_manifest = bundle.parent / 'validated-manifest.xml'
        output_manifest.write_bytes(manifest_bytes)
        reports.append(f'{module}: PASS — {application}, target {target}, upload signature verified, development transport excluded; native library/ABI checks passed\nSHA-256: {hashlib.sha256(bundle.read_bytes()).hexdigest()}')
    text = '\n\n'.join(reports)+'\n\nValidation candidates only: physical hands-free/watch acceptance, public privacy policy, health declarations and Play review remain pending.\n'
    print(text)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text)


if __name__ == '__main__':
    main()
