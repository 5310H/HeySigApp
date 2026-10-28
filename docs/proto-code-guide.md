# HeySig Proto: code and behavior guide

October 16, 2026. This guide describes the current manual acoustic-control prototype, so future work can build on the working code without mistaking saved app state for aid readback.

## What it does

Android sends short command tones through the phone speaker to the configured hearing aids. It offers program slots, left/right battery requests, volume, Sound Sharpness and tinnitus levels. It does not need to operate Signia's screen to send these commands. The user separately confirmed the tested changes and restorations.

This user's working qualified address is **12 (0x0c)**, recovered from actual Signia output. Address-XOR is enabled. This is configuration for the tested setup, not a generic Signia default. The form requires explicit addresses; its basic-default preset uses zero, which did not work on these aids. Select the correct address before sending.

Neither app can query the aids in this acoustic setup. HeySig remembers the last completed request. Signia's saved program/slider values do not automatically follow requests sent by HeySig. Program beep counts and the user's listening feedback establish reception separately; completion of phone playback alone does not.

## File map

| File | Responsibility |
|---|---|
| `androidApp/src/debug/kotlin/com/heysig/app/ProtoActivity.kt` | Debug-only manual form, input validation, audio preflight, single-request playback, Stop/cancellation, and completed-request labels |
| `androidApp/src/main/AndroidManifest.xml` | Android launcher, SDK levels, app identity and label |
| `research/acoustic/kotlin/ProtoRequest.kt` | Explicit waveform profile, command mapping, CRC/bit encoding, PCM generation, WAV wrapping and command-line renderer |
| `tools/build-android-prototype.sh` | Kotlin compile, D8 dexing, ARM-compatible packaging, local debug signing and verification |
| `research/acoustic/tools/sig3_chirp_packet.py` | Independent packet/CRC model |
| `research/acoustic/tools/waveform_oracle.py` | Bounded interpretation of original encoder CIL for offline comparisons |
| `research/acoustic/tools/decode_playback_capture.py` | Known-profile decoding of captured postmix WAVs into command/address candidates |
| `research/acoustic/tools/trace_*.py` | Fingerprints of original methods inspected during research; these do not automatically prove semantic conclusions |
| `research/acoustic/tests/check_proto_waveform.py` | Battery/program/volume rendering comparisons, with focused program/volume modes |
| `research/acoustic/tests/check_extra_slider_waveforms.py` | Focused Sound Sharpness/tinnitus comparisons and invalid-range rejection |
| `docs/acceptance/` | Actual tests, user feedback, restorations, failures and limits |
| `research/acoustic/fixtures/` | Sanitized decoded observations, hashes and offline comparison results |

The existing `shared/`, voice, background, accessibility, watch and iPhone directories are scaffolding. The current working renderer is under research; it has not yet been extracted into a production shared control layer.

## Command mappings

`ProtoRequest` exposes typed entry points rather than requiring the Android UI to assemble raw packets:

| Action | Kotlin API | Command |
|---|---|---|
| Left battery | `pcm(BatterySide.LEFT, address, profile)` | `0x80` |
| Right battery | `pcm(BatterySide.RIGHT, address, profile)` | `0x81` |
| Program slot 1–6 | `programPcm(slot, address, profile)` | `0x40 + slot - 1` |
| Standard volume 0–15 | `volumePcm(level, address, profile)` | `0x1f - level` |
| Sound Sharpness −8..7 | `balancePcm(level, address, profile)` | `0x20 + (level & 15)` |
| Tinnitus 0–15 | `tinnitusPcm(level, address, profile)` | `0x3f - level` |

Program slots are one-based in the form and zero-based in the protocol. Names belong to the fitting: the tested setup includes Universal at 1, Sleep at 2 and TV at 5. Do not hardcode those names as universal mappings.

Volume/tinnitus encode attenuation, so a higher displayed level yields a lower command position. Sound Sharpness is Signia's sharp/soft control; it should not be assumed to mean ordinary stereo left/right balance. Its platform-specific orientation was confirmed for this setup with live neutral/+4 captures. Other platforms require their own mapping check.

The numeric volume implementation covers the standard fitted range, not every platform's special receiver mute/unmute sequence. Tinnitus availability is per fitted program. The prototype does not discover that capability automatically; Universal exposed it in the accepted tests, while TV did not.

## From button to aid

1. The UI reads a snapshot of the address/profile and chosen target. Range checks reject invalid input before playback.
2. `preflight()` requires an awake/unlocked phone, DND off, normal audio mode, an unmuted phone, media volume at least half maximum, no recognized external outputs, and an available built-in speaker. It reports problems instead of changing those settings.
3. The renderer constructs PCM for that command/address. An atomic busy flag allows one request at a time.
4. A worker creates an `AudioTrack` with legacy media attributes, PCM16 mono, the explicit sample rate and streaming transfer mode. It prefers the built-in speaker, writes the full buffer, starts playback and checks actual routing/playback progress with a bounded timeout.
5. Stop or leaving the activity requests cancellation. Release happens in `finally`. Cancellation cannot undo audio already sent or a command already received.
6. Only a completed, uncancelled request updates its persisted last-request label. Failures and cancellations must not be treated as successful aid changes.

The Android package ID remains `com.heysig.batteryprototype` so an update can replace the existing installed prototype. The launcher class/file is now `ProtoActivity`, and the displayed app name is **HeySig Proto**. On creation it copies missing last-request values from the old Activity preference file, preserving prior request history across the rename. The retained old class strings are migration identifiers, not current source filenames.

Preferences hold completed program/volume/balance/tinnitus requests. They do not form a per-program saved-settings model and do not persist the complete address/profile setup. Those fields still require explicit input after recreation. The debug signing key is local and must be retained for compatible updates.

## Waveform construction

The tested profile uses 44,100 Hz, center frequency 15,360 Hz, band spacing 512 Hz, bit-duration parameter 0.0625 seconds and gain divisor 1.2. Four carrier frequencies are 14,592 / 15,104 / 15,616 / 16,128 Hz. Profile inputs are validated against Nyquist and finite/range constraints.

The command feeds CRC-8 (polynomial 0x07). The enabled profile XORs the result with the qualified address and 0xaa. Command/checksum bits are interleaved into two streams that choose 32 frequency pulses. The address is encoded through this checksum relationship; it is not the app's device GUID.

Pulse length is ties-to-even rounding of `bitSeconds / 4 * sampleRate`: 689 samples in the tested profile. Each pulse has 50-sample cosine edges and restarts carrier phase. The renderer adds two leading and ten trailing silent pulse lengths, producing 30,316 samples (60,632 PCM bytes), approximately 0.687438 seconds. It emits signed 16-bit little-endian PCM. `wav()` adds a standard mono WAV header.

The 1.2 divisor comes from Signia's startup broadcast-level override; its encoder constructor alone would suggest 4. The original static Android player also contained a pre-write initialization-state error; the current streaming player supersedes that version.

## How it was verified

Three evidence layers answer different questions:

- Packet/original-method checks establish command definitions and encoder agreement for the inputs tested.
- Actual Signia/HeySig playback captures recover tone sequences and destination addresses. Capture used scrcpy playback duplication at 48 kHz stereo, without microphone/video recording. This is mixed/resampled output, not automatically original generator-buffer equality.
- User listening and program beep counts establish actual aid behavior. Program changes produce a beep count equal to the slot on this fitting. Battery requests produce the usual aid battery indication, not a numeric percentage read by the app.

See [working programs/address recovery](../research/acoustic/notes/decoded-program-capture-2026-10-15.md), [battery acceptance](acceptance/direct-proto-2026-10-15.md), [volume scope](acceptance/direct-volume-2026-10-16.md), and [Sound Sharpness/tinnitus acceptance](acceptance/direct-balance-tinnitus-2026-10-16.md). Right battery had an initial miss before its accepted retry; retain that observation when assessing reliability. Tested levels are not exhaustive acceptance of every endpoint, aid model or firmware.

## Build and checks

Use [phone prerequisites and commands](../androidApp/README.md) and [watch instructions](../wearApp/README.md). Both apps connect directly; the desktop build and relay have been removed.

```sh
python3 research/acoustic/tests/check_packet.py
python3 research/acoustic/tests/check_proto_waveform.py --volume-smoke
```

Original-CIL comparison additionally requires the pinned research dependencies and caller-supplied extracted assemblies; see the test scripts' `--help`. The extra-slider check accepts the built Android JAR and compares six cases. No test should trigger phone playback automatically.

The renderer CLI also accepts `left`, `right`, `program1`…`program6`, `volume0`…`volume15`, `balance-8`…`balance7`, and `tinnitus0`…`tinnitus15`, followed by explicit address/XOR/profile/output arguments. Existing WAV outputs are refused.

## Before adding automation

Move fitting configuration, typed commands and serialized transport behind a reusable control interface. Add durable settings/mapping and diagnostics before noise automation. Calibrate the new microphone sampler rather than copying Tasker thresholds. Preserve cooldown, hysteresis, manual override, Sleep priority and cancellation. The current app has no noise sampler, voice wake, background scheduler, microphone capture or accessibility control.

Raw device captures, proprietary extracts, APKs, signing keys and build output are ignored. Commit sanitized evidence and source documentation; keep reproducible capture tooling/details with the research notes. Several historically battery-named files now use `proto` filenames; their contents still describe the specific battery tests they originally recorded.
