# Direct Sound Sharpness and tinnitus implementation

**Accepted on aids:** HeySig Sound Sharpness 0 → +4 → 0 and tinnitus 0 → 4 → 0. The user confirmed both changes and both restorations. Qualified address 12, XOR enabled, accepted startup profile. Universal remains selected; volume 8 was sent before the tests.

October 16, 2026. User requested the same direct control approach after confirming volume worked. Reconnected the Pixel at the user-supplied wireless-debugging port. Fresh Signia screen showed Universal, remembered volume 8, with both controls available.

## Signia reference captures

| Family | Fresh UI sequence | Decoded command/address |
|---|---|---|
| Sound Sharpness | 0 → 4 → 0 | 0x24 / 0x20, address 12 |
| Tinnitus | 0 → 2 → 0 | 0x3d / 0x3f, address 12 |

Each capture matches 32/32 frequency slots, address-XOR enabled, under the accepted profile. No competing automation was inferred; the earlier TV → Sleep change was explicitly explained by the user as their own action.

Reference mapping for this fitting/platform: signed Sound Sharpness −8..7 becomes `0x20 + (value & 15)`; tinnitus 0..15 becomes `0x3f - value`. The Sound Sharpness platform variant is resolved for the tested setup by the live +4 and neutral captures. Do not assume this orientation for every other Signia platform.

[Decoded references](../../research/acoustic/fixtures/balance-tinnitus-reference-2026-10-16.json) retain capture hashes and tone slots. Raw WAVs are in ignored `private/signal-capture-20261016/`.

## Implementation

Added explicit Sound Sharpness and tinnitus targets to the existing app and standalone renderer, sharing the accepted address/profile and guarded streaming player. Completion persists the last request without claiming current aid readback. Live aid acceptance remains pending until the user's observations are recorded below. The app does not discover tinnitus capability automatically; use a program where fitting/Signia setup enables it.

## Completed validation and live acceptance

- Android build/signature verification passed; APK installed on the reconnected Pixel.
- Six original-CIL waveform comparisons passed: balance −4/0/+4, tinnitus 0/2/4. Four out-of-range requests were rejected. [Waveform evidence](../../research/acoustic/fixtures/balance-tinnitus-waveform-validation-2026-10-16.json).
- HeySig sent volume 8 as the baseline. The prior persisted last-volume request showed 15, consistent with the user's volume-testing report; the agent did not conduct the earlier intended 8 → 15 sequence itself.
- HeySig Sound Sharpness +4 completed; user answered “Yes, it changed.” Sent Sound Sharpness 0 afterward.
- HeySig tinnitus 4 completed in Universal; user answered “Yes, it changed.” Sent tinnitus 0 afterward.
- User confirmed both Sound Sharpness and tinnitus sounded restored after the zero requests.
- Final prototype fields record completed volume 8, balance 0, tinnitus 0. No program change was requested by HeySig during these direct tests; Universal remains the baseline program.

These direct listening tests used freshly located buttons and completed-request UI checks. Their PCM was not captured; the Signia reference captures and offline original-CIL comparisons are separate evidence. One preparation capture contained silence after a shifted-screen tap; it is not accepted as a transmission. Ordinary commands were then issued from verified button locations.

[Sanitized live acceptance](../../research/acoustic/fixtures/direct-balance-tinnitus-acceptance-2026-10-16.json) records user feedback and limits. The final UI snapshot and raw reference WAVs are in ignored `private/signal-capture-20261016/`.

This accepts these levels for the user's fitting, address and Universal program. It does not establish every endpoint, firmware orientation, independent-ear routing, per-program tinnitus availability or unattended reliability. Stored request fields are not queried aid state.
