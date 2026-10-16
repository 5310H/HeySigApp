#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/setup-signing.py
./gradlew --no-daemon :androidApp:assembleDebug
cp androidApp/build/outputs/apk/debug/androidApp-debug.apk androidApp/build/heysig-proto.apk
if [ "${HEYSIG_AUDIO_SMOKE:-0}" = "1" ]; then
    ./gradlew --no-daemon :androidApp:assembleDebugAndroidTest
fi
printf '%s\n' 'Built androidApp/build/heysig-proto.apk (Gradle development build; not installed)'
