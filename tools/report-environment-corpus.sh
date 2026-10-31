#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p shared/build
kotlinc shared/src/commonMain/kotlin/com/heysig/noise/*.kt \
  tools/environment/EnvironmentCorpusReport.kt \
  -include-runtime -d shared/build/environment-corpus-report.jar
java -cp shared/build/environment-corpus-report.jar com.heysig.tools.EnvironmentCorpusReportKt "$@"
