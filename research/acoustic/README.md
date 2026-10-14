# Acoustic command research

Keep command/address/profile investigation, pulse/waveform reconstruction and comparison tooling here. The existing HeySig analysis verified command definitions and checksum/control bits, including 131,072 original-code comparisons. Full waveforms, the active device configuration and direct playback are still unvalidated.

`fixtures/` is for synthetic or explicitly approved reference material. Keep extracted proprietary assemblies, APKs, credentials and private device captures in the ignored root `private/` directory. No transmitter is implemented in this scaffold.

## Existing research included

- [Installed-app validation](notes/chirp-validation-2026-10-14.md): command definitions, checksum/profile branch and validation limits.
- `tools/sig3_chirp_packet.py`: offline checksum/control-bit model; no waveform generator or transmitter.
- `tools/verify_sig3_chirp_dll.py` and `tools/verify_sig3_chirp_commands.py`: read-only checks against caller-supplied extracted assemblies; optional dependencies are pinned in `tools/requirements-research.txt`.
- `fixtures/chirp-validation-evidence.json`: original-method hashes and frozen comparison digests.

Run the dependency-free offline regression from the repository root:

```sh
python3 research/acoustic/tests/check_packet.py
```

The next research gate is resolving the active address/profile and matching a reconstructed waveform to Signia output. A battery-request playback test follows waveform validation; program commands have no expected audible acknowledgement.
