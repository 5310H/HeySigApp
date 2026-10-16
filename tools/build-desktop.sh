#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p desktopApp/build
kotlinc research/acoustic/kotlin/ProtoRequest.kt desktopApp/src/main/kotlin/com/heysig/desktop/ProtoWindow.kt -include-runtime -d desktopApp/build/heysig-proto.jar
jar --update --file desktopApp/build/heysig-proto.jar --main-class com.heysig.desktop.ProtoWindowKt
java -jar desktopApp/build/heysig-proto.jar --check
