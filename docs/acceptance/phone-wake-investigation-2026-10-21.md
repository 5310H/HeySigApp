# Phone wake-word investigation — October 21, 2026

Device: Pixel 9a, existing `com.heysig.batteryprototype` development app.

The user confirmed tap-to-speak and later shake-to-speak work. Three shakes
produce a vibration and the command-listening indicator. The user reports that
saying Hey Sig does not produce a response. Wake-word acceptance has not passed.

Initially no hands-free service was running, although shake and the session
disclosure were saved as enabled. Starting the session through the app created
the microphone foreground service and displayed Listening for Hey Sig.
Android reported active, non-silenced VOICE_RECOGNITION capture and an active
ShakeTrigger accelerometer registration. Microphone and notification runtime
permissions were already granted. Later inspection confirmed both voicePrompts
and spokenResponses were enabled. No hearing-aid command was issued by this
investigation.

## Candidate adjustment

WakeModel previously overrode the keyword probability threshold to 0.5.
The candidate uses 0.25, the default in the pinned sherpa-onnx 1.13.8
[Kotlin configuration](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/kotlin-api/KeywordSpotter.kt).
This is a sensitivity adjustment, not proof of the root cause or a verified fix.
Model files, phrase phonemes, recognition provider and command validation are
unchanged. No recording or transcript history is introduced.

After build and compatible installation, separately verify Hey Sig activation,
read-only command completion, recovery, locked-phone behavior and unwanted
activation during ordinary speech. More sensitive detection can also increase
unwanted activation. Do not use aid-changing commands for this initial test.
Watch wake behavior is not established by these phone results.

## Build and installation

Pinned Gradle shared checks, phone/watch debug APKs and the phone instrumentation
APK build passed using the restored SDK and documented ARM aapt2 override.
The development APK class-definition check passed. The phone debug APK installed
successfully with `adb install -r`; no uninstall or data clearing occurred.
An aggregate fingerprint of all shared preferences matched before and after
installation, before reopening the app. Instrumentation was built but not run
for this candidate.

## User acceptance after installation

After starting a fresh session, the user confirmed Hey Sig produced the requested
readiness response (the question allowed either Ready or vibration, so the exact
cue was not independently identified). The user then separately confirmed that
saying current program after Ready produced the spoken program reply. This
establishes one successful user-reported wake-to-command/reply sequence on this
phone with the adjusted threshold. Earlier shake-to-command acceptance was
reported before this update; it has not been repeated on the candidate.

Locked/pocket reception, repeated wake accuracy, accidental activations and
physical watch testing remain pending. No aid-setting change was requested as
part of this acceptance.
