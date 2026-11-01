#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
kotlinc -classpath shared/build/scene-head-report.jar \
  tools/environment/SceneSpecialistReport.kt tools/environment/SceneSpecialistCheck.kt \
  -include-runtime -d shared/build/scene-specialist-check.jar
java -cp shared/build/scene-specialist-check.jar:shared/build/scene-head-report.jar com.heysig.tools.SceneSpecialistCheckKt
