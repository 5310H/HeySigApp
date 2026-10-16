# Current program and program changes: static and live trace

User clarification after this trace: the aids produce a beep count equal to the selected program slot. This supersedes the earlier no-program-acknowledgement assumption below. No beep-count feedback was collected for the TV → Universal → TV session itself; direct tests should request the heard count.

October 15, 2026. Installed Signia 2.8.0.18214 assemblies and a live Pixel 9a UI/log observation. No function hooks or PCM samples were captured.

## Reading the current program

`Component.RemoteControl.dll`, `ProgramService.GetCurrentProgram` state machine (`0x060007c6`), gets the provider's master side, calls `GetCurrentProgramIdAsync`, and resolves the resulting ID through `IProgramClientHelper.GetProgram` to obtain the program object/name.

`GetCurrentProgramIdAsync` (`0x060007c8`) checks `BluetoothConnected(side)`. When connected it invokes the Bluetooth program extension's `GetAsync`. If Bluetooth is absent, or the inspected Bluetooth read fails through its handled exception path, it reads `InternalConfigurationKeys.CurrentProgramId` from `IConfigurationService`.

Therefore **the acoustic-mode current program is app-maintained state, not a query answered by the aids**. UI readback confirms what Signia believes is selected. It cannot independently detect a missed acoustic command or an aid program changed elsewhere.

## Sending a change and updating local state

`ProgramService.InternalWriteAsync` (`0x060007ca`) selects Bluetooth/acoustic paths according to configured-side connectivity and acoustic availability. It dispatches through the corresponding program extension. The both-sides path also has per-side handling for connectivity combinations; it is not unconditionally a single broadcast.

After successful results, the acoustic/master-not-Bluetooth path schedules `UpdateProgramConfiguration` through a helper (`0x060007bd`) and raises the user-program-change event. `UpdateProgramConfiguration` state machine (`0x060007d0`) writes the requested ID to `InternalConfigurationKeys.CurrentProgramId`. This saved ID explains the subsequent acoustic-mode read.

In `WSA.Foundation.Acoustic.dll`, the multi-side `ProgramExtension.WriteAsync` (`0x060001b6`) checks whether sides have the same acoustic addresses. One branch uses the master side; distinct destinations can require separate side requests. The single-side writer (`0x060001b8`) validates that the program exists, reads platform/firmware-dependent mute requirements, optionally adds command `0x5b`, then adds:

```text
program command = byte(0x40 + internal program ID)
```

It passes the command list to `WriteTonesAsync(side, tones, cancellation, false)`. Do not equate internal IDs, one-based displayed slots, and custom program names without resolving their mapping. The platform-specific optional command must not be blindly prepended to every request. Addresses are supplied by the configured hearing-system device, as described in the [setup trace](setup-capabilities-2026-10-15.md).

## Live TV → Universal → TV test

Fresh UI inspection established **TV Selected Button** in the program list. The current list labels were Universal, Sleep, Noisy, Outdoor, TV, Very Noisy; these are this session's observed labels, superseding historical naming for this observation.

At approximately 8:45 p.m. America/New_York:

| Step | Fresh screen evidence | Playback evidence |
|---|---|---|
| Start | TV selected | No change requested |
| Select Universal | `Hearing program Universal Active`; tinnitus tab visible; displayed volume 8 | Signia AudioTrack: 30,316 frames delivered |
| Restore TV | `Hearing program TV Active`; tinnitus tab absent; displayed volume 8 | Signia AudioTrack: 30,316 frames delivered |

The two captured playback events used 44,100 Hz mono, media usage, UNKNOWN content type and device ID 3. Log times were 20:45:14.734–20:45:15.580 and 20:45:25.704–20:45:26.542. AudioTrack lifecycle intervals include audio-stack overhead, not just PCM duration. The user has not independently confirmed physical aid program changes for this test; program changes have no expected battery-style audible acknowledgement. No slider action was issued. Final Signia screen was restored to TV with displayed volume 8.

Each change produced one observed AudioTrack playback of the same frame count as the earlier battery request. This supports common tone framing but does not establish equal samples, command bytes, addresses, or independent aid receipt. There was no extra playback event in the captured selection/readback intervals attributable to reading the current program.

Raw logs and UI snapshots are retained in ignored `private/signia-program-20261015/`. [Sanitized observation](../fixtures/live-program-observation-2026-10-15.json) and [method fingerprints](../fixtures/program-control-trace.json) retain the relevant evidence without unrelated phone logs or proprietary disassembly.

Reproduce method identities with the pinned research dependencies:

```sh
python3 research/acoustic/tools/trace_program_control.py /private/assemblies --output /tmp/program-control-trace.json
```

These findings reinforce the need for HeySig to distinguish **requested/last-sent program**, **Signia UI program**, and **connected-aid readback**. Acoustic playback completion must not be presented as aid-confirmed current state.
