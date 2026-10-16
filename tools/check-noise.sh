#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p shared/build
kotlinc shared/src/commonMain/kotlin/com/heysig/noise/*.kt \
  shared/src/commonTest/kotlin/com/heysig/noise/*.kt \
  -include-runtime -d shared/build/noise-check.jar
java -cp shared/build/noise-check.jar com.heysig.noise.NoiseMeasurementCheckKt
java -cp shared/build/noise-check.jar com.heysig.noise.NoiseObserveCheckKt
