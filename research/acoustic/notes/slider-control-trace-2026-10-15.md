# Volume, balance and tinnitus: read/write/storage trace

October 15, 2026. Static inspection of the already extracted installed Signia 2.8.0.18214 assemblies, accompanied by bounded live Pixel tests. No PCM or active address was extracted. These findings concern Signia's UI/services and acoustic transport, not hearing-aid firmware.

## Reading values

`Component.RemoteControl.dll` services choose Bluetooth reads when the relevant side is connected. Otherwise they obtain the current program and use the corresponding stored program fields, falling back to defaults when values are unavailable.

| UI family | Service read state machine | Stored acoustic-mode values |
|---|---|---|
| Volume | `VolumeService.GetAsync`, `0x06000862` | `Program.VcValueLeft`, `VcValueRight` |
| Balance / Sound Sharpness | `SoundBalanceService.GetAsync`, `0x060007fb` | `Program.ScValue` |
| Tinnitus | `TinnitusVolumeService.GetAsync`, `0x06000839` | `Program.TinnitusValueLeft`, `TinnitusValueRight` |

Bluetooth uses the corresponding `Volume`, `SoundBalance`, or `Tinnitus` extension's `GetAsync`; acoustic reads do not query the aids for the current numeric value. As with the [program trace](program-control-trace-2026-10-15.md), current program in acoustic mode is itself app-maintained state.

The interface calls this balance family **Sound Sharpness**. Its implementation is `SoundBalance`, with a signed slider and a converted position. This trace does not establish a conventional stereo left/right balance mechanism; keep the actual Signia terminology when explaining its acoustic effect.

## Sending changes

Each service selects the applicable Bluetooth/acoustic extension and processes side-specific results. Successful write handling schedules a storage update. The acoustic multi-side methods inspect whether configured sides share an address: matching addresses can use one master-side transmission; different addresses can require separate side requests.

The acoustic single-side writers use converters and validation before producing commands:

| Family | Acoustic state machine | Command construction |
|---|---|---|
| Volume | `WriteVolumeAsync`, `0x06000207` | `0x10 + converted position`, with receiver-state branches |
| Sound balance | `WriteAsync`, `0x060001ce` | `0x20 + converted position` |
| Tinnitus | `WriteTinnitusVolumeAsync`, `0x060001f6` | `0x30 + converted position` |

For volume, the inspected receiver-state branches can send `0x5a` alone, or prepend `0x5b` before the numeric command; the ordinary branch sends the numeric command. Therefore sending only a numeric command is not a complete implementation of all volume/mute states. The command lists are passed through `WriteTonesAsync`; sound balance and tinnitus use `WriteToneAsync`. The inspected calls pass `false` for the final flag, as in the battery/program traces.

These are converted **positions**, not a license to add raw displayed values to command bases. In particular, signed balance slider values require conversion. The previously verified command ranges and control bits remain recorded in the [original validation](chirp-validation-2026-10-14.md). This trace does not freshly validate every converter branch or every slider endpoint.

Tinnitus availability and independent-ear adjustment depend on capability/decoupling configuration; see the [setup trace](setup-capabilities-2026-10-15.md). TV's absent tinnitus tab must be treated as unavailable rather than supplied with a fabricated numeric reading.

## Saving the result

The inspected storage state machines resolve the current program, modify its numeric fields, and invoke `IProgramService.UpdatePrograms`:

- `UpdateVolumeInStorageAsync`, `0x0600086c`: left/right `VcValue`.
- `UpdateSoundBalanceInStorageAsync`, `0x06000805`: `ScValue`.
- `UpdateTinnitusInStorageAsync`, `0x06000845`: left/right `TinnitusValue`.

This is why a later acoustic-mode read can show the value that Signia sent. Playback completion and a saved number are not independent proof that the aid received the command. Full transport arbitration, every exception branch, all mute cases and firmware behavior remain outside this focused trace.

## Live changes and restoration

Fresh inspection established TV active, volume 8. Tests used Universal, which exposes all three controls, then returned to TV. Numeric readback and the selected tab were checked after each action.

| Family in Universal | Observed sequence | Result |
|---|---|---|
| Volume | 8 → 9 → 8 | Changed and restored |
| Sound Sharpness | 0 → 1 → 0 | Changed and restored |
| Tinnitus, initial attempt | 0 → 2 → 1 | Intended one-step change and first restoration failed |
| Tinnitus recovery | 1 → 0 | Original value restored |
| Tinnitus direct-drag retest | 0 → 2 → 0 | Change and restoration verified; one-step precision still failed |

The initial gesture included a thumb tap followed by a drag. The retest omitted the preparatory tap, but still reached tinnitus 2 rather than 1. Do not accept either as a one-step tinnitus test. The scripts' nonzero exits reflect those assertions. Fresh final XML separately verifies tinnitus zero before returning to TV; final TV readback is active with volume 8. Volume and Sound Sharpness had already been verified restored to 8 and 0 in Universal. No additional endpoint or exhaustive slider testing was attempted.

The focused process logs contain 17 playback completions across the initial session and tinnitus retest, including program navigation and slider actions; each reports 30,316 frames delivered. Audio-service history for observed events reports 44,100 Hz mono media/UNKNOWN audio and device ID 3. Multiple playback events occurred within individual gesture intervals. These logs do not expose the individual commands or prove why more than one tone played; do not assume one user gesture equals one packet, or infer left/right addresses from the event count.

This completes the bounded read/write/storage investigation and real UI-change trace for all three families. It does not certify precise new slider automation or independent acoustic aid receipt. Exact PCM, active addresses, every converter/mute branch and exhaustive endpoint behavior remain outside this trace.

[Sanitized live observations](../fixtures/live-slider-observation-2026-10-15.json) retain both failed attempts and final restoration. Raw logs, screenshots and UI snapshots are in ignored `private/signia-sliders-20261015/`. Diagnostic labels such as `tinnitus-restored` or `tinnitus-retest-one` describe intended steps; their recorded numeric values and assertion failures determine the outcome.

## Evidence

[Method fingerprints](../fixtures/slider-control-trace.json) identify fifteen inspected methods in two assemblies. Semantic conclusions are manual CIL analysis, not execution of the extracted assemblies. Reproduce fingerprints with the pinned research dependencies:

```sh
python3 research/acoustic/tools/trace_slider_control.py /private/assemblies --output /tmp/slider-control-trace.json
```
