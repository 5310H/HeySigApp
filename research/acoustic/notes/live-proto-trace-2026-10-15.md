# Working Signia battery request: live observation

October 15, 2026, 8:42 p.m. America/New_York. Pixel 9a, installed Signia process. Connected over the user's existing ADB connection; inspected the current battery panel and tapped **Check Left Battery Button** once. The user confirmed the usual left-aid battery indication for this exact test.

## Collected evidence

- A focused Signia-process log spanning the button press and completion.
- Android audio-service snapshots before/after and an AudioFlinger snapshot requested after the tap. The latter is diagnostic context, not a guaranteed snapshot during the short playback.
- Signia's AudioTrack stop log reports **30,316 frames delivered**.
- The audio service reports **44,100 Hz, mono, USAGE_MEDIA, CONTENT_TYPE_UNKNOWN**, playback started at 20:42:51.617 and stopped at 20:42:52.441. This 0.824-second lifecycle interval includes audio-stack timing; it is not the exact PCM duration.
- 30,316 frames at 44,100 Hz represents approximately **0.687438 seconds of PCM**, matching the reconstructed request's frame count. Matching length is not byte equality.
- Audio-service routing diagnostics list media output as the speaker. The observed player reports device ID 3; exact device routing should continue to be checked during any prototype comparison.
- Signia logs a track recreation after `setOutputDevice()` before playback. This is followed by successful playback and user-confirmed aid response; the warning alone is not an observed failure.

Raw diagnostics are retained under ignored `private/signia-live-20261015/`, since system audio dumps contain unrelated phone history. The [sanitized observation](../fixtures/live-proto-observation-2026-10-15.json) contains only the relevant facts.

## What this narrows down

Our reconstructed request agrees with a real, working Signia request on sample rate, channel count, and total frame count. The prototype already requests the built-in speaker and media usage. One observed difference is content type: Signia uses UNKNOWN; the prototype currently requests MUSIC. That could affect processing, but no causal conclusion or fix has been established.

The captured process log does not reveal the command byte, active acoustic address, generation frequencies, gain, or PCM samples. No function hook or PCM recording was obtained. The installed Signia app was previously found non-debuggable through standard `run-as`; Frida executables were not available in the local shell. This collection is a live audio/lifecycle trace, not instrumentation of every internal method.

## Next controlled comparison

Compare the Android prototype on this same phone, at the same media volume and phone position, against this accepted reference. Record its route, delivered frame count, profile, and user feedback. A content-type-matched variant is a bounded follow-up experiment if the baseline fails. Exact address and PCM comparison remain the strongest way to distinguish packet/profile differences; additional app access or an appropriate audio capture would be needed. A microphone recording can help compare acoustic frequencies/timing but cannot prove PCM byte equality.

No Signia setup, program, slider, phone volume, or Tasker configuration was deliberately changed by this trace. The battery panel was left open.
