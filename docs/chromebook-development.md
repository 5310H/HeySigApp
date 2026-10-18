# Chromebook-first Android development

October 16, 2026. Develop the actual Android application on the Chromebook and run it inside the Chromebook's Google Play/Android environment. This is the same Kotlin/Android source that will be built for the phone; a separate desktop simulation is not required. Future work uses `main`; `proto` remains the saved earlier snapshot.

## Current manual-core slice

`HomeActivity` is the launcher for version 0.9, targeting API 36 through the [Gradle pipeline](gradle-release-builds.md). `ProtoActivity` remains a debug-only research screen, with the existing renderer unchanged. The development package stays `com.heysig.batteryprototype` with its preserved signer; production validation candidates use `com.heysig.app` and a separate upload signer.

The experimental [Noise screen](noise-quick-start.md) now provides six ordered program assignments, five thresholds, bounded microphone samples and Observe proposals without playback. Automatic switching remains disabled. Voice has separate saved prompt and response switches; the watch companion has its own local switches.

- Home provides configured program buttons, explicit volume/Sound Sharpness/tinnitus targets, left/right/both battery requests, Stop, read-only audio checks and marking requested state unknown.
- Setup saves explicit control/left/right addresses, names for up to six slots and per-program tinnitus capability. User-tested address 12 and known names are editable suggestions requiring Save. Unknown tinnitus capability blocks its control.
- Voice provides Android speech input and a typed command field using the same shared parser. Installed recognition service may use a network connection; this build does not claim offline recognition. Unsupported speech sends nothing. Conflicting recognized alternatives ask for another command. Recognized text is held only for the current interaction.
- Saved shows the explicitly saved snapshot and restores known fields only. Last-sent values update separately. Only an explicit Save replaces the snapshot; later manual commands and restore retain its source record. Restore previews known/unknown fields. Use Speak answer and say “confirm restore” or “cancel” for spoken confirmation.
- A repeated program/level target sends nothing and offers explicit Send again. This compares remembered requests, never actual aid state.
- Stop and leaving the activity cancel remaining playback/stages. A pending-operation marker prevents interrupted playback from becoming a confident baseline after restart. No auto-replay, background listener or automatic command at startup.

The renderer's existing validated profile remains 44100 Hz / center 15360 Hz / spacing 512 Hz / bit duration 0.0625 / divisor 1.2 / address-XOR on. Playback retains speaker/audio guards. Chromebook audio routing and aid reception are not certified by compilation or screen checks.

## Code responsibilities

| Source | Responsibility |
|---|---|
| `shared/.../control/ManualControl.kt` | Immutable fitting/program/state models, ranges, capabilities and request validation |
| `shared/.../control/VoiceCommands.kt` | Sig3-style text parsing, typed intents and transport-independent session/restore planning |
| `androidApp/.../HomeActivity.kt` | Current native screen flow, private preferences, speech adapter and manual orchestration |
| `androidApp/.../SpeakerPlayback.kt` | Read-only audio preflight and bounded speaker streaming |
| `research/acoustic/kotlin/ProtoRequest.kt` | Existing command-to-PCM renderer; unchanged in this slice |
| `shared/.../noise/NoiseMeasurement.kt` | Bounded relative digital PCM statistics without recording retention |
| `shared/.../noise/NoiseObserve.kt` | Six-level settings, hysteresis, target confirmations and explicit noise voice edits |
| `androidApp/.../PhoneNoiseSampler.kt` | Foreground, bounded built-in microphone capture with cancellation and route checks |
| `androidApp/.../NoiseFeature.kt` | Noise draft editor, private persistence, Sample and Observe; no playback dependency |

Paths abbreviated with `...` refer to the corresponding `src/commonMain/kotlin/com/heysig` or `src/main/kotlin/com/heysig/app` directories. The platform-free session is also a local development/test harness; Android orchestration remains an initial activity implementation to be moved behind the specified coordinator as further features are added.

Configuration uses private preferences `heysig-core-v1`, separate from research Activity preferences. Existing research sliders are not imported as current state because their program context is not reliable. Changed fitting setup currently clears requested/saved records conservatively, including label changes. Label-only migration without clearing is future work. Configurations and snapshots have no production import/export yet.

## Build and local checks

Run `bash tools/check-manual.sh` for shared fitting, ranges, tinnitus, command parsing, voice preferences and restore-plan checks. `bash tools/check-noise.sh` checks relative digital measurement, six-level validation, hysteresis, unused/repeated targets, stale samples and noise voice edits without microphone capture. Existing renderer regression: `python3 research/acoustic/tests/check_packet.py`.

Build using the environment variables in [Android build instructions](../androidApp/README.md). Output remains `androidApp/build/heysig-proto.apk` for development compatibility. Builds do not install or play commands automatically. Keep the local signer for compatible upgrades.

On this Chromebook, ADB identifies its Android container as `emulator-5554` (model `staryu`); this is not the Pixel phone. Select the exact destination for installation and launch `com.heysig.app.HomeActivity`. Never rely on an unqualified ADB target when multiple devices are connected. No ADB connection is needed for installed manual operation.

## Completion and device acceptance

Do not assign a single “99% complete” percentage based on desktop tests. Track working application flows separately from hardware acceptance:

1. Chromebook: startup and navigation, fitting persistence, typed phrases, invalid targets, snapshot preview, read-only queries, speech launch, cancellation and interrupted-operation recovery.
2. Phone: same app plus actual speech capture, speaker route, aid reception, lifecycle behavior and preferences upgrade. Test selected programs and levels with explicit user confirmation/restoration.
3. Watch: native communication, independent microphone launcher, local replies, shared parsing, all settings dialogs, stale/duplicate request handling and Stop/disconnection outcomes.

The full phone/watch product is **not complete**. Native watch transport is not implemented in this slice. Wear dependency metadata download and phone-package inspection were declined during this session; work continued locally without those accesses. No phone/watch installation or aid playback is claimed from the Chromebook smoke tests.

Guided voice editing of fitting/settings, complete alias/relative-command support, preference import/export, production background lifecycle, schedules, automatic noise switching and optional language assistance remain tracked in the [coding specification](coding-spec.md). Six-level Noise Setup/Observe and independent prompt/response preferences are development features requiring device acceptance. A system speech prompt is not a wake-word listener.

## Playback timeout investigation

The first Chromebook manual playback attempts timed out. Targeted AudioTrack logs showed track recreation during initial routing (`dead IAudioTrack` and buffer sequence mismatch), followed by buffer timeout and zero delivered frames. This was an Android playback failure, not an aid acknowledgment failure.

Version 0.4 changes `SpeakerPlayback` to request transient audio focus, start the stream before writes, feed bounded nonblocking chunks, and poll actual progress. A 50 ms zero tail allows resampling to drain; the command PCM itself remains unchanged. Focus loss, Stop, output changes, write errors and deadlines fail explicitly without replay. Timeout errors include frame/byte counts rather than a generic message.

The optional silent-audio diagnostic now lives in the separate Gradle Android test APK. Build `:androidApp:assembleDebugAndroidTest`, install the debug app and test APK on the selected target, then explicitly run `adb -s emulator-5554 shell am instrument -w com.heysig.batteryprototype.test/com.heysig.app.PlaybackSmoke`. It plays two zero-only buffers, checks completion/speaker routing and rejects an already-cancelled request. The default `Api36Smoke` runner uses no audio or microphone. Neither runner is packaged in release artifacts. Physical aid reception remains a separate listening test.

## Manual voice follow-up (0.5)

The next build adds recognized speech text for the current session, number/slot program forms, a confirmed clarification for “program to,” Sound Sharpness naming, additional current-level phrases and rejection of contradictory number signs. Current-session speech text is not persisted. Stop/Cancel take precedence over a program named Cancel.

Speak and Stop move above the main controls. Setup provides Save at the top as well as the bottom, warns about unsaved changes when leaving for a different tab, and Home reports tinnitus capability from the saved active-program configuration. A changed dropdown becomes active only after Save. Program and restore confirmation requests expire after 60 seconds.

Live Chromebook checks and their limits are maintained in [manual-control acceptance](acceptance/chromebook-manual-controls-2026-10-16.md); parser regression currently exercises 410 phrase cases plus model/restore/Cancel checks. The numbered-program failure is retained alongside successful named-program control.
