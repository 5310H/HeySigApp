# Direct battery request: static call-path trace

October 14, 2026. Analysis used the assemblies already extracted from installed Signia **2.8.0.18214** under `/tmp/signia-chirp-analysis-20261014`. No phone connection, audio playback, configuration read, or endurance-trial modification was performed.

## Findings

The two inspected UI implementations (`BluetoothAndBatteryStatusModel` and `BatteryControlCenterItem`) call `IBatteryExtension.WriteStatusToneAsync` through their acoustic hearing-system provider. Their `PlayBatteryStatusSound` state machines pass the selected ear and handle a tone-status exception by displaying the acoustic-command error. This acoustic branch is distinct from Bluetooth battery reporting.

The single-side path is:

```text
UI PlayBatteryStatusSound
  → BatteryExtension.WriteStatusToneAsync(selected receiver side)
  → BatteryExtension.WriteToneAsync(side, cancellation)
  → HearingSystem.WriteToneAsync(side, command, cancellation, false)
  → Device.WriteToneAsync(command, cancellation, false)
  → ToneService.SendToneAsync(command, fully qualified address, cancellation, false)
  → GenerateControlTone(command, fully qualified address)
  → CommandPlayer.PlayToneAsync(PCM, cancellation)
```

`BatteryExtension.GetBatteryToneId` returns **0x80 for left, 0x81 for right**. Its `WriteToneAsync` explicitly supplies `false` for the final flag. The hearing-system and device methods forward that flag; the audio service tests it before generating a sniff-suspend tone. Consequently, **this traced battery path does not prepend the optional sniff-suspend tone**. The single-tone service calls `GenerateControlTone` once and plays its output once. No repetition loop appears in the inspected single-side UI/service/player path. This is static behavior for these implementations, not an on-phone recording or a guarantee about every Signia control path.

The receiver-side `Both` extension branch enumerates the configured sides and awaits all tasks; the Android command player serializes playback with a semaphore. Our initial test should use a single explicit side and its own address rather than invent a stereo or simultaneous request.

## Address: important distinction

`Device` constructs the byte passed to the encoder as:

```text
fullyQualifiedAddress = byte((brandIdentifier << 4) | configuredArcAddress)
```

Thus the Kotlin generator's address argument is the **fully qualified byte**, not necessarily the bare pairing number. `HearingSystem.GetArcAddress` exposes the bare address; `Device.WriteToneAsync` sends the qualified byte. Left and right can have different stored addresses.

The stored address is obtained through `HearingInstrumentParameterKeys.ConfiguredAcousticAddress`, scoped to the device identifier and side. Basic pairing saves separate left/right pairing addresses and a converted app brand. `RandomArcAddressProvider.Create` uses `Random.Next(1, 15)` for new addresses; it does not supply evidence of the user's existing address.

The shared brand enum and the acoustic brand parser distinguish `Signia` (enum 35, identifier 0), `Siemens` (enum 45, identifier 0), and `BasicSiemensSignia` (enum 10, identifier 4). These mappings must not be selected solely from the app's displayed name. The active stored brand/address has not been read.

## Audio profile and playback

The audio constructor defaults decode to:

| Parameter | Constructor default |
| --- | --- |
| Sample rate | 44,100 Hz |
| Center frequency | 15,360 Hz |
| Band spacing | 512 Hz |
| Frequencies | 14,592 / 15,104 / 15,616 / 16,128 Hz |
| Bit duration | 0.0625 s |
| Samples per pulse | 689, using ties-to-even rounding |
| Gain divisor | 4 |
| Address-XOR checksum | enabled |

`ToneGenerationConfigurator` permits gain, center-frequency and band-spacing changes followed by recalculation. **Constructor defaults do not establish the active phone profile.** The current prototype's explicit inputs remain necessary.

`GenerateControlTone` renders 32 frequency pulses, appends twelve pulse lengths, shifts the original signal by two pulse lengths, and emits signed 16-bit little-endian PCM. Under the constructor defaults, that is 30,316 samples / 60,632 PCM bytes / approximately 0.687438 seconds, including two leading and ten trailing silent pulse lengths.

Android's player builds an AudioTrack with the generation sample rate and configured format, chooses ring or music via `GetDeviceStream`, prefers the speaker, waits for the end marker, and releases the track/semaphore. The traced audio service checks its sound-play status before generating audio. A successful return is playback completion, not a measured hearing-aid battery percentage or proof of aid reception.

## Reproducible identities

[Method fingerprints](../fixtures/proto-request-trace.json) record SHA-256 hashes of four original assemblies and twenty-five selected method bodies. The artifact contains no extracted DLLs or full proprietary disassembly. Recompute with the dependencies already pinned in `tools/requirements-research.txt`:

```sh
python3 research/acoustic/tools/trace_proto_request.py /private/assemblies --output /tmp/proto-request-trace.json
```

The tool fingerprints methods and extracts the gain table; the other semantic findings above are manual instruction analysis. The earlier packet validation and Kotlin/Python rendering comparisons remain separate checks.

## Next gate

After the endurance trial, obtain the configured left/right address and brand and confirm the active audio profile, using a read-only export or visible settings where available. Compare the complete single-side Signia battery PCM output against the Kotlin-generated output for those same inputs. A reference may come from instrumented generation or an appropriate audio capture; microphone capture includes speaker/room effects and cannot establish byte equality. Do not guess the active address or replace the ongoing trial to collect it.

Then integrate one manual Android playback action with audio guards and verify the user's audible battery indication one side at a time. This trace resolves the single-side send sequence, but does not yet accept real-device playback.

## Basic setup without user pairing

Following the user’s clarification that their setup uses no pairing, the basic initialization path was inspected. `FoundationConfigurationManager.DefaultConfiguration` explicitly sets both acoustic addresses to zero and the app brand identifier to zero. `BrandConverter.ConvertToBrand(0)` maps to the shared `Signia` enum (35), and the acoustic brand parser maps that to identifier zero. This produces a fully qualified address of zero for the basic default case. `SetBasicHearingSystem` uses the foundation configuration addresses to create a basic system when no existing devices are present. These static defaults support a **manual address-zero battery test**, without requiring the user to invent pairing numbers; they do not prove the user’s current database is unchanged from defaults.

The desktop and Android prototypes offer an explicit “Use basic Signia defaults” action: both qualified addresses zero and the startup-matched audio profile described below. It sends only a selected-side battery request when a Check button is pressed. Aid feedback is still the acceptance criterion. The foundation database’s default `AcousticGain` field is 2.0, while the inspected encoder constructor uses divisor 4; the startup-to-generator mapping was subsequently resolved as described below; the preset now uses the verified startup divisor 1.2.

## Startup output-level override found after the first silent test

The user reported no aid response from the initial Chromebook test and confirmed there is no user pairing in their setup. Signia was then closed normally; this investigation continued offline without reading or changing its saved configuration.

In the installed app’s `RTA.Maui.Signia.dll`, `Bootstrapper.RegisterAcousticClasses` initializes the acoustic services and calls `IToneGenerationConfigurator.set_BroadcastLevel(2)`. The setter indexes `AcousticConstants.get_GainArray`, whose extracted array is `[4.0, 2.0, 1.2]`, then calls `Recalculate`. Consequently, the observed startup path changes the encoder divisor from **4.0 to 1.2**. The generated PCM amplitude is about 3.33 times the constructor-default amplitude at the same phone/system volume. This is a digital amplitude comparison, not a measured speaker or ear sound-pressure level.

The initial preset missed that startup override. Both prototype presets now select divisor 1.2, with the same rate/frequencies/address-zero test hypothesis. This is a concrete startup dependency in audio initialization; it does not by itself establish whether any additional aid-side initialization occurs.

Separately, `AppLaunchManager.NavigateToHomeScreen` invokes `SetAcousticHearingSystem` before navigating home. The inspected implementation checks the saved acoustic-setup flag, obtains stored acoustic IDs, loads the saved hearing system when IDs exist, and selects it through the provider; a missing-ID branch creates a basic system from saved left/right configuration addresses. These method names describe Signia’s internal acoustic setup, not a requirement for the user to perform Bluetooth pairing. The user’s existing configuration remains authoritative and has not been reset or re-paired.

The next controlled comparison is the same selected-side request with divisor 1.2 while Signia remains closed. If it still produces no response, normal Signia startup followed by an otherwise identical retest can investigate a startup-dependent aid state. Record playback failures separately from silence. A failed test still does not isolate address, speaker response, routing, or the complete waveform on the real device.

## User-reported Chromebook retests

The user reports no audible aid response from a selected-left Chromebook request with gain divisor 1.2 while Signia is closed. After a normal Signia launch, the otherwise unchanged Chromebook request again produced no response. These results do not support a simple “Signia must be open” explanation and do not accept the reconstructed request on real hardware. The next control is the original Signia left battery button itself, followed by isolation of source-speaker/audio-path differences if that control succeeds. No saved Signia configuration was changed by this work.

The user subsequently confirmed that Signia’s own left battery button still produces the usual audible aid indication. That supplies a positive reference on the phone, but changes both the software source and speaker compared with the Chromebook test. It does not isolate the cause as settings alone.

## Visible Pixel settings inspection

The user supplied wireless-debugging port 37325 and the Pixel reconnected at its previous IP. Signia’s private configuration is not readable through standard `run-as` because the installed app is not debuggable. A bounded Signia-process log read exposed no matching battery/audio configuration entries. No bypass was attempted.

Navigated the existing app through Settings → App settings. The displayed Smartphone sound value is **Media sound**, consistent with the prototype’s Android media stream. The screen lists Set up the app, Connect to TeleCare and Rate the app; the setup and connection actions were not selected. No visible address/profile was exposed. The original Controls screen showed Universal, volume 8; no aid controls were operated during inspection.

A separate prototype APK was prepared for a same-Pixel-speaker comparison, preserving Signia data. Screen inspection and APK testing constitute additional phone use during the observation period and must be considered when interpreting total-phone battery consumption; no Tasker trial configuration or sampling controls were modified.
