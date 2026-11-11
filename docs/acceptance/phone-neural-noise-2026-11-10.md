# Phone neural noise replacement — November 10, 2026

The owner directed replacement of the earlier approach and completion of the
phone path. The phone's normal Automatic now uses HSGMASK1 speech/event evidence
plus aligned RNNoise retained/residual measurements and fixed shared Kotlin bands.
The retired HSGMASK2 asset is moved to third_party/masking/legacy-cancellation-head.txt
for historical reproduction, outside application assets. Legacy six-level saved
assignments and historical acceptance evidence are retained. Universal is the
visible quiet/clear role; its persisted `everyday` key remains compatible.

## Processing and limits

Three-second PCM windows are processed locally in memory. The paired reference
and denoised output use the same filter/delay path. The first 200ms of recurrent
settling is excluded. Speech-weighted retained/removed power yields an estimated
ratio; removed sound can contain speech and retained sound can contain competing
voices. This is not target-speaker SNR, calibrated acoustic dB or intelligibility.
The denoiser resets each capture. No enrollment, stored profile, extra capture,
recording, transcript, upload or cleaned-audio playback is introduced.

Development-derived boundaries are -4/+8dB with a 2dB uncertainty margin:
heavy <= -6, moderate -2 through +6, clear >= +10. Values between those bands hold.
Baseline speech evidence must agree. Quiet retains its separate strict guard.
Fresh checks, saved cooldown, manual/Therapy priority, duplicate suppression and
shared validated transport are unchanged. No new enable/acceptance lock is added.

## Existing regression evidence

Copied scalar evidence from the existing native-resampler experiment is in
[data/phone-neural-noise-2026-11-10](data/phone-neural-noise-2026-11-10).
It uses the previously viewed correlated corpus, not fresh blind field data.
The fixed bands were fit on development mixtures only. These results are not
new phone measurements and do not establish clinical or physical acceptance.

| Heldout mixtures (576) | Earlier spectral agreement | Neural agreement |
|---|---:|---:|
| Correct | 305 | 335 |
| Wrong accepted | 86 | 23 |
| Uncertain holds | 185 | 218 |
| Correct among accepted | 78.0% | 93.6% |
| Moderate/heavy cross-confusions | 30 | 0 |

Moderate coverage remains weak: 48/192 correct, 138 holds and six incorrectly
clear. Heavy: 125/192 correct, 50 holds and 17 incorrectly clear. The 23 wrong
mixture decisions are in overlapping speech. Four of fourteen nonquiet controls
still change incorrectly from Universal and six from Noisy, including TV
surrogates. Quiet surrogate handling is not real-world quiet acceptance.
Retained/residual versus known-source ratio MAE is 3.41dB, with 90th-percentile
absolute error 9.43dB; it must not be represented as a precise acoustic meter.

## Validation status

Implementation/build/device checks are tracked in the
[working checklist](../phone-noise-completion-checklist.md).
No physical phone upgrade, aid reception, sustained battery, real placement,
listening benefit or Play approval is asserted by this report.

November 10 checks so far: full noise shell suite passed; model/notice hash pins
passed; Python tools compile; `git diff --check` passed. Recompiled
SeparationDecisionReport against the current shared noise sources and reproduced
all saved neural decision rows byte-for-byte from the recorded scalar measurements
and original baseline evidence. This rechecks policy reproducibility, not a new
audio inference or accuracy evaluation.

Phone validation APK: 24 pinned native libraries pass ELF and APK ZIP 16KB
alignment. Active masking/separation assets match pinned hashes; retired head
and masking fixture audio are absent from the main APK. Gradle shared tests:
35 passed, zero failures/errors/skips.

Phone and phone-test debug APK builds and Android lint passed on November 10.
Lint issue counts: {'Warning': 31}. Existing warnings remain; no release approval is implied.
