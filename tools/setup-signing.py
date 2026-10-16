#!/usr/bin/env python3
"""Preserve existing development signers; optionally create a local upload key.

No key is registered with Google or uploaded. Back up private/signing securely.
Passwords are never printed or passed as process command-line arguments.
"""
import argparse
import os
from pathlib import Path
import secrets
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def keytool(destination, alias, password, label, size):
    environment = dict(os.environ, HEYSIG_KEY_PASSWORD=password)
    subprocess.run([
        'keytool', '-genkeypair', '-keystore', str(destination), '-storetype', 'PKCS12',
        '-storepass:env', 'HEYSIG_KEY_PASSWORD', '-keypass:env', 'HEYSIG_KEY_PASSWORD',
        '-alias', alias, '-keyalg', 'RSA', '-keysize', str(size), '-validity', '10000',
        '-dname', f'CN={label}',
    ], env=environment, check=True, capture_output=True)
    destination.chmod(0o600)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--create-upload-key', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    directory = ROOT / 'private/signing'
    directory.mkdir(parents=True, exist_ok=True)
    directory.chmod(0o700)
    for module in ('androidApp', 'wearApp'):
        destination = directory / f'{module}-debug.keystore'
        if destination.exists():
            continue
        previous = ROOT / module / 'build/debug.keystore'
        if previous.is_file():
            shutil.copyfile(previous, destination)
            destination.chmod(0o600)
            print(f'Preserved {module} development signer outside build outputs.')
        else:
            keytool(destination, 'androiddebugkey', 'android', f'HeySig {module} Development', 2048)
            print(f'Created {module} development signer for a fresh checkout.')
    if args.create_upload_key:
        properties = directory / 'upload.properties'
        destination = directory / 'heysig-upload.keystore'
        if properties.exists() and destination.exists():
            print('Existing local upload signing retained.')
        elif properties.exists() or destination.exists():
            raise SystemExit('Incomplete upload-signing setup; review private/signing without replacing existing material.')
        else:
            password = secrets.token_urlsafe(48)
            keytool(destination, 'heysig-upload', password, 'HeySig Upload', 4096)
            properties.write_text(
                'storeFile=private/signing/heysig-upload.keystore\n'
                f'storePassword={password}\nkeyAlias=heysig-upload\nkeyPassword={password}\n'
            )
            properties.chmod(0o600)
            print('Created local upload signing. Back up private/signing securely; no Play enrollment performed.')


if __name__ == '__main__':
    main()
