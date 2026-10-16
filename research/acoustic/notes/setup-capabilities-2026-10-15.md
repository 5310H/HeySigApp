# How Signia setup determines tinnitus availability and command destinations

October 15, 2026. Static inspection of the already extracted installed Signia 2.8.0.18214 assemblies. No phone connection, setup changes, aid commands, private configuration reads, or receiver-firmware inspection. These findings describe the inspected version and paths, not every Signia product.

## Main finding

The acoustic implementation uses saved capability data. It does not discover tinnitus availability by sending a chirp and listening for a response. The data is indexed by **device GUID, ear side, and program slot**. Manual setup can populate it from the user's selections; fitting QR import can populate it from fitting configuration. Bluetooth has a separate checksum-aware refresh path.

The app's saved device GUID and its transmitted acoustic address serve different purposes. The GUID scopes local configuration. The inspected acoustic sender passes a brand/address byte to the tone service. The inspected code therefore does not demonstrate that acoustic control is exclusive to a uniquely identified physical pair.

## Manual setup: where the per-program choices come from

In `RTA.Core.dll`, `ProgramConfigurationViewModel.ItemTapped` (`0x06000b9c`) toggles the selected program's membership in `ProgramUtilities.TinnitusProgramIndex`.

`ProgramConfigurationViewModel.Confirm` (`0x06000b9b`) converts those zero-based indexes to one-based program slots, passes the list to `WithTinnitusProgramAtSlots`, and calls `WithTinnitusDecoupled(false)`. It saves programs, marks acoustic setup and program setup complete, and carries the builder to the directional-hearing setup page. The internal flag is named `IsAcousticPairingDone`; setting this flag is not evidence of a Bluetooth pairing or an aid-side handshake.

In `WSA.Foundation.Persistency.dll`, `WithTinnitusProgramAtSlots` (`0x0600001a`) stores the supplied list in the builder. `AddTinnitusPrograms` (`0x06000030`) does the following:

- If the supplied list is empty, returns without writing tinnitus program keys in this method. Do not interpret this branch as an explicit clearing of every prior flag; reset/default behavior is a separate concern.
- Otherwise, visits slots 1 through 6 and writes `TinnitusVolumeControlAvailable(side, slot, guid)` as true if the slot is in the list, false otherwise, with the builder's checksum.

`HearingInstrumentParameterKey<T>` stores the key name, program slot, side and GUID separately. `ProgramUtilities.UpdateProgramSlots` also rebuilds the tinnitus slot list from persisted program objects' `TinitusMaskerVcAvailable` flag. Legacy configuration migration copies that same flag into foundation persistence.

This means the app can be told that a slot has tinnitus without acoustically measuring whether the aid actually has that feature. Matching setup to the aids' actual fitted programs is necessary for the UI to be truthful. Renaming a program alone is not the acoustic capability query.

## Fitting QR import: a second source

`WSA.Foundation.QrCode.dll`, `QrCodeConverter.ReadInformationAsync` state machine (`0x0600001e`), reads each `ProgramSlotConfigDto.TinitusMasker` value and persists it as `TinnitusVolumeControlAvailable(side, program slot, guid)`, carrying the imported checksum. The same method imports tinnitus step-size and decoupling configuration from `AppSmartRelayConfigDto`.

This establishes a code path for fitting data to supply the flags. It does **not** establish that this user's installation used a QR code or identify the user's saved values.

## Runtime: why the tinnitus tab appears in one program and not another

The inspected chain in `Component.RemoteControl.dll` is:

```text
TabControlViewModelHelper.IsTinnitusSupported
  -> TinnitusVolumeService.IsSupportedAsync() for current programs
  -> for each configured side: GetCurrentProgramIdAsync(side)
  -> IsSupportedAsync(programId, side)
     -> BluetoothConnected(side): Bluetooth tinnitus extension
     -> otherwise, AcousticAvailable: acoustic tinnitus extension
```

The per-side result array is reduced with `Any` for tab support; the predicate returns the boolean itself. Thus at least one supported configured side can make the tinnitus tab available. Separate slider code checks the results and decoupling state for independent adjustment; do not infer that both ears support tinnitus merely because the tab is present.

In `WSA.Foundation.Acoustic.dll`, `TinnitusExtension.IsSupportedAsync` state machine (`0x060001f2`):

1. Validates side and program ID.
2. Checks `Program.HasProgramAsync(side, programId)` and throws for a nonexistent program.
3. Converts the program ID to a slot, obtains that side's device GUID, constructs `TinnitusVolumeControlAvailable(side, slot, guid)`.
4. Reads `GetValueAvailabilityAsync<bool>` and returns `Parameter.Value`.

No hearing-aid capability exchange occurs in that method. The persistence checker reads its cache/configuration service with the key default as fallback; it also computes checksum freshness, but the acoustic caller above extracts the value directly. This distinction matters: saved acoustic capability is not a fresh measurement of aid state.

This mechanism is consistent with the previously accepted Universal-available / TV-unavailable UI observations. It does not reveal this user's exact stored map for all six programs.

## Bluetooth differs

In `WSA.Foundation.Bluetooth.dll`, `TinnitusExtension.IsSupportedAsync` state machine (`0x06000c3f`) also validates the program and constructs the per-device/side/slot tinnitus key, but passes it to `ParameterChecker.GetUpdatedConfigValue`.

That helper's state machine (`0x06000961`) obtains the hearing system's checksum for the side, reads the saved parameter, and returns it when up to date. Otherwise it invokes the hearing system's configuration updater and reads the parameter again. This proves a refresh mechanism through the connected hearing system; the detailed lower-level Bluetooth configuration exchange is outside this trace.

## What ties commands to the configured aids?

At startup, `HearingSystemInitializationService.SetAcousticHearingSystem` (`RTA.Core.dll`, state machine `0x060014eb`) checks the saved acoustic setup flag and gets stored acoustic IDs. Existing IDs are loaded through the acoustic factory and selected in the provider. The missing-ID branch uses saved left/right acoustic addresses to create a basic hearing system.

`PairingDataService.SaveBasicHearingSystem` (`0x0600158a`) creates a new GUID, constructs the side/address mapping, calls the acoustic factory and selects the resulting system. A locally generated GUID in this path is **not proof of a serial number read from a physical aid**.

In `WSA.Foundation.Acoustic.dll`, `Device` construction (`0x06000007`) stores the GUID, side and acoustic address, then computes:

```text
qualified address = byte((brand identifier << 4) | acoustic address)
```

`Device.WriteToneAsync` (`0x0600000b`) forwards the command and this qualified address to `IToneService.SendToneAsync`. It does not forward the GUID. The previously traced basic default is address zero; left/right stored addresses can differ. See the [battery trace](proto-request-trace-2026-10-14.md) for those defaults and the packet path.

The tinnitus write method (`0x060001f6`) converts and validates the requested level and sends a tinnitus command based on its position through the selected side's hearing system. It does not transmit the full capability map, device GUID, or a tinnitus program description in that call. Matching the currently active aid program remains necessary; an app-side selected-program record is not independent aid readback.

**Conclusion about exclusivity:** the app scopes controls to its configured hearing system and addresses outgoing tones accordingly. Receiver firmware determines which tones an aid accepts. This trace does not prove that another compatible aid using the same acoustic address would reject them, or that the default-zero address uniquely identifies this user's aids. It also does not explain the failed reconstructed battery playback by itself.

## Implications for HeySig

Keep an explicit per-side, per-program capability map and preserve its provenance (manual setup, fitting import, or connected-device refresh). Do not infer tinnitus support from user-visible program names. UI automation can continue to verify actual tab availability. A future direct acoustic controller needs both the correct destination/profile and a capability map matching the fitted aids; producing valid PCM alone supplies neither live capability discovery nor acknowledgement.

The unresolved user-specific facts are the exact saved capability flags, active brand/addresses, and why the reconstructed battery request has not yet produced an accepted aid response. No setup reset is required to establish the code paths above.

## Evidence and reproduction

[Method fingerprints](../fixtures/setup-capability-trace.json) identify eight original assemblies and twenty inspected method bodies without copying assemblies or full proprietary disassembly. Semantic findings above come from manual inspection of CIL, not execution of Signia code. Additional helper methods were inspected in the same assemblies.

With the pinned research dependencies available:

```sh
python3 research/acoustic/tools/trace_setup_capabilities.py /private/assemblies --output /tmp/setup-capability-trace.json
```

Tokens are version-specific. The tool guards method names and records assembly/method hashes; it does not automatically verify the semantic conclusions or inspect a phone.
