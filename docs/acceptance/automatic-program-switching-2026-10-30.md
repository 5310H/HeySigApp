# Automatic program switching development checks — October 30, 2026

This is source/build evidence on the ARM development host. It is not Play,
medical-device, classifier-accuracy, Pixel Watch 3, battery or aid acceptance.
No app installation, microphone access, production upload or aid command was
performed during these checks. Existing user fitting/settings and prior
acceptance records were preserved.

## Checks

| Check | Evidence |
|---|---|
| Shared regressions | `:shared:test`: 30 tests in 8 suites, zero failures/errors/skips. Includes existing manual/voice/sleep checks and new environment, codec, watch-session, capture-access policy, shake sensitivity and monitoring grant tests. |
| Standalone noise checks | `bash tools/check-noise.sh`: all legacy measurement/calibration checks and new reducer/recording checks pass. |
| Existing manual/sleep scripts | `tools/check-manual.sh` and `tools/check-sleep.sh` passed; manual grammar includes 433 existing voice phrase cases. |
| Prerecorded model runner | Pinned quantized YAMNet signature `[15600] → [1,521]`; 24 WAV duration/gain cases produced finite scores. Outputs and hashes are retained as scalar regression evidence. |
| Phone APK | `:androidApp:assembleDebug` rebuilt successfully, including final Sleep-priority and capture-access completion fixes. |
| Wear APK | `:wearApp:assembleDebug` rebuilt with the Monitoring Activity voice return and capture-access completion fixes. |
| Instrumentation | `:androidApp:assembleDebugAndroidTest` compiled, including isolated storage/native-model checks; not run on a device. |
| Phone lint | `:androidApp:lintDebug` passed again after the final Sleep-priority and capture-access completion fixes; zero errors/fatal issues and 21 warnings. |
| Wear lint | First offline attempt lacked two AndroidX dependencies; targeted online rerun passed. Final offline `:wearApp:lintDebug` also passed with zero errors/fatal issues and 14 warnings. |
| Packaged native/model integrity | `tools/validate-native-apks.py --variants debug` passed: 20 phone and 4 watch native libraries match pinned hashes, with ELF/ZIP alignment at least 16 KB. Both model/label asset hashes match. |
| Required phone classes | `tools/check-development-apk.py` passed, including EnvironmentFeature, ForegroundVoice and Yamnet. |
| Signing and identity | Both development APK signatures verify and share the same certificate. Both badging manifests retain `com.heysig.batteryprototype`, phone version 15/target 36/min 29 and Wear version 12/target 35/min 30. Release identities/signing configuration were not edited. |
| Source/Python hygiene | `git diff --check` and Python compilation of the updated validation tools passed. |

Builds use pinned Gradle tooling and the documented machine-local ARM
`android.aapt2FromMavenOverride=/tmp/heysig-arm-tools/build-tools/aapt2`. The
default Google Maven aapt2 is x86 and cannot execute on this host. The SDK also
reports absent/unaccepted platform-tools; these builds did not need or install
that package. Earlier release APKs/AABs are stale and were not validated as
artifacts of this change.

After the interrupted session, the temporary SDK/tool directories were absent.
They were restored from Google repository-checksummed platform 35/36 and build
tools 35 archives and the previously documented SHA-256-pinned ARM tool archive.
The combined final offline Gradle run passed all shared tests, both debug APK
builds, instrumentation compilation and both debug lint tasks. Both rebuilt APKs
then passed native/model/alignment and signature checks; their development
certificates match. The phone DEX inventory check also passed.

The resumed review fixed an audio-worker completion gap: losing capture access
immediately before or after inference now reports failure rather than leaving
the adapter waiting indefinitely for a sample. Explicit cancellation still
discards results. This change compiles for both apps; the interruption race and
restart behavior still need device testing, as specified in the feature guide.

## Accuracy findings and remaining work

The four development WAVs are MS-SNSD Restaurant_1, Babble_6, Traffic_1 and
VacuumCleaner_1. They are external development downloads, not packaged audio;
see [provenance/licensing and reproduction](../../shared/src/commonTest/fixtures/environment/README.md).
Each first 3/5/10-second window was tested at gains 1.0 and 0.25. No calibrated
SPL is inferred. At the environment-mapping threshold, eight cases abstain and
all six restaurant cases incorrectly map to generic Conversation. Babble also
depends strongly on gain. Vacuum cases map to non-speech noise; short traffic
samples are often uncertain. This small, non-independent sample is inadequate
to claim six-environment accuracy or adequate sampling cadence.

`EnvironmentReleaseGate.CLASSIFIER_ACCEPTANCE_PASSED` and the legacy physical
acceptance gate both remain false. Enabling Automatic arms decisions without
requiring calibration; it does not bypass either gate. Explicit simulation is
labelled, uses isolated cooldown state and never transmits to the aids. Legacy
calibration and the existing explicit development trial remain unchanged.

Still required: representative held-out quiet/conversation/small/large-group and
crowd recordings; reliable overlap/context classification; Android/Wear model
timing/memory/CPU and battery measurements; physical pairing/disconnect/timeout,
permission/privacy-switch and audio-interruption testing; round-watch/large-font
and accessibility acceptance; physical program reception and no-replay testing.
New instrumentation tests compile but do not constitute these device results.
Release needs current official policy rechecks, signed artifacts and the Play
test track plus all other [existing readiness gates](../play-store-readiness.md).
