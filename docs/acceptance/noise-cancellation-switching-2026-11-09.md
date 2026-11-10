# Noise cancellation as program-switch evidence — November 9, 2026

The owner requested using noise cancellation to determine when Automatic switches.
Chirps, saved program roles and the existing shared executor remain unchanged.
This is analysis-only local spectral noise suppression, not anti-noise playback
or cleaned-audio streaming to hearing aids. No wearer calibration, enrollment,
examples, learned noise profile or learning period is introduced.

## Implementation and data flow

Shared Kotlin uses a 512-sample Hann-window FFT with a 256-sample hop on each
existing three-second 16kHz capture. The within-capture 20th-percentile bin power
estimates a temporary stationary floor. Wiener-style attenuation, bounded to
.05–1, supplies six scalar features: removed power fraction, speech-band retained
power, retained-envelope range and variation, floor flatness and active-frame
fraction. Spectral arrays are erased after analysis. No cleaned waveform is
reconstructed, played, stored or uploaded. No floor survives between captures.

The unchanged HSGMASK1 baseline is hash-pinned alongside an HSGMASK2 head using
those six additional features. The live detector requires matching severities
from both heads; otherwise it holds. They share YAMNet and are not independent
models. Matching evidence still passes the original fresh-sample confirmations,
cooldown, protected-program/manual/Therapy priority and duplicate-send executor.
Unassigned roles hold. Existing quiet, music, transient and invalid-evidence guards
remain. No preview or acceptance enable lock was added. No preferences/schema,
permissions, microphone schedule, service lifetime or transport changed.

## Development evaluation and actual tradeoff

The [comparison proof](data/noise-cancellation-2026-11-09/comparison.json) verifies
all 1,182 records, original 2,052 features and event scores are identical to the
previous experiment. Six new features come from the actual shared Kotlin
implementation, not a Python approximation. The same licensed LibriSpeech and
background sources and disjoint outer speaker/background partitions are reused.
This is a viewed, correlated regression set, not fresh blind acceptance.

Regularization, temperature and cancellation-feature scale are selected using
development-speaker folds only. Inner folds share backgrounds. The scale grid
1/4/16 was added after the first outer regression had been viewed. Selected:
C=.1, temperature=1, scale=16; scaling is folded into the fixed coefficients.
The [fit](data/noise-cancellation-2026-11-09/fit.json) records all candidates.

The candidate alone scored 341/576 (59.2%), below the original 356/576 (61.8%),
and reduced heavy-case correctness from 145/192 to 97/192. It is **not** used as a
standalone replacement. The final agreement policy was chosen after reviewing
those errors. Its recorded [decisions](data/noise-cancellation-2026-11-09/decisions.tsv)
and [summary](data/noise-cancellation-2026-11-09/summary.json) show:

| Heldout metric | Original | Agreement with noise suppression |
|---|---:|---:|
| Correct severity / all mixtures | 356/576 (61.8%) | 305/576 (53.0%) |
| Wrong accepted severities | 135/576 | 86/576 |
| Correct among non-uncertain estimates | 356/491 (72.5%) | 305/391 (78.0%) |
| Noisy/Very Noisy cross-confusions | 64/384 (16.7%) | 30/384 (7.8%) |
| Uncertain holds | 85/576 (14.8%) | 185/576 (32.1%) |
| Matched-loudness clear/buried pairs both correct | 119/192 (62.0%) | 78/192 (40.6%) |
| Heavy references correctly called heavy | 145/192 | 97/192 |

This trades fewer wrong proposals for fewer correct proposals and more holds.
It is **not an overall accuracy or clear/buried discrimination improvement**.
Four of fourteen nonquiet controls still falsely propose a change from Everyday;
six of fourteen from Noisy. Background-only and TV-surrogate errors remain.
Music/impulse controls hold. The quiet ambience surrogate still holds instead of
returning to Everyday. Repeated samples cannot cure persistent model errors.

Known mixture SNR is an experimental reference, not wearer intelligibility.
Stationary-floor estimates cannot identify a wanted talker, reliably remove
competing speech, or distinguish every TV voice. Digital gain/position proxies
are not actual phone-position tests. Broader recordings, background-disjoint
inner validation, real placements and a dedicated overlap/target-speech model
remain necessary before claiming more reliable listening assistance.

## Checks and device evidence

The full noise shell suite passed, including new same-loudness/different-ratio,
gain invariance, zero/short capture, stateless floor, versioned-head wiring and
agreement/disagreement regressions. Existing confirmation, cooldown, manual,
Therapy, stale/duplicate and PCM-cancellation checks passed. Python tool syntax
checks passed. Model/source hashes and native fixtures are pinned.

Final Android builds, new native consistency, upgrade/settings preservation and
device checks are recorded separately after the source commit. No new battery,
physical aid reception, clinical, regulatory or Google Play acceptance is claimed
by these corpus results. Existing acceptance flags remain false evidence records,
not enable locks. Automatic is live only when the wearer explicitly turns it On.

## Final build and device verification

Code revision **d48f349** was committed and pushed before final device APKs.
Both the Pixel 9a and Pixel Watch 3 were updated in place successfully to
`Build d48f349 · Nov 9, 2026`. Initial installation requests were declined;
installation proceeded only after the owner supplied the phone and watch ports
again. No uninstall or data clearing was used.

- Final shared tests: 35 passed. Full noise shell suite and added cancellation
  regressions passed. Phone, phone-test and watch debug APK builds passed.
- Phone/watch lint: zero errors; 31/18 warnings. The ARM host required the existing
  documented `-Pandroid.aapt2FromMavenOverride=/tmp/heysig-arm-tools/build-tools/aapt2`.
  The initial default x86 packaging tool failed to execute; the corrected build
  passed. No release/Play approval is implied.
- Final phone/watch APKs contain the expected pushed build identity. Both phone
  model assets and source-credit pins match; no PCM fixtures are packaged in the
  main phone APK. Native inventory and ELF/ZIP 16KB alignment checks passed:
  20 phone libraries, four watch libraries.
- All five checked phone preference files are byte-identical before/after the
  update: core/fitting, noise, speech-role choices, Therapy and personal phrases.
  The watch preference file is also byte-identical.
- Pixel instrumentation passed all six native fixtures, including both heads'
  agreement decisions and DSP feature checks. Expected severities:
  development CLEAR/MODERATE/HEAVY; heldout CLEAR/UNKNOWN/HEAVY. Native score and
  encoder consistency bounds passed. This verifies consistency, not accuracy.
- First-use, isolated explicit choice saving/recreation, unassigned choice and
  stale-fitting rejection checks passed. Instrumentation confirmed real fitting,
  saved user snapshot and six noise assignments/thresholds/timings were retained.
- This verification used recorded fixtures and UI checks, with live microphone,
  screen-off and hearing-aid playback tests disabled. No hearing-aid tones were
  sent. Automatic was left Off by the test cleanup. No new sustained battery or
  physical aid reception acceptance is claimed. The accuracy limitations and
  increased-hold tradeoff above remain unchanged.
