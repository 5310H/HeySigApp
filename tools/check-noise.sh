#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p shared/build
kotlinc shared/src/commonMain/kotlin/com/heysig/noise/*.kt \
  shared/src/commonTest/kotlin/com/heysig/noise/*.kt \
  audioAndroid/src/main/kotlin/com/heysig/audio/EnvironmentSampleWorker.kt \
  audioAndroid/src/test/kotlin/com/heysig/audio/EnvironmentWorkerCheck.kt \
  -include-runtime -d shared/build/noise-check.jar
java -cp shared/build/noise-check.jar com.heysig.noise.NoiseMeasurementCheckKt
java -cp shared/build/noise-check.jar com.heysig.noise.NoiseObserveCheckKt
java -cp shared/build/noise-check.jar com.heysig.noise.SoundTypeCheckKt

java -cp shared/build/noise-check.jar com.heysig.noise.NoiseAutomationCheckKt
java -cp shared/build/noise-check.jar com.heysig.noise.NoiseThresholdLearningCheckKt
java -cp shared/build/noise-check.jar com.heysig.noise.EnvironmentSwitchingCheckKt
java -cp shared/build/noise-check.jar com.heysig.noise.EnvironmentRecordingCheckKt
java -cp shared/build/noise-check.jar com.heysig.noise.EnvironmentSignalCheckKt
java -cp shared/build/noise-check.jar com.heysig.noise.EnvironmentSceneHeadCheckKt
java -cp shared/build/noise-check.jar com.heysig.audio.EnvironmentWorkerCheckKt
