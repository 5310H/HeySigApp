#!/usr/bin/env python3
"""Explicit no-aid device tests. Microphone access requires --microphone opt-in.

No raw audio/transcripts are retained. Existing preferences are fingerprinted
in memory before installation and after testing; no settings contents are printed.
"""
import argparse
import hashlib
import io
from pathlib import Path
import subprocess
import tarfile
import threading

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.heysig.batteryprototype'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', required=True)
    parser.add_argument('--module', choices=('androidApp', 'wearApp'), required=True)
    parser.add_argument('--microphone', action='store_true', help='Explicit consent for disclosed foreground-only microphone tests')
    parser.add_argument('--keep-awake', action='store_true', help='Bounded WAKEUP key events during diagnostics; does not unlock or change screen-timeout settings')
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()

    def adb(*command, timeout=180):
        return subprocess.run(['adb', '-s', args.device, *command], capture_output=True, timeout=timeout)

    def fingerprint():
        result = adb('exec-out', 'run-as', PACKAGE, 'tar', '-cf', '-', 'shared_prefs')
        if result.returncode:
            raise RuntimeError('Cannot fingerprint existing settings on this device')
        with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
            return {member.name: hashlib.sha256(archive.extractfile(member).read()).hexdigest()
                    for member in archive.getmembers() if member.isfile()}

    before = fingerprint()
    paths = [ROOT / args.module / f'build/outputs/apk/debug/{args.module}-debug.apk',
             ROOT / args.module / f'build/outputs/apk/androidTest/debug/{args.module}-debug-androidTest.apk']
    for apk in paths:
        result = adb('install', '-r', str(apk))
        if result.returncode or b'Success' not in result.stdout:
            raise RuntimeError('In-place APK update failed; existing app was not uninstalled')
    completed = False
    wake_stop = threading.Event()
    wake_counts = [0, 0]
    def keep_awake():
        while not wake_stop.is_set():
            try:
                wake = adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP', timeout=8)
                wake_counts[0 if wake.returncode == 0 else 1] += 1
            except subprocess.TimeoutExpired:
                wake_counts[1] += 1
            if wake_stop.wait(4):
                break
    waker = threading.Thread(target=keep_awake, daemon=True) if args.keep_awake else None
    if waker:
        waker.start()
    try:
        result = adb('shell', 'am', 'instrument', '-w', '-e', 'microphone', str(args.microphone).lower(),
                     PACKAGE + '.test/com.heysig.audio.NoiseAcceptance', timeout=420)
        text = (result.stdout + result.stderr).decode(errors='replace')
        completed = result.returncode == 0 and 'result=PASS:' in text and 'INSTRUMENTATION_CODE: -1' in text
    except subprocess.TimeoutExpired:
        text = 'FAIL: device test connection timed out; runtime result unconfirmed\n'
    finally:
        wake_stop.set()
        if waker:
            waker.join(timeout=10)
    if waker:
        text += f'\nBounded display wake events: {wake_counts[0]}; failed: {wake_counts[1]}; no unlock request or screen-timeout change\n'
    try:
        after = fingerprint()
        preserved = all(after.get(name) == digest for name, digest in before.items())
    except (RuntimeError, subprocess.TimeoutExpired, tarfile.TarError):
        preserved = None
        text += '\nPreference verification unavailable after testing; preservation unconfirmed\n'
    text += f'\nExisting preference files preserved: {preserved}; checked {len(before)} files\n'
    text += f'Target APK SHA-256: {hashlib.sha256(paths[0].read_bytes()).hexdigest()}\n'
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(text)
    print(text)
    return 0 if completed and preserved is True else 1


if __name__ == '__main__':
    raise SystemExit(main())
