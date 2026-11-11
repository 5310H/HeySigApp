#!/usr/bin/env python3
"""Re-sign a successful CI artifact locally; never compile, install or use release keys."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
APKS = [f'{module}/build/outputs/apk/{folder}/{module}-{suffix}.apk'
        for module in ('androidApp', 'wearApp')
        for folder, suffix in [('debug', 'debug'), ('androidTest/debug', 'debug-androidTest')]]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True, help='Extracted successful workflow artifact')
    parser.add_argument('--commit', required=True, help='Expected full 40-character pushed commit')
    parser.add_argument('--output', type=Path, required=True, help='New output directory')
    parser.add_argument('--apksigner', required=True, help='Path to Android SDK apksigner')
    args = parser.parse_args()
    if not re.fullmatch(r'[0-9a-f]{40}', args.commit):
        parser.error('--commit must be the full source revision')
    source = (args.artifact / 'ci-results/source.txt').read_text()
    if not source.startswith(f'commit {args.commit}\n'):
        parser.error('Artifact source differs from expected commit')
    checksums = {}
    for line in (args.artifact / 'ci-results/apk-sha256.txt').read_text().splitlines():
        digest, path = line.split(maxsplit=1)
        checksums[path] = digest
    for path in APKS:
        actual = hashlib.sha256((args.artifact / path).read_bytes()).hexdigest()
        if checksums.get(path) != actual:
            parser.error(f'Missing or mismatched original APK checksum: {path}')
    key = ROOT / 'private/signing/androidApp-debug.keystore'
    if not key.is_file():
        parser.error('Existing development key is missing; do not create a replacement')
    cert = subprocess.check_output(['keytool', '-exportcert', '-keystore', str(key),
                                    '-storepass', 'android', '-alias', 'androiddebugkey'], stderr=subprocess.DEVNULL)
    expected = hashlib.sha256(cert).hexdigest()
    # A new directory prevents overwriting an earlier candidate or original evidence.
    args.output.mkdir(parents=True, exist_ok=False)
    records = []
    for path in APKS:
        target = args.output / Path(path).name
        subprocess.run([args.apksigner, 'sign', '--ks', str(key), '--ks-pass', 'pass:android',
                        '--ks-key-alias', 'androiddebugkey', '--key-pass', 'pass:android',
                        '--out', str(target), str(args.artifact / path)], check=True)
        report = subprocess.check_output([args.apksigner, 'verify', '--verbose', '--print-certs', str(target)], text=True)
        signers = re.findall(r'^Signer #\d+ certificate SHA-256 digest: (\w+)$', report, re.MULTILINE)
        if signers != [expected]:
            raise RuntimeError(f'Unexpected signer: {target}')
        records.append(f'{hashlib.sha256(target.read_bytes()).hexdigest()}  {target.name}')
    (args.output / 'apk-sha256.txt').write_text('\n'.join(records) + '\n')
    shutil.copyfile(args.artifact / 'ci-results/source.txt', args.output / 'source.txt')
    print(f'Four development APKs signed and verified for {args.commit}. Certificate SHA-256: {expected}')
    print('No device installation performed. Preserve settings evidence before an in-place update.')


if __name__ == '__main__':
    main()
