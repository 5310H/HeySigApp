#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p shared/build
kotlinc shared/src/commonMain/kotlin/com/heysig/control/*.kt \
  shared/src/commonMain/kotlin/com/heysig/sleep/*.kt \
  shared/src/commonTest/kotlin/com/heysig/sleep/SleepRoutineCheck.kt \
  -include-runtime -d shared/build/sleep-check.jar
java -jar shared/build/sleep-check.jar
