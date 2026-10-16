# Plan: replace HeySig’s Tasker setup with an app

Historical accessibility-first plan. The [October 16 program specification](program-spec.md) supersedes its control-path and feature-priority decisions, including first-release phone/watch voice parity.

October 14, 2026. Implementation plan; the full app has not started. Experimental Chromebook/Android battery-audio prototypes now exist separately. The current eight-hour phone observation trial continues independently.

## Intended result

A Kotlin Android app provides program selection, battery checks, occasional slider adjustments, saved-settings restoration, voice commands and optional noise/sleep automation. It replaces Tasker, AutoVoice and AutoInput. The first version continues operating the installed Signia app through our own accessibility service. Removing Signia requires a separately validated direct hearing-aid control path.

Use Jetpack Compose for the Android interface. Keep command parsing, validation, settings models and automation decisions separate from Android APIs so they can later be shared through Kotlin Multiplatform. Do not assume that Android’s method of operating Signia can be used on iPhone.

## 1. Freeze the working behavior and migration requirements

- Collect the current eight-hour trial when it completes; record sampling gaps, stopping behavior and total battery use. A comparable idle measurement is still needed to isolate sampling cost.
- Inventory supported commands, six configured program names, aliases, audio guards, preferences, sleep choices and noise configuration.
- Convert accepted phone evidence into an app acceptance checklist. Preserve fresh readback, repeated-target no-ops, explicit failures, manual priority and settings restoration.
- Record pending behavior separately: background wake recognition, configured sleep schedules after reboot, watch input and alternative native slider movement have not all passed acceptance.
- Keep the existing setup available for rollback. During app testing, disable competing Tasker control profiles/listeners so only one controller acts.

**Completion:** an agreed feature checklist and export of the existing configuration, without interrupting the ongoing observation trial.

## 2. Prove the Android control path before building the full interface

- Create the Kotlin project and a minimal accessibility service with user-directed permission setup.
- Identify Signia’s active program, exact configured program names, slider values and tinnitus availability from its current accessibility tree.
- Implement one serialized command executor with busy rejection, cancellation, timeouts and concrete results.
- Read the current program, select a different configured program and verify fresh readback; restore the starting program.
- Exercise Android’s own gesture dispatch for one representative slider adjustment. Port measured selectors/calibration carefully; stop when the layout changes.
- Retain foreground/unlocked requirements for automatic UI changes. Explain manual recovery when Signia is on the wrong page or a required permission is missing.

**Completion:** program read/select/readback and one occasional slider change work on the Pixel without Tasker or AutoInput. This is the first feasibility gate.

## 3. Build useful manual controls and durable settings

- Add program buttons, a battery-check action, current values, occasional-use sliders and clear status/errors.
- Port volume 0–15, balance −8–7 and tinnitus 0–15, with per-program availability checks and no movement for repeated targets.
- Port DND, phone-volume and audio-route preflight. Read settings and explain problems; do not silently change phone audio configuration.
- Preserve successful program/value snapshots atomically, with explicit Save/Restore actions, partial-failure reporting and mapping validation.
- Provide selectable program aliases and independent switches for spoken prompts/results.
- Import the existing user configuration through an explicit preview and confirmation of program mappings; avoid hardcoded program names or a new-device assumption about Pixel geometry.

**Completion:** core controls and restore behavior pass focused phone tests, including restoration of starting values and restart persistence. Exhaustive slider transitions are not required for the user’s occasional-use scope.

## 4. Replace voice plugins

- Add a microphone button using Android speech recognition and spoken responses using text-to-speech.
- Port the accepted command vocabulary, including tinnitus/tin, named programs, current-value queries, battery, restore settings, override and cancel.
- Preserve “Sig here,” retry/cancel behavior and independent prompt/result switches. Report recognition failure without dispatching a command.
- Coordinate the microphone, speech playback and command executor so capture cannot overlap competing work.
- Add optional “Hey Sig” wake detection as a separate milestone. Evaluate an on-device wake detector, its license, background-service requirements, accuracy and battery use before selecting it; do not assume continuous speech recognition is a dependable wake detector.
- Test background and locked-phone behavior. Where Signia UI control is blocked, give an unlock instruction; do not attempt secure unlocking.

**Completion:** microphone-button voice commands work without AutoVoice. Hands-free wake support is accepted only after separate real background, false-trigger and endurance tests.

## 5. Port selectable automation

- Measure the app’s microphone-derived noise index before choosing thresholds. The Tasker/AutoVoice values −54.5 / −43 must not be copied unchanged to a different sampler.
- Implement selectable quiet/normal/very-noisy roles, observation mode, hysteresis, consecutive confirmation and cooldown.
- Preserve manual override, cancellation, sleep priority, foreground/locked deferral and fresh confirmation after a sampling gap.
- Add editable sleep/wake times and programs, including timezone, clock-change, restart and overnight tests. Explain Android restrictions when an event cannot execute on time.
- Add opt-in background operation with visible running/stopped state, appropriate notifications and user permission setup. Keep automation off by default.
- Compare idle battery use against sampling and wake listening separately, then test supported combinations. Provide stop controls and bounded diagnostics with no audio recording by default.

**Completion:** real program transitions, timing, interruption recovery and unattended battery/endurance tests pass in the app. Existing Tasker acceptance does not certify the new implementation.

## 6. Add watch support after the phone app is stable

Identify the actual watch and supported platform first. Start with remote program selection, battery request and microphone-button commands. Treat watch noise measurement as a separate experiment requiring microphone availability, calibration, battery and reconnection tests. Keep phone/watch measurements distinct.

**Completion:** selected watch commands reach the phone reliably without AutoWear; no watch-noise feature is claimed before measurement acceptance.

## 7. Evaluate direct aid control and the iPhone route

- Keep UI control behind a replaceable control interface so a direct transport can be added later.
- Use the REA work as a starting point for protocol investigation. Static packet/control-bit agreement does not validate complete audio waveforms, addressing or physical aid response.
- Validate device compatibility, program commands, numeric controls and battery behavior on actual aids before replacing Signia. The user reports audible confirmation only for battery checks; do not wait for nonexistent program chirps or claim independent receipt from app readback.
- Determine a supported direct control mechanism or official integration for iPhone before committing to equivalent functionality. Android accessibility automation is not the iPhone implementation.
- If that route is viable, share the platform-independent core through Kotlin Multiplatform and build the iPhone-specific control, voice and UI components, likely with Swift/SwiftUI.

**Completion:** a demonstrated control path on each platform. Language reuse alone does not establish iPhone feasibility.

## 8. Release and remove the old dependencies

Produce an installable, signed Android build with versioned configuration, permission/recovery guidance and an export/backup option. Verify fresh installation, upgrade, permission revocation, reboot, busy/interrupted commands, changed Signia layouts and operation without ADB. Disable the old controller during the acceptance period; retire Tasker/plugins after feature acceptance, retaining their exports for rollback. If publishing publicly, review current store requirements for accessibility and microphone/background use at release time.

**First build:** manual program controls, current-value reads, battery check and microphone-button voice, followed by occasional sliders and restore settings. Add wake listening and automation after the replacement control path has passed its feasibility gate. Watch and iPhone work follow demonstrated phone reliability and platform control capability.
