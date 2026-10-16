# Chromebook playback timeout correction — October 16, 2026

Target: Chromebook Android environment, ADB `emulator-5554`, model `staryu`. HeySig 0.4, development-signed build with optional silence-only instrumentation. Phone and watch were not changed.

The user reported “Playback timed out.” Android AudioTrack/AudioFlinger logs at 10:44:56 and 10:45:12 showed track recreation, `releaseBuffer` sequence mismatch, buffer timeout and `stop` with zero delivered frames. The old transport preloaded the entire 30,316-frame command before starting playback.

`SpeakerPlayback` now requests transient audio focus, starts streaming before writing data, uses bounded nonblocking chunks, handles focus loss/cancellation, and includes queued-byte/played-frame counts on timeout. A 2,205-frame (50 ms) zero tail follows the original 44.1 kHz command buffer to allow resampling to drain. The renderer, addresses and numeric command mappings are unchanged. No automatic retry was introduced.

The opt-in `PlaybackSmoke` instrumentation played two 30,316-frame zero-only buffers plus their silent tails through the built-in speaker route:

| Check | Result |
|---|---|
| Silent playback 1 | 30,693 frames reported at completion check, at least 30,316 required; 881 ms |
| Silent playback 2 | 30,693 frames reported at completion check, at least 30,316 required; 781 ms |
| Already-cancelled request | Rejected before playback |
| Audio-engine delivered-frame log | Both completed tracks reported all 32,521 queued frames delivered |
| Original failure pattern | Route recreation occurred before feeding data; no new queued-buffer sequence mismatch or buffer timeout |
| Packet regression | Frozen digests, 131,072 frames, mappings, inverse decode and invalid inputs passed |
| Build/packaging | Kotlin/D8, aligned APK and signature verification passed; version 0.4 installed |

This accepts native playback progress for two silent buffers. It does not prove the physical speaker frequency response, acoustic command integrity after the host audio path, or hearing-aid reception. No actual aid command was emitted by the diagnostic. The next physical check is an explicit left battery request, judged by the user's usual audible aid indication. Keep Chromebook host volume/routing in view; Android reports fixed media volume on this environment.

Build the optional diagnostic with `HEYSIG_AUDIO_SMOKE=1` and the normal Android build variables. The build adds instrumentation only when requested. Run it with `adb -s emulator-5554 shell am instrument -w com.heysig.batteryprototype/com.heysig.app.PlaybackSmoke`. It does not edit fitting or saved request state.

## User-confirmed acoustic reception

After version 0.4 was installed and HeySig reopened, the user reported “worked with voice playback.” Asked whether the aid also gave its usual battery indication, the user answered “yes.” This accepts the tested voice-triggered battery request from the Chromebook through speaker playback to an audible aid response. The preceding requested test was Check left battery; no recognition transcript or separate left/right event trace was collected, so do not infer both-ear acceptance or acceptance of every voice command. Other program/level commands, watch delivery and repeat reliability still require their own tests.
