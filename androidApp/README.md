# Android HeySig development build

Version 0.9 targets API 36 and launches `com.heysig.app.HomeActivity`: native Home, Setup, Saved, Voice and Noise screens. [Six-level Noise Setup and Observe](../docs/noise-quick-start.md) are experimental; automatic switching is disabled. Voice prompts and spoken responses have independent saved switches. See [Gradle development/release builds](../docs/gradle-release-builds.md) and [Chromebook development](../docs/chromebook-development.md). The earlier research Activity is included only in debug builds. The compatibility build command and APK filename are retained.

See [the code guide](../docs/proto-code-guide.md) for responsibilities, mappings, playback flow, validation and limits. Launcher: `com.heysig.app.HomeActivity`; output: `androidApp/build/heysig-proto.apk`.

Start with the [voice command quick guide](../docs/voice-quick-start.md). Representative live Chromebook program, level and save/restore results are recorded in [manual-control acceptance](../docs/acceptance/chromebook-manual-controls-2026-10-16.md).

## Sound Sharpness and tinnitus controls

October 16 live acceptance: Sound Sharpness 0 → +4 → 0 and tinnitus 0 → 4 → 0 both passed user-confirmed change and restoration in Universal/address 12. Volume 8 was sent first. Six original-CIL waveform checks and four invalid-input checks passed. Other levels/programs/platform variants remain separate acceptance.

Explicit selectors now expose Sound Sharpness −8..7 and tinnitus 0..15 through the same destination/profile and guarded playback. The captured fitting maps them to `0x20 + (signed value & 15)` and `0x3f - tinnitus level`. Current aid state remains unknown; only completed requests are remembered. Use tinnitus only in a program that supports it. Other Sound Sharpness platform orientations need their own setup validation. See [reference captures and live acceptance](../docs/acceptance/direct-balance-tinnitus-2026-10-16.md).

## Direct volume experiment

The prototype now offers absolute volume levels 0–15 using the address selector shared with program requests. For the standard 15-step profile, the numeric command is `0x1f - level`; captured Signia levels 8/9 decoded to 0x17/0x16 at address 12. Special receiver mute/unmute sequences for other fitted ranges are outside this bounded implementation. The display stores only the last completed request and does not imply queried aid state. Signia's saved slider will not automatically reflect external HeySig commands.

Levels 4, 8 and 9 passed original-CIL waveform comparison. Live acceptance is tracked separately. A 4-step reduction and restoration was chosen because the user finds one-step listening comparisons difficult.

**Battery acceptance, October 15:** both left and right requests work for this user's setup with qualified address **12** in both fields, XOR enabled and the startup profile/divisor 1.2. Right required a retry; keep the first miss in the reliability record. These are audible aid indications. See [battery acceptance](../docs/acceptance/direct-proto-2026-10-15.md). Values remain explicit user-specific configuration, not generic defaults.

## Direct program experiment

**Accepted October 15:** Sleep slot 2 and TV slot 5 work with this user's decoded qualified address **12**, address-XOR enabled and the startup profile. The user confirmed two/five beeps and TV restoration. Enter 12 explicitly for this tested program destination; do not change generic defaults or infer other users' addresses. See [captured-signal evidence](../research/acoustic/notes/decoded-program-capture-2026-10-15.md). Earlier address-zero failure descriptions below are historical. Battery/slider direct commands still need separate acceptance.

Playback now follows the inspected Signia streaming mode and legacy media attributes. The earlier static variant had an initialization-state bug (checked before loading PCM); after correction it completed playback but the user reported no Sleep response. The streaming variant also completed playback with matching observed audio attributes, but the user reported no beeps/change. Direct program reception remains unaccepted; the active address and exact Signia waveform remain unresolved.

The same app now offers program slots 1–6 and a choice of the configured left/right address. It sends the statically traced numeric command `0x40 + (slot - 1)` through the existing guarded speaker playback. Slots must match the aids' fitting; no program-name mapping is assumed. This bounded experiment omits platform-specific unmute preambles and does not implement Bluetooth or acoustic readback.

The display distinguishes **last completed program request** from **current aid program unknown**. It persists only a completed request's slot, not an inferred aid response. A shared acoustic address may address both aids; choosing a side's address does not promise that only that ear changes. Signia's unchanged saved header cannot verify a command sent outside Signia.

For a real test, identify the starting aid program and the intended alternate fitted slot, retain the working Signia control path for restoration, and judge the actual aid behavior. No direct program command has yet been accepted on aids. Opening or installing this app does not send a request.

A minimal foreground research Activity reuses the desktop Kotlin generator. It provides explicit left/right address and profile inputs, separate side buttons, cancellation, and no automatic/background playback. It reads audio/lock/DND state and requires an unlocked screen, normal audio mode, media volume of at least 50%, no detected external audio outputs, and a confirmed built-in speaker route. It does not change phone volume or DND. Leaving the Activity requests cancellation.

The research Activity uses platform widgets and has no accessibility service or Tasker integration. Current Home additionally supports opt-in microphone sampling for Noise; that capture requires disclosure and runtime permission.

Use JDK 17, the pinned Gradle wrapper and SDK platform 36. ARM resource-tool setup and signing instructions are in the [Gradle build guide](../docs/gradle-release-builds.md):

```sh
bash tools/build-android-prototype.sh
```

Output: `androidApp/build/heysig-proto.apk`, signed with the preserved local development key under ignored `private/signing/`. Gradle clean does not remove that key. Release bundles use a separate upload key and application identity. No APK is installed by the build script.

Historical tool sources used before the Gradle migration:

- `https://dl.google.com/android/repository/platform-35_r02.zip`, SHA-1 matching Google's package index: `0bb560a90a7a2cbd0dd8348224d518b638fe7949`
- `https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.5.26/r8-9.5.26.jar`, downloaded SHA-256: `3502f087388ce95bbf5254555927f09385dc2636873964d0bd53f3eaf4fb49ad`

The active aid addresses/profile and acoustic reception still need device validation. Do not treat constructor defaults as verified active settings. Only the user's audible battery indication establishes a response; UI playback success cannot read a percentage.

For the user’s no-pairing setup, **Use basic Signia defaults** selects the statically traced basic address-zero case and the startup profile (gain divisor 1.2). This is a manual test preset; try one side and report the audible aid response. It does not assume a Bluetooth pairing or require entering pairing numbers. The preset’s match to the current device remains a test hypothesis.

The earlier shell build used API 29 resources while compiling/targeting 35. The Gradle build now uses platform 36 directly. Historical API 29 archive: `https://dl.google.com/android/repository/platform-29_r05.zip`, SHA-1 `9d8a7e0ffa5168dbca6c60355b9129c6c7572aff` from Google's index.

Startup tracing found that Signia overrides the constructor gain divisor 4 with **1.2**. The basic preset now applies that override; the initial form shows the startup profile with empty addresses; choose a preset or enter your explicit configuration. See [startup findings](../research/acoustic/notes/proto-request-trace-2026-10-14.md).

Optional [hands-free sessions](../docs/hands-free-quick-start.md) start explicitly on the unlocked phone, then support bounded command recognition and the existing validated transport while locked. Local wake detection, Off/Pause/End, notification/Quick Settings controls and a default-off shake trigger are implemented. No session starts after installation/reboot; physical wake, lock, gesture and battery acceptance remain pending.
