#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p shared/build
kotlinc shared/src/commonMain/kotlin/com/heysig/noise/NoiseMeasurement.kt \
  shared/src/commonTest/kotlin/com/heysig/noise/NoiseMeasurementCheck.kt \
  -include-runtime -d shared/build/noise-check.jar
java -jar shared/build/noise-check.jar
