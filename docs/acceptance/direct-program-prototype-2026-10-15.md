# Direct acoustic program prototype

**Latest result:** direct Sleep slot 2 and TV slot 5 are accepted with qualified address **12**, address-XOR enabled and the startup waveform profile. User-confirmed two/five beeps establish reception and TV restoration. The address-zero failures below are retained as history. See [capture/decode evidence](../../research/acoustic/notes/decoded-program-capture-2026-10-15.md).

October 15, 2026. User selected direct acoustic control, with Sleep (slot 2) as the intended live target. This is an experimental extension of the existing Android battery prototype, not the full replacement app.

- Added explicit program slots 1–6, mapping internal IDs 0–5 to commands 0x40–0x45.
- Added address-side selection, guarded speaker playback and persistent last-completed-request display. The current aid program is explicitly unknown; no acoustic readback is claimed.
- Android build and APK signing verification passed. Updated APK installed on the Pixel and new controls inspected.
- Six program waveforms at address zero/startup profile passed independent-reference and original-CIL comparisons; seven invalid-input checks passed. Separate checks rejected program0/program7. Full 96-case suite was not completed; the initial expanded run stopped at the oracle's battery-only admission guard, then a broader rerun was interrupted in favor of the bounded six-program check.
- [Program waveform evidence](../../research/acoustic/fixtures/program-waveform-validation.json) records tested inputs and hashes.
- A slot-1 send attempt was blocked by the prototype's DND guard before playback. No completed program request was recorded.
- User changed the desired target to **Sleep, slot 2**, then confirmed DND off. A slot-2 attempt failed the prototype's AudioTrack initialization check before playback. Inspection found that static tracks were incorrectly required to be INITIALIZED before the initial PCM write; they are in NO_STATIC_DATA at that point. The check now accepts a created static track, writes PCM, and requires INITIALIZED afterward. Corrected build/retest is pending. Address zero is a test hypothesis, not a recovered active address.

No direct program reception has been accepted on the aids. No platform-specific unmute preamble is included. The user confirmed they can distinguish program changes by listening; actual feedback for Sleep remains pending. The last known starting aid program is TV (slot 5); preserve the working Signia path for restoration after the user's observation.

The user subsequently clarified that aid program acknowledgement is an audible beep count equal to the slot number. Expect two beeps for Sleep and five for TV. This supersedes the earlier no-program-acknowledgement assumption; the current test still needs its own reported count.

## Corrected static-playback attempt and positive control

The corrected build installed successfully. Sleep slot 2, qualified address zero and startup profile/divisor 1.2 completed prototype playback. Android reports speaker device ID 3, 44,100 Hz mono, media MUSIC content type, with speaker media volume 17/25. The user reported **no beeps or program change**: direct reception failed despite playback completion.

Signia itself was then used for the same Sleep selection. The user confirmed **two beeps and Sleep**, followed by **five beeps and TV** when restoring slot 5. Final Signia screen shows TV, volume 8. This supplies a live program-command positive control as well as restoration evidence.

Further inspection found `TonePlayingParameters.SetDefaultValues` selects transfer mode 1 (streaming), and `AudioTrackGenerator` builds attributes with `SetLegacyStreamType`. The prototype has been changed from static/MUSIC to streaming/legacy media attributes for the next bounded comparison. Build/retest pending; no improved reception is claimed. The command/address/profile are unchanged. [Sanitized live evidence](../../research/acoustic/fixtures/direct-program-live-2026-10-15.json).

## Streaming comparison result

The streaming build passed, installed, and completed one Sleep slot-2 playback. Android reports media usage, UNKNOWN content type, flags 0xA00 (DEEP_BUFFER + MUTE_HAPTIC), 44,100 Hz mono and device ID 3, matching the observed Signia attributes. Playback lifecycle was 21:14:24.526–21:14:25.333 America/New_York. The user again reported **no beeps or program change**. Matching these audio settings did not establish reception; no success is claimed.

TV was restored through Signia before this attempt, with user-confirmed five beeps. The user reported no change from the failed direct attempt. The prototype remains open; no further commands were sent. The next unresolved gate is obtaining Signia's active destination/profile or actual output samples. Do not treat address-zero defaults as established by these failed tests.
