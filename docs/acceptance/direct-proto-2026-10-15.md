# Direct left/right battery checks accepted

October 15, 2026. Pixel 9a, existing Kotlin prototype with streaming AudioTrack and legacy media attributes. User requested battery testing before noise automation.

**Both direct audible battery checks passed user acceptance using qualified address 12 and address-XOR enabled.** No program change was requested; TV was the last user-confirmed program.

## Profile and evidence

- Sample rate 44,100 Hz; center 15,360 Hz; spacing 512 Hz; bit-duration parameter 0.0625 seconds; gain divisor 1.2.
- Left command 0x80; right command 0x81.
- Raw playback duplication captures use 48,000 Hz stereo PCM16LE, without microphone/video capture. This is postmix output, not original generator PCM.
- Five relevant captured requests each decode to 32/32 expected frequency slots and uniquely to qualified address 12/XOR enabled.
- The aligned first 24,000 mono-averaged postmix samples from the initial Signia-right and HeySig-right captures are exactly equal. This comparison does not claim full original-buffer equality or identical physical speaker output on every attempt.

## Live sequence

1. HeySig left request with address 12: user confirmed the usual left battery indication.
2. Captured Signia right request: decoded command 0x81/address 12, establishing the right request's destination independently of the program tests.
3. HeySig right request with address 12: playback/decoded output passed, but the user reported no indication.
4. Repeated Signia's own right button: user confirmed the usual right indication.
5. Requested that the phone remain in the position that worked for Signia, then retried HeySig right: user confirmed the usual right indication. Physical positioning was not independently measured, so it is not established as the cause of the first miss.

During retry preparation the UI shifted and an obsolete coordinate hit a label rather than the right button. That capture is excluded from request acceptance. The final retry located the enabled `CHECK RIGHT` button from fresh UI XML and captured the actual request; its decoder independently verified command 0x81/address 12.

[Sanitized evidence](../../research/acoustic/fixtures/direct-proto-acceptance-2026-10-15.json) retains decoded sequences, capture hashes, comparison scope and both failed/successful feedback. Raw WAVs and helper logs remain in ignored `private/signal-capture-20261015/`.

## Scope

This accepts audible battery requests for this user's two aids and current configuration. It does not retrieve a numeric percentage or establish unattended transmission reliability. Address 12 is user-specific, not a generic Signia default. The prototype currently requires explicit address/profile entry after recreation; retain these settings in the documented setup until durable configuration is implemented.
