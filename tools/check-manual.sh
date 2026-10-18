#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p shared/build
kotlinc shared/src/commonMain/kotlin/com/heysig/control/*.kt \
  shared/src/commonTest/kotlin/com/heysig/control/PhraseImportCheck.kt \
  shared/src/commonTest/kotlin/com/heysig/control/FittingWorkflowCheck.kt \
  shared/src/commonTest/kotlin/com/heysig/control/ManualControlCheck.kt \
  -include-runtime -d shared/build/manual-check.jar
java -jar shared/build/manual-check.jar
