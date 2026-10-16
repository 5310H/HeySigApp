#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/setup-signing.py
./gradlew --no-daemon :wearApp:assembleDebug
cp wearApp/build/outputs/apk/debug/wearApp-debug.apk wearApp/build/heysig-watch.apk
printf '%s\n' 'Built wearApp/build/heysig-watch.apk (Gradle development build; not installed)'
