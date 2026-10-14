# Signia acoustic command validation — October 14, 2026

REA was used successfully on the Signia app installed on the Pixel 9a. Command definitions and the encoder's checksum/control-bit prefix have been validated. Full pulse encoding, waveform generation, playback and the active address/profile remain separate work. No phone setting, installed app, Tasker control or hearing-aid state was changed, and no command was transmitted.

The user confirms that **only battery checks give an audible response**. Silence after other commands is not a failure signal. Further validation should compare Signia's generated audio with a reconstructed signal, rather than require acknowledgements that those commands do not provide.

## App and tool identity

The installed package is `com.signia.rta`, version **2.8.0.18214**, version code **1821400000**, minimum SDK 29 and target SDK 36. All four installed APKs were pulled read-only and hashed. The ARM64 split has exactly the same SHA-256 as the artifact examined in the October 9 report: `a3bcec0ba14a32b66b28c41c74812e168687bd17ed632d003fadcb0f544e8a5d`. The installed base APK differs from that historical mirror artifact; no reason for that difference or fresh signature-validation claim is inferred.

The assembly store was unpacked into 423 structurally validated PE assemblies. Originals and full disassembly stay in `/tmp/signia-chirp-analysis-20261014`, outside git. The store layout follows the [official .NET Android assembly-store documentation](https://github.com/dotnet/android/blob/main/Documentation/project-docs/AssemblyStores.md), including the observed 64-bit index width.

REA **6.5.0** ran using verified Node **22.23.3** for Linux ARM64, installed only under `/tmp/rea-chirp-tools`. Agent configuration and MCP registrations were left untouched. REA's `inspect-managed-artifact` and `inspect-managed-members` inspected `WSA.Foundation.Audio.dll` and `WSA.Foundation.Acoustic.dll`. Their reported metadata/member coverage was complete; that is static metadata coverage, not exhaustive runtime behavior. See [REA managed analysis](https://github.com/5310H/rea/blob/main/docs/managed-code-analysis.md).

Pinned `dnfile 0.18.0` and `dncil 1.0.2` supplied full numeric CIL decoding and constant-table inspection. All five selected encoder-method CIL hashes independently agree with REA. Hashes, RVAs, APK identities, command definitions and comparison digests are recorded in [the evidence JSON](../../../docs/acceptance/evidence/HeySig3-chirp-validation-2026-10-14.json).

## Verified protocol definitions

These are **protocol receiver positions**, not a verified mapping from UI slider labels or named program slots.

| Definition | Absolute positions | Increase / decrease |
| --- | --- | --- |
| Standard volume | 0–15 → `0x10`–`0x1F` | `0x52` / `0x53` |
| Sound balance | 0–15 → `0x20`–`0x2F` | `0x54` / `0x55` |
| Tinnitus volume | 0–15 → `0x30`–`0x3F` | `0x58` / `0x59` |
| Program | 0–5 → `0x40`–`0x45` | `0x56` / `0x57` |
| Receiver mute / unmute | `0x5A` / `0x5B` | — |
| Standby / wake | `0x50` / `0x51` | — |
| Battery request, left / right | `0x80` / `0x81` | — |

The battery-side branch was checked against the original method instructions and the shared `Side` enum (`Left=0`, `Right=1`). A standby constant's existence does not prove the user's aid supports it. Other paths such as CROS have their own command definitions; the table is the standard path, not a claim about every device family.

The standard volume setter still converts and validates the supplied volume. Its mute state emits `0x5A`; unmute can prepend `0x5B` before the absolute position command. Thus a UI value of zero must not be equated automatically with command `0x10`.

## Encoder comparison

`CalculateCrc8` processes one command byte MSB first with polynomial **0x07**, initial zero, no reflection and no final XOR. When the profile's address-XOR option is enabled, `GenerateControlTone` changes the checksum to:

```text
crc8(command) XOR address XOR 0xAA
```

The two eight-bit streams interleave MSB-first command/checksum bits. Data A uses the upper nibbles, command then checksum; Data B uses the lower nibbles, checksum then command. The address is a byte and is incorporated by the enabled XOR branch; it is not a separately appended address byte in this prefix.

An independent restricted interpreter evaluated the original DLL's four pure utility methods and `GenerateControlTone` up to its `PulseEncoder.EncodePulse` call. It validates the profile getter as a plain stored boolean and stops **before** pulse encoding or modulation. It never loads the DLL into a .NET runtime or invokes Android/native code. Unsupported operations fail rather than being approximated.

The readable model matched **all 256 CRC inputs** and **131,072 command/address/mode cases** (256 command bytes × 256 address bytes × two profile modes), including the original address-XOR branch. This validates those mathematical inputs, not the legitimacy or device support of every possible command byte.

## Repeatable checks

The offline model in `tools/sig3_chirp_packet.py` requires an explicit address and profile mode. It returns checksum/control bits only and contains no transmitter or waveform generator.

```sh
python3 tests/check_sig3_chirp_packet.py
```

That regression compares the model with frozen original-CIL digests, polynomial division, inverse bit decoding, command ranges and invalid inputs. Fresh original-DLL comparisons require the caller's extracted assemblies and the pinned Python libraries:

```sh
python3 tools/verify_sig3_chirp_dll.py /private/assemblies/WSA.Foundation.Audio.dll --output /tmp/packet-proof.json
python3 tools/verify_sig3_chirp_commands.py /private/assemblies --output /tmp/command-proof.json
```

## Remaining validation

1. Resolve the active acoustic address, profile parameters and UI-to-protocol conversions for the user's configuration.
2. Complete pulse ordering, waveform shaping, timing and PCM conversion using the installed encoder.
3. Capture Signia-generated command audio and compare the reconstructed signal. Preserve battery feedback as the only expected audible response.
4. Evaluate direct playback and app-cache behavior only after that comparison. No working direct chirp sender or locked-screen control is claimed here.
