#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Build the head reporter first, as documented; reuse its platform-independent noise types.
kotlinc -classpath shared/build/scene-head-report.jar \
  tools/environment/SceneConsensusReport.kt tools/environment/SceneConsensusCheck.kt \
  -include-runtime -d shared/build/scene-consensus-check.jar
java -cp shared/build/scene-consensus-check.jar:shared/build/scene-head-report.jar com.heysig.tools.SceneConsensusCheckKt
