# Six-level Observe and voice switches — 2026-10-16

## Automated checks

- `bash tools/check-noise.sh`: passed relative PCM measurements and six-level
  validation, equality/hysteresis edges, repeated/unused targets, jumps, stale
  gaps and noise voice edits. Observe has no playback dependency.
- `bash tools/check-manual.sh`: passed 410 existing voice phrase cases and
  fitting/context/range/restore checks, plus prompt/response on/off vocabulary.
- `python3 tools/watch-relay/check_relay.py`: six tests passed, including noise
  editor/Observe allowlisting while automatic noise and aid controls are blocked
  in status-only mode.
- Android 0.8 and Wear 0.2 APK builds/signature verification passed. Installed
  development updates preserve app data. They are not Play release artifacts.
- `git diff --check` and Python syntax checks passed.

## Device observations

- Installed Android 0.8 on Chromebook and Wear 0.2 on Pixel Watch 3.
- Chromebook launch/navigation tree includes Home, Setup, Saved, Voice and Noise.
- Opened Noise and Sample now on the final build. Verified the microphone-use
  explanation and separate Not now/Continue actions appear before runtime
  permission or capture. Dismissed with Not now; no microphone access was granted
  or capture started during this UI check.
- Watch screen exposes independent Voice prompts and Spoken responses switches.
  Turning the prompt switch off saved `voicePrompts=false` separately from
  responses. Restored prompts to enabled after the check. Full audible on/off
  matrix remains pending user acceptance.
- Restarted the development relay with noise editor/Observe support, still in
  status-only mode. No aid command was sent for these installation/UI checks.

## Not yet accepted

Microphone capture and illustrative thresholds still require device
measurement/acceptance. Observe proposals, permission denial/revocation,
audio contention, process-death/draft recovery, round-watch accessibility and
full phone/watch spoken edits need hardware acceptance. Automatic noise
switching and guided calibration are not implemented.

The Play preflight intentionally exits with blockers: phone target SDK,
development receiver, watch debug configuration and missing Gradle/AAB release
pipeline. See [release audit](../play-store-readiness.md). This evidence does
not establish Google Play eligibility or medical-device classification.
