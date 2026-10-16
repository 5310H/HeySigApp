#!/usr/bin/env python3
"""Run the no-audio/no-microphone smoke runner on one explicitly chosen device."""
import argparse
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', required=True, help='Exact authorized ADB serial/IP:port')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    for apk in ('androidApp/build/outputs/apk/debug/androidApp-debug.apk',
                'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk'):
        completed = subprocess.run(['adb','-s',args.device,'install','-r',str(ROOT/apk)],capture_output=True,text=True)
        if completed.returncode or 'Success' not in completed.stdout:
            raise SystemExit('Development/test APK install failed; existing app was not uninstalled.')
    completed = subprocess.run(['adb','-s',args.device,'shell','am','instrument','-w',
        'com.heysig.batteryprototype.test/com.heysig.app.Api36Smoke'],capture_output=True,text=True)
    output = completed.stdout+completed.stderr
    print(output)
    if args.report:
        args.report.parent.mkdir(parents=True,exist_ok=True)
        args.report.write_text(output)
    # am instrument can exit zero even when its runner reports a failure.
    return 0 if completed.returncode == 0 and 'result=PASS:' in output and 'INSTRUMENTATION_CODE: -1' in output else 1


if __name__ == '__main__':
    raise SystemExit(main())
