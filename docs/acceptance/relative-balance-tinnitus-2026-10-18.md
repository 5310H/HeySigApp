# Relative Balance and tinnitus — October 18, 2026

Implemented one-step Balance and tinnitus up/down, including tin, sharpness and
sound sharpness aliases and bounded turn/increase/decrease/raise/lower forms.
Configured program names retain precedence. Negation, compound commands and
multi-step amounts reject without playback.

Shared LevelSteps resolves a target from the current program's known requested
baseline at dispatch. Balance uses -8–7 and tinnitus 0–15. Missing or invalid
baselines, range endpoints, invalid programs and unknown/unsupported tinnitus
capability reject. No aid-state readback or physical reception is inferred.
Phone, forwarded watch voice and hands-free dispatch use the existing validated
executor. No saved restoration snapshot is replaced by a relative command.
The existing VolumeSteps entry point delegates to the same planner.

Build source: working tree based on commit 43658e1; development host, no device/OS acceptance.

Validation by Codex on October 18: tools/check-manual.sh passed on the development
host. Added exhaustive two-direction checks across both ranges, invalid contexts,
step sizes, parser ambiguity and configured-name precedence. Extended isolated
SavedSettingsAcceptance fake-transport checks to cover both directions and saved
snapshot preservation; device execution remains pending. No user fitting/settings
were modified and no physical-aid command was sent.

Full Gradle shared tests, phone/Wear development APKs and instrumentation APK
passed in one offline Gradle invocation using the existing ARM cache. The phone
APK also passed tools/check-development-apk.py (required DEX classes present). Physical speech recognition, aid reception and watch-device
acceptance remain pending; these checks are not Play approval.

Development APK SHA-256:

- `androidApp/build/outputs/apk/debug/androidApp-debug.apk`: `b21006acd500a3fb888aff88a98122ea1645e26a315ff8e0bbc4d31aed02d65b`
- `wearApp/build/outputs/apk/debug/wearApp-debug.apk`: `e6492235ad7d933f41a02b3f82528931a17001a88e4ffb819f9c1d62f30d0b6d`
