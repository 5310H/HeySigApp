# Experimental Kotlin battery-request generator

## Numeric volume requests

CLI requests `volume0` through `volume15` and `ProtoRequest.volumePcm(level, address, profile)` use the traced standard range: command `0x1f - level`. Levels 8/9 were confirmed against live Signia output; 4/8/9 waveforms were compared with the original encoder. This does not implement special receiver mute/unmute sequences for other fitting ranges. Run `check_proto_waveform.py --volume-smoke` for those three levels at address 12/startup profile, optionally with `--original-dll` and `--evidence`.

## Program requests

The renderer also accepts `program1` through `program6` in place of `left`/`right` in the command line. These map to internal program IDs 0–5 and commands `0x40`–`0x45`. The public Kotlin entry point is `ProtoRequest.programPcm(slot, address, profile)` with one-based slots. Fitted slot names must be established separately. This single-tone experiment omits platform-specific unmute preambles and cannot read the current aid program.

The waveform regression now covers all six program commands as well as both battery commands, against independent rendering and optionally the original CIL interpreter. Historical twenty-four-case battery evidence remains unchanged.

For a bounded program check using the startup profile and address-zero test preset:

```sh
python3 research/acoustic/tests/check_proto_waveform.py --program-smoke --original-dll /private/assemblies/WSA.Foundation.Audio.dll --evidence /tmp/program-waveforms.json
```

Without `--program-smoke`, the full suite checks 96 request/address/profile combinations. These comparisons establish encoder agreement, not aid reception or the active address.

This standalone JVM prototype renders a **single battery or program control tone** to mono 16-bit little-endian WAV. It does not play audio, connect to a phone, read aid state, or start an Android service. It requires no Gradle project or Android SDK.

The reconstruction follows the locally extracted Signia 2.8.0.18214 `WSA.Foundation.Audio` encoder: verified command/checksum interleaving, four one-hot frequency slots per bit pair, 50-sample cosine edges, gain division, two leading pulse lengths, twelve added pulse lengths in total, and truncation to signed 16-bit PCM. Frequency phase restarts at each pulse. Exposed commands are battery `0x80`/`0x81` and programs `0x40`–`0x45`.

Build:

```sh
kotlinc research/acoustic/kotlin/ProtoRequest.kt -include-runtime -d /tmp/heysig-proto.jar
```

Generate an **offline research example**, using extracted constructor defaults and a synthetic address of zero:

```sh
java -jar /tmp/heysig-proto.jar left 0 on 44100 15360 512 0.0625 4 /tmp/battery-left-example.wav
```

Arguments are side, fully qualified byte address (brand identifier plus configured aid address), address-XOR on/off, sample rate, center frequency, frequency spacing, bit duration in seconds, gain divisor, and output file. All must be explicit. Existing output files are refused. The example is not the user's verified configuration and must not be treated as a playback fixture.

Verification:

```sh
python3 research/acoustic/tests/check_packet.py
python3 research/acoustic/tests/check_proto_waveform.py
```

The latter compiles Kotlin and compares twenty-four rendered WAV files against an independent Python rendering of the inspected instruction sequence, with at most one PCM least-significant-bit difference for platform math rounding. It also checks seven invalid configurations. This does **not** execute the original waveform methods or compare recorded Signia output.

The [static battery call-path trace](../notes/proto-request-trace-2026-10-14.md) shows that the inspected single-side battery path sends one control tone with sniff-suspend disabled. Before on-aid testing, resolve the active fully qualified address and audio profile and compare a Signia-generated reference with the reconstructed request. Real output comparison and device playback remain pending. Confirm the actual playback route and phone audio guards when integrating Android playback. Only the user's audible aid indication can confirm the battery response; generating a WAV cannot establish reception or battery level.

The eight-hour Tasker trial was not accessed or modified during this work.

## Original waveform instruction validation

The bounded interpreter in `tools/waveform_oracle.py` now interprets the selected original DLL waveform methods, including the constants constructor, pulse encoder, frequency calculation, shaper, modulation and final PCM conversion. BCL arrays and math calls are modeled in Python; no .NET runtime, audio or native code is invoked.

```sh
python3 research/acoustic/tests/check_proto_waveform.py --original-dll /private/assemblies/WSA.Foundation.Audio.dll --evidence /tmp/proto-waveform-validation.json
```

[Recorded evidence](../fixtures/proto-waveform-validation.json): all twenty-four Kotlin/original-CIL comparisons matched PCM bytes exactly for the tested profiles/addresses. This does not yet compare a live Signia capture or prove speaker reception.
