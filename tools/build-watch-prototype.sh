#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${HEYSIG_ANDROID_JAR:?Set HEYSIG_ANDROID_JAR to Android API 35 android.jar}"
: "${HEYSIG_ANDROID_RESOURCES_JAR:?Set HEYSIG_ANDROID_RESOURCES_JAR to API 29 android.jar}"
: "${HEYSIG_D8_JAR:?Set HEYSIG_D8_JAR to the Google R8/D8 jar}"
mkdir -p wearApp/build/dex
kotlinc shared/src/commonMain/kotlin/com/heysig/control/VoicePreference.kt wearApp/src/main/kotlin/com/heysig/watch/*.kt -classpath "$HEYSIG_ANDROID_JAR" -jvm-target 1.8 -include-runtime -d wearApp/build/watch.jar
java -cp "$HEYSIG_D8_JAR" com.android.tools.r8.D8 --min-api 30 --lib "$HEYSIG_ANDROID_JAR" --output wearApp/build/dex wearApp/build/watch.jar
aapt package -f -M wearApp/src/main/AndroidManifest.xml -I "$HEYSIG_ANDROID_RESOURCES_JAR" -F wearApp/build/unsigned.apk
python3 - <<'PY'
from pathlib import Path
import zipfile
with zipfile.ZipFile('wearApp/build/unsigned.apk','a') as archive:
    for path in sorted(Path('wearApp/build/dex').glob('classes*.dex')):
        archive.write(path,path.name)
PY
if [ ! -f wearApp/build/debug.keystore ]; then
    keytool -genkeypair -keystore wearApp/build/debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=HeySig Watch, O=Development, C=US'
fi
zipalign -f 4 wearApp/build/unsigned.apk wearApp/build/aligned.apk
apksigner sign --ks wearApp/build/debug.keystore --ks-pass pass:android --key-pass pass:android --out wearApp/build/heysig-watch.apk wearApp/build/aligned.apk
apksigner verify wearApp/build/heysig-watch.apk
printf '%s\n' 'Built wearApp/build/heysig-watch.apk (development build; not installed)'
