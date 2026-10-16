#!/usr/bin/env python3
"""Built-manifest Play preflight; does not replace the remaining release gates.

This does not validate an AAB, a privacy policy, health classification, or Play
approval. Recheck the dated policy floors before preparing each submission.
"""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
POLICY_CHECKED = '2026-10-16'


def inspect_manifest(path, label, minimum_target):
    blockers = []
    try:
        manifest = ET.parse(path).getroot()
        sdk = manifest.find('uses-sdk')
        target = int(sdk.get(ANDROID + 'targetSdkVersion', '0')) if sdk is not None else 0
        if target < minimum_target:
            blockers.append(f'{label}: target SDK {target}; current new-submission floor is {minimum_target}')
        app = manifest.find('application')
        if app is None:
            blockers.append(f'{label}: application element missing')
            return blockers
        for flag in ('debuggable', 'testOnly', 'usesCleartextTraffic'):
            value = app.get(ANDROID + flag, 'false')
            if value != 'false':
                blockers.append(f'{label}: {flag}={value}; release configuration needs review')
        for component in app:
            name = component.get(ANDROID + 'name', '')
            if 'WatchBridgeSetup' in name or 'PlaybackSmoke' in name:
                blockers.append(f'{label}: development component present: {name}')
        for instrumentation in manifest.findall('instrumentation'):
            blockers.append(f'{label}: instrumentation present: {instrumentation.get(ANDROID + "name", "unknown")}')
    except (OSError, ET.ParseError, ValueError) as error:
        blockers.append(f'{label}: cannot inspect plain XML manifest: {error}')
    return blockers


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phone-manifest', type=Path, default=ROOT / 'androidApp/build/outputs/bundle/release/validated-manifest.xml')
    parser.add_argument('--watch-manifest', type=Path, default=ROOT / 'wearApp/build/outputs/bundle/release/validated-manifest.xml')
    args = parser.parse_args()
    blockers = inspect_manifest(args.phone_manifest, 'Phone', 36)
    blockers += inspect_manifest(args.watch_manifest, 'Wear', 35)
    for label, options in (
        ('Gradle settings', ('settings.gradle', 'settings.gradle.kts')),
        ('Gradle root build', ('build.gradle', 'build.gradle.kts')),
        ('Gradle wrapper', ('gradlew',)),
        ('Gradle wrapper properties', ('gradle/wrapper/gradle-wrapper.properties',)),
    ):
        if not any((ROOT / option).is_file() for option in options):
            blockers.append(f'Build: {label} missing; release AAB pipeline not established')
    print(f'Google Play built-manifest preflight — policy floors checked {POLICY_CHECKED}')
    for blocker in blockers:
        print(f'BLOCKED: {blocker}')
    if not blockers:
        print('PASS: inspected release manifests and Gradle scaffold checks; remaining manual gates still apply.')
    print('MANUAL: signed release AAB validation, production/no-ADB transport, privacy/Data safety,')
    print('health/medical assessment, accessibility/device tests and Play Console requirements.')
    print('Details: docs/play-store-readiness.md. This is not a Play approval check.')
    if not args.phone_manifest.is_file() or not args.watch_manifest.is_file():
        print('Build release candidates and run tools/validate-release-bundles.py first, or supply plain XML release manifests explicitly.')
    return 1 if blockers else 0


if __name__ == '__main__':
    raise SystemExit(main())
