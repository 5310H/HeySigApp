# Acoustic command research

Keep command/address/profile investigation, pulse/waveform reconstruction and comparison tooling here. The existing HeySig analysis verified command definitions and checksum/control bits, including 131,072 original-code comparisons. Full waveforms, the active device configuration and direct playback are still unvalidated.

`fixtures/` is for synthetic or explicitly approved reference material. Keep extracted proprietary assemblies, APKs, credentials and private device captures in the ignored root `private/` directory. No transmitter is implemented in this scaffold. An experimental offline Kotlin battery-control-tone renderer is now available; complete request sequencing and playback remain unvalidated.

## Existing research included

- [Direct Sound Sharpness/tinnitus acceptance](../../docs/acceptance/direct-balance-tinnitus-2026-10-16.md): user-confirmed changes/restoration, live Signia mapping and six waveform checks.
- [Direct left/right battery acceptance](../../docs/acceptance/direct-proto-2026-10-15.md): decoded address 12, user-confirmed indications, and retained first-right-test failure.
- [Working direct programs and decoded address](notes/decoded-program-capture-2026-10-15.md): actual Signia/HeySig playback captures, address 12, and accepted Sleep/TV commands.
- [App startup trace](notes/app-startup-trace-2026-10-15.md): fresh-process launch, saved setup loading, audio initialization, and observed absence of startup playback.
- [Slider read/write/storage trace](notes/slider-control-trace-2026-10-15.md): volume, Sound Sharpness and tinnitus, including acoustic state and command construction.
- [Current program and changes](notes/program-control-trace-2026-10-15.md): live TV → Universal → TV trace and the distinction between saved acoustic state and Bluetooth readback.
- [Live working battery trace](notes/live-proto-trace-2026-10-15.md): user-confirmed response, actual sample rate/frame count, and remaining capture limits.
- [Setup and tinnitus capabilities](notes/setup-capabilities-2026-10-15.md): manual/fitting sources, per-device/ear/program lookup, Bluetooth refresh, and acoustic addressing limits.
- [Installed-app validation](notes/chirp-validation-2026-10-14.md): command definitions, checksum/profile branch and validation limits.
- `tools/sig3_chirp_packet.py`: offline checksum/control-bit model; no waveform generator or transmitter.
- `tools/verify_sig3_chirp_dll.py` and `tools/verify_sig3_chirp_commands.py`: read-only checks against caller-supplied extracted assemblies; optional dependencies are pinned in `tools/requirements-research.txt`.
- `fixtures/chirp-validation-evidence.json`: original-method hashes and frozen comparison digests.

Run the dependency-free offline regression from the repository root:

```sh
python3 research/acoustic/tests/check_packet.py
```

The next research gate is resolving the active address/profile and matching a reconstructed waveform to Signia output. The user clarified on October 15 that program changes produce a beep count equal to the slot; use explicit user feedback to accept direct program reception. Earlier battery-only-feedback statements are superseded for this setup.

## Kotlin battery prototype

See [build instructions and validation limits](kotlin/README.md). The standalone generator produces left/right battery WAV files with explicit profile/address inputs. It does not play them. Run `python3 research/acoustic/tests/check_proto_waveform.py` for cross-language rendering checks.

The [battery request trace](notes/proto-request-trace-2026-10-14.md) resolves the inspected single-side sequence and qualified-address construction. Active phone configuration and captured-output comparison remain pending.
