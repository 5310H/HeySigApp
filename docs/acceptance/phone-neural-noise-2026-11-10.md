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

## November 11 physical-phone follow-up

Pushed code 62e2396 was rebuilt and upgraded in place on the Pixel phone. The
visible label was Build 62e2396 · Nov 11, 2026. All five pre-upgrade preference
files (core/fitting, noise, masking choices, Therapy, personal phrases) were
byte-identical immediately after installation.

All six native fixture severities and score/feature/ratio checks passed. Native
severities: development CLEAR/MODERATE/HEAVY; heldout CLEAR/UNKNOWN/HEAVY.
First-use UI and isolated saved-choice/recreation/stale-fitting checks passed.
An initial harness assumption that every fixture must reach the denoiser failed
on the expected UNKNOWN case; the test now checks its neural measurements
separately and retains all complete-pipeline severity/score assertions.

The silent lifecycle test exposed a startup race: its first tick stopped with
Automatic notification removed before Android registered the foreground
notification. A follow-up requests immediate notification display on API31+,
waits up to five seconds for registration, and forbids microphone capture until
the notification is present. Notification/permission disablement still stops
immediately; disappearance after first visibility stops immediately as before.
A timeout fails closed. Device validation of this fix is tracked below.

## Final November 11 device verification

Notification fix **7b51fe2** was committed and pushed before rebuilding the final
phone/test APKs. Both builds and phone lint passed; no lint errors, 31 existing
warnings. The final phone APK passed all 24 native-library hash and 16KB ELF/ZIP
alignment checks, active asset pins and retired-head/test-audio exclusion.
The phone was upgraded in place with no uninstall or data clearing.

The [screen-off log](data/phone-neural-noise-2026-11-10/phone-screen-off.txt)
records native/choice passes, actual microphone capture with the notification
present, screen-off Noisy dispatch, cooldown, sustained Universal return, no
duplicate playback and manual cancellation through the shared validated executor
with silent transport. `isolatedScreenOff=true`. The run then timed out at the
secured lock screen before its second explicit foreground start; that timeout
is retained and is not reported as an overall passing run.

After the owner unlocked the phone, a separately selectable notification-only
mode ran the same native/choice checks and an isolated explicit start, waited
for the notification, sent its Off action and verified the session stopped.
That [foreground run](data/phone-neural-noise-2026-11-10/phone-notification-off.txt)
passed. The split avoids requiring another phone lock/unlock cycle. The final
main-app code was unchanged between those two runs.

All five real preference files (core/fitting, noise, masking choices, Therapy and
personal phrases) remained byte-identical both after the final install and after
all tests. Native six-fixture separation/masking DSP CPU totaled about 2.47s in
the screen-off run and 2.41s in the notification-only run, excluding YAMNet.
These are fixture consistency observations, not sustained battery estimates.
Automatic is left Off after cleanup; no hearing-aid tones were emitted by these
tests. No actual aid reception, listening benefit, fresh blind accuracy, sustained
battery, regulatory or Play acceptance is claimed. The watch connected on retry
but was not updated in this phone-only scope.
