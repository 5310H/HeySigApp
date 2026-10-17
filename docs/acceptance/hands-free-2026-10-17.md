# Hands-free and shake activation — October 17, 2026

The owner requested hands-free On/Off, watch controls, locked-phone operation
and shake activation on both devices. Implementation uses an explicit,
non-sticky, eight-hour phone microphone foreground session. Local Hey Sig
keyword detection hands off to one bounded command recognition request.
Prompts and responses remain independently optional. Off, Pause and End have
distinct visible controls. Watch/tile controls require an already-established
session. Nothing starts listening after installation or reboot.

Phone and watch share the existing manual validation and one ManualRuntime
executor/journal/transport. Background authorization only bypasses the existing
screen-lock check during a live session. It preserves route, call, DND, volume,
cancellation, journal and uncertain-state behavior. No request is queued or
replayed. Six-level Automatic noise switching remains source-locked.

Separate default-off shake settings require three separated high-acceleration
peaks with a cooldown. Vibration acknowledges activation. Phone shake only
operates with listening On in a live session, including locked use. Watch shake
uses its existing Speak action while its activity is resumed. Sleeping-watch
shake activation is **not implemented**. No motion/audio/transcript history is
added. Provider command recognition/TTS may use a network as disclosed.

See the [background design](../architecture/hands-free-session.md),
[quick start](../hands-free-quick-start.md) and
[dependency provenance](../../third_party/sherpa-onnx/README.md).

## Verification

The final product APKs were installed compatibly on the Pixel 9a and Pixel
Watch 3; fitting/noise and watch pairing/speech preference digests were
unchanged at installation. Listening and shake were not enabled by installation.
See the [installation record](evidence/handsfree-device-installs-2026-10-17.txt).
The real watch runs Android 17/API 37. Its More hierarchy contains both new
entries; complete dialog interaction remains pending because the watch left the
app between automation steps. See the [partial watch UI record](evidence/handsfree-watch-ui-2026-10-17.txt).

The full final shared/debug/release APK, debug/release lint and signed-bundle
build passed in 26m 48s (255 tasks: 79 executed, 176 up-to-date). All ten shared
JUnit test groups passed, including five hands-free/shake tests. Lint errors are
zero in all four variants. Remaining warning counts: phone debug 36, phone
release 4, watch debug 10, watch release 1. These include research/test warnings,
SDK resource/attribute configuration, available dependency updates and one
English-string localization warning; this is not an all-warning-free claim.
No Java/Kotlin compiler deprecation warning appeared in the final build log.
The host's AAPT2 override remains experimental.

Both signed bundles passed signature, application ID, version-code, permission,
component, development-code exclusion and pinned native/model checks. Both
phone APKs pass exact native checksums, ELF load-segment alignment and stored
ZIP alignment at 16 KB. Actual 16 KB-device runtime acceptance is still pending.

- [Final build log](evidence/handsfree-build-2026-10-17.txt)
- [Bundle validation and hashes](evidence/handsfree-release-bundles-2026-10-17.txt)
- [Native APK alignment](evidence/handsfree-native-alignment-2026-10-17.txt)
- [Final normal-text check](evidence/handsfree-chromebook-final-2026-10-17.txt)
- [Final 2×-text check](evidence/handsfree-chromebook-large-font-final-2026-10-17.txt)
- [Corrected screenshot-runner build](evidence/handsfree-screenshot-runner-build-2026-10-17.txt)

The initial combined lint run exposed deprecated tile-call detection on the
pre-34 compatibility branch and a Kotlin indentation error. The tile branch
now has a scoped lint explanation and the conditional was reformatted.
An initial instrumentation attachment failed because UiAutomation was null;
a retry then exposed a fake failing-Editor test double that returned the real
Editor from putBoolean. The fake now preserves its failing commit and assertions
run on the instrumentation thread. Neither failure sent aid commands or changed
real fitting/noise preferences. Native-model silence decoding and isolated
controller checks subsequently passed at normal and 2× text size.

The first final 2×-text screenshot attempt hit an existing harness race: its
newly installed root still had zero width/height. Capture now waits up to five
seconds for layout, keeps bitmap errors on the instrumentation thread and
recycles bitmaps in finally. The device font scale and real preferences were
restored/preserved; see the [failed-attempt record](evidence/handsfree-large-font-screenshot-timing-2026-10-17.txt).

The corrected instrumentation APK and debug lint passed in 4m 1s (63 tasks:
6 executed, 57 up-to-date). The final 2×-text rerun then passed, including
silent native-model decoding, isolated executor/journal/cancellation checks,
navigation and isolated round-watch layouts at 1×/2×. Android font scale 1.0
was restored, and real fitting/noise preference digests remained unchanged.
Visual review confirms the compact Home fits at 2× and inactive Voice setup
shows Start and shake choice before active-session controls. No real microphone
capture or aid playback was performed by these checks.

## Required physical acceptance

Still pending: actual Hey Sig sensitivity and false wakes/other speakers;
locked/pocket microphone recognition and aid reception; phone/watch deliberate
shake versus exercise/movement and haptics; permission denial/revocation/privacy
switch; Off/Pause/End/tile/notification; call/noise/TTS arbitration; process
termination/reboot; real paired release Data Layer; eight-hour battery impact;
TalkBack and exact Android 16/16 KB runtime coverage. Development installation
or silent fake-transport checks do not establish these results.

No public submission was prepared or performed. Privacy publisher/contact/host,
medical/health declarations, microphone-FGS Play declarations and applicable
Play test-track acceptance remain separate release gates.
