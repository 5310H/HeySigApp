#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p shared/build
kotlinc shared/src/commonMain/kotlin/com/heysig/noise/*.kt \
  tools/environment/SceneHeadReport.kt -include-runtime -d shared/build/scene-head-report.jar
java -cp shared/build/scene-head-report.jar com.heysig.tools.SceneHeadReportKt "$@"
