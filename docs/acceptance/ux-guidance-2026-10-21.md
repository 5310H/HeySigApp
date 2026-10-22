# Guided UX validation — October 21, 2026

Scope: phone first-use checklist, guided Noise routes and Sleep dashboard/review.
Existing fitting, snapshot, phrase and reminder/noise storage formats are retained.
No automatic switching gate, permission, transport or signing identity is changed.

## Build checks

Passed with the pinned wrapper and the documented host-local ARM aapt2 override:

```sh
GRADLE_USER_HOME=/tmp/heysig-gradle-cache ./gradlew --no-daemon \
  -Pandroid.aapt2FromMavenOverride=/tmp/heysig-arm-tools/build-tools/aapt2 \
  :shared:test :androidApp:assembleDebug :wearApp:assembleDebug \
  :androidApp:assembleDebugAndroidTest
```

Shared tests were up-to-date. Both debug APKs and the updated instrumentation
APK built successfully. The first sandbox attempt could not initialize Gradle
networking; an unrestricted attempt used an incompatible x86 aapt2 on this ARM
host. The documented ARM override resolved that build failure. No SDK licenses
were accepted or build tooling added by this change.

## Device validation

Device: connected Chromebook Android instance `emulator-5554` (staryu_cheets).
The existing no-audio/no-microphone Api36Smoke runner is used with updated route
labels and a checklist visit. It checks the shared watch layout at 192dp round
sizes and font scales 1 and 2 in isolated rendering contexts. This is not a
physical watch test. Sleep/Noise execution and storage checks use isolated
preferences and fake transport. Read-only settings fingerprints are taken before
and after the compatible development APK update.

## Limits

No hearing-aid command, microphone sample or actual reminder is triggered by
these UI checks. They do not establish wake accuracy, overnight reminder
reliability, hearing-aid reception, user task success rates or Play approval.
Real phone/Wear device acceptance and the plan's external usability sessions
remain outstanding. Automatic Noise switching remains disabled; Sleep remains
a reminder followed by a user-confirmed switch.
