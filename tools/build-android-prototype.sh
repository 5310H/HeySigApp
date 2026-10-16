#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${HEYSIG_ANDROID_JAR:?Set HEYSIG_ANDROID_JAR to Android API 35 android.jar}"
: "${HEYSIG_ANDROID_RESOURCES_JAR:?Set HEYSIG_ANDROID_RESOURCES_JAR to API 29 android.jar for the installed ARM aapt}"
: "${HEYSIG_D8_JAR:?Set HEYSIG_D8_JAR to the Google R8/D8 jar}"
mkdir -p androidApp/build/dex
manifest_path=androidApp/src/main/AndroidManifest.xml
extra_sources=()
if [ "${HEYSIG_AUDIO_SMOKE:-0}" = "1" ]; then
    extra_sources+=(androidApp/src/androidTest/kotlin/com/heysig/app/PlaybackSmoke.kt)
    manifest_path=androidApp/build/smoke/AndroidManifest.xml
    python3 - <<'PYSMOKE'
from pathlib import Path
source = Path('androidApp/src/main/AndroidManifest.xml').read_text()
source = source.replace('</manifest>', '<instrumentation android:name="com.heysig.app.PlaybackSmoke" android:targetPackage="com.heysig.batteryprototype" /></manifest>')
Path('androidApp/build/smoke').mkdir(exist_ok=True)
Path('androidApp/build/smoke/AndroidManifest.xml').write_text(source)
PYSMOKE
fi
kotlinc research/acoustic/kotlin/ProtoRequest.kt shared/src/commonMain/kotlin/com/heysig/control/*.kt shared/src/commonMain/kotlin/com/heysig/noise/*.kt androidApp/src/main/kotlin/com/heysig/app/*.kt "${extra_sources[@]}" -classpath "$HEYSIG_ANDROID_JAR" -jvm-target 1.8 -include-runtime -d androidApp/build/prototype.jar
java -cp "$HEYSIG_D8_JAR" com.android.tools.r8.D8 --min-api 29 --lib "$HEYSIG_ANDROID_JAR" --output androidApp/build/dex androidApp/build/prototype.jar
aapt package -f -M "$manifest_path" -I "$HEYSIG_ANDROID_RESOURCES_JAR" -F androidApp/build/unsigned.apk
python3 - <<'PY'
from pathlib import Path
import zipfile
with zipfile.ZipFile('androidApp/build/unsigned.apk','a') as archive:
    for path in sorted(Path('androidApp/build/dex').glob('classes*.dex')):
        archive.write(path,path.name)
PY
if [ ! -f androidApp/build/debug.keystore ]; then
    keytool -genkeypair -keystore androidApp/build/debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=HeySig Prototype, O=Development, C=US'
fi
zipalign -f 4 androidApp/build/unsigned.apk androidApp/build/aligned.apk
apksigner sign --ks androidApp/build/debug.keystore --ks-pass pass:android --key-pass pass:android --out androidApp/build/heysig-proto.apk androidApp/build/aligned.apk
apksigner verify androidApp/build/heysig-proto.apk
printf '%s\n' 'Built androidApp/build/heysig-proto.apk (local debug prototype; not installed)'
