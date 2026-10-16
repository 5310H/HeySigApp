# Working direct programs: address recovered from playback capture

October 15, 2026, Pixel 9a. The user authorized capturing Signia's working signal. This resolves the program-command address-zero test failure for the tested configuration.

## Result

**Qualified address 12 (0x0c), with address-XOR enabled**, decoded uniquely from both Signia Sleep and TV output. HeySig then sent those same program commands with address 12. The user confirmed **two beeps and Sleep**, followed by **five beeps and TV restoration**.

Commands are Sleep slot 2 = 0x41 and TV slot 5 = 0x44. Each of the four captured signals (two Signia, two HeySig) matches all 32 expected frequency slots for its command and address 12. Address 0 does not match the working Signia sequence. This is evidence for the tested system's program destination; do not generalize address 12 to other users or assume separate left/right battery addresses have been established.

## Capture method

The Pixel's shell screenrecord has no audio option. Used the official [scrcpy 5.0.1 server release](https://github.com/Genymobile/scrcpy/releases/tag/v5.0.1), SHA-256 `764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`, matching the release asset's published digest.

The temporary server ran under the already authorized ADB shell using audio source `playback`, duplication enabled, raw codec, video/control disabled, and raw-stream metadata disabled. A short-lived local ADB forward received PCM. Capture sockets and forwarding were closed after each bounded recording. No root, Signia modification, database extraction, microphone or video capture was used. The downloaded helper remains in the phone's shell temporary directory for reproducibility.

Version-matched [AudioConfig.java](https://github.com/Genymobile/scrcpy/blob/v5.0.1/server/src/main/java/com/genymobile/scrcpy/audio/AudioConfig.java) defines 48,000 Hz stereo PCM16LE. [AudioRawRecorder.java](https://github.com/Genymobile/scrcpy/blob/v5.0.1/server/src/main/java/com/genymobile/scrcpy/audio/AudioRawRecorder.java) and [Options.java](https://github.com/Genymobile/scrcpy/blob/v5.0.1/server/src/main/java/com/genymobile/scrcpy/Options.java) define the selected raw stream/options. Capture is Android's mixed output, potentially resampled/scaled, not a byte-for-byte copy of Signia's 44,100 Hz generator buffer.

A three-second baseline was all zero. Four roughly four-second captures each included one explicit button press after approximately one second. Signia playback remained audible to the aids: the user confirmed two beeps and Sleep for the captured Signia request. Signia TV restoration was captured next; UI showed TV/8 afterward. The two later HeySig captures have explicit user-confirmed Sleep/TV outcomes.

## Decoding

The known profile has source rate 44,100 Hz, 689 samples per pulse and nominal frequencies 14,592 / 15,104 / 15,616 / 16,128 Hz. At the capture rate, each pulse spans approximately 749.932 samples.

The decoder finds signal onset, examines a 500-sample interior window per pulse, and chooses among the four frequency bases. It rejects silence, incomplete windows and weak frequency separation. It compares all 32 observed positions with the packet model for the known command, all 256 candidate addresses, and both address-XOR branches. A result requires one exact candidate. All four captures yielded address 12 with XOR enabled; the minimum winning/runner-up frequency magnitude ratio was greater than 18 for the Signia captures. The silent baseline was rejected.

This is a **known-profile decode**, not blind discovery of arbitrary carrier frequencies or pulse timing. It does not establish original PCM byte equality. The subsequent successful on-aid tests provide separate reception evidence.

[Sanitized decoded evidence](../fixtures/decoded-program-captures-2026-10-15.json) contains capture hashes, frequency slots, profile inputs, decoded candidates and user acceptance. Raw WAVs, capture metadata and server logs remain in ignored `private/signal-capture-20261015/`; the capture helper is retained there for reproducibility.

Reproduce using Python with numpy:

```sh
python3 research/acoustic/tools/decode_playback_capture.py /private/signia-sleep.wav --command 0x41 --source-rate 44100 --pulse-samples 689 --frequencies 14592 15104 15616 16128 --output /tmp/decoded-sleep.json
```

## Current-state meaning

The user explicitly clarified that **neither app can query the aids in this acoustic setup**. Signia displays remembered selection; HeySig's last-completed-request field serves the same basic purpose. Beep-count feedback separately confirms the tested changes. Neither app can automatically discover an out-of-band program change through this acoustic path.

## Remaining work

Persist the selected profile/address and fitted program-name mapping in a reviewed setup flow. Validate other slots and any platform-specific mute transitions as needed. Battery, slider commands and independent-ear destinations require their own direct acceptance; program success does not certify them. Keep address 12 user-specific rather than making it a generic default.
