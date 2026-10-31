# Noise diagnostics — October 31, 2026

Automatic program switching is **not complete or accepted**. Both classifier and
physical acceptance gates remain false. No hearing-aid commands were sent during
these diagnostics. Existing fitting/settings were preserved.

## Phone

The user authorized bounded local microphone checks and supplied the phone
endpoint ending in 40481. Their current reference was “Mixed speech or background
noise.” The isolated debug acceptance runner passed Stop, guarded access loss,
foreground pause cancellation, synthetic model execution, freshness, and the
192dp round/font-scale 1/2 layout checks. This is mechanical acceptance, not
recognition accuracy, accessibility service testing, or endurance testing.

All 25 existing preference files were byte-identical after in-place installation.
Tested phone APK SHA-256:
`0041715611b96abb3bba2125fefc19c36cf498d3d4fe9321d67dfa9f63eb4650`.

| Window | Capture + inference wall | Result age | Mean Speech | Mean Conversation | Decision |
|---|---|---|---|---|---|
| 3 s | 3539 ms | 57 ms | 0.934375 | 0.003906 | Uncertain |
| 5 s | 5260 ms | 68 ms | 0.522135 | 0.002170 | Uncertain |
| 10 s | 10318 ms | 112 ms | 0.596834 | 0.001645 | Uncertain |

Mean crowd, chatter and babble scores were zero in all three windows. Scores are
model activations, not calibrated environment probabilities. Generic Speech is
insufficient to infer a conversation, number of speakers, or restaurant.

Synthetic model wall/thread CPU times were 98/52, 32/31 and 54/53 ms for the
3/5/10-second inputs. Process PSS was 113031 KB before, 158438 KB after model
benchmarks and 146727 KB at completion, including instrumentation overhead.
These numbers do not establish production battery or sustained memory behavior.
Raw audio/transcripts were not retained. The debug screen discloses coarse event
scores; ordinary monitoring has no score observer. Scalar test output was saved
locally at `/tmp/heysig-phone-noise-diagnostics-2026-10-31.txt`.

## Recording regression probe

The [pinned corpus](../../shared/src/commonTest/fixtures/environment/corpus/README.md)
contains seven public CC0 previews, reviewed against source-page licenses. Six
are development recordings; one separate creator/source is held out for Large
Group only. All class references are provisional creator descriptions.

The actual shared Kotlin mapper, at its unchanged 0.65 switching threshold,
produced **0 correct, 4 wrong Quiet, and 36 abstentions** over 40 duration/gain
variants. Three false Quiet results came from the lower-gain couple recording;
one came from a lower-gain crowd recording. Variants are not independent samples.
The corpus cannot establish six-class generalization, but these failures rule
out claiming acceptance. Lowering context thresholds is not a supported fix.
Two other restaurant source downloads returned HTTP 403 and were excluded.

Reproduction records model/source/manifest/decoded-PCM hashes, decoder version,
full scalar event scores and the actual shared classifier decisions. A Python
model environment is developer-only and introduces no app dependency. No source
recordings are packaged with either application.

## Quiet verification correction

Quiet candidates now require agreement with a second digital-gain inference
view. Shared Kotlin computes a target RMS of 0.05 full scale, capped at 16x gain
and 0.95 peak headroom, without attenuating the original. No audio is played,
transferred, saved or used as calibrated SPL. Zero PCM supplies no acoustic
evidence and cannot establish Quiet. Disagreement abstains; the second view
cannot promote another environment or increase the original confidence. The
existing cancellation token is checked before, between and after model frames.
Only Quiet candidates with gain above 1 need additional native inference.

The reproducible PCM16 corpus rerun has **0 correct, 0 wrong, 40 abstentions**
at the unchanged switching threshold. All four known false Quiet decisions are
rejected; six room-tone variants remain below switching confidence and are never
promoted. This fixes a demonstrated failure, not six-class recognition. Pinned
paired scalar fixtures run in the standard shared regression suite. The original
results above are retained as historical evidence, not overwritten acceptance.

Both Android debug lint tasks for the preceding diagnostic commit passed with
no errors. Builds and device results for the Quiet correction are recorded
separately when complete; the phone APK hash above refers to the earlier build.

The Quiet correction built both debug and both instrumentation APKs. All 31
Gradle shared tests passed without failures/errors/skips, as did the standalone
shared noise checks and four corpus integrity/leakage checks. Both APKs passed
the pinned native/model and 16 KB ELF/ZIP alignment checks.

The corrected phone build passed command-free model, cancellation, zero-input
and round/large-font layout checks with the microphone off. All 25 existing
preference files remained byte-identical. APK SHA-256:
`09680422449d8e4526825db2d35c515afef945d4d8d7af079c42a0d48e8a74e7`.
Synthetic 3/5/10-second wall/thread CPU times were 86/66, 34/33 and 60/60 ms.
Process PSS was 120081 KB before, 158438 KB after model benchmarks and 170974 KB
at completion, including instrumentation/extra interpreter overhead. This does
not establish a sustained memory leak or production battery performance.
The scalar report is `/tmp/heysig-phone-quiet-verification-2026-10-31.txt`.
No repeat physical microphone or watch acceptance is claimed for this correction.

Both corrected debug lint tasks also passed with no errors. Existing warnings
remain: 26 on phone and 18 on watch, including untranslated debug-only test text.

## Alternative pretrained-model probe

A host-only evaluation used the 17,843,130-byte pretrained tinyCLAP PhiNet student
with its original 44.1 kHz reference spectrogram/log-mel preprocessing and six
fixed text descriptions. No fitting, microphone samples or user conversations
were used. Text vectors came from the official 2022 CLAP text encoder; no new
model was trained. [Scalar results and provenance](data/tinyclap-zero-shot-2026-10-31.json)
and a [reproducer](../../tools/environment/README.md) are retained.

Forced cosine top-1 matched 17/40 provisional references: Quiet 6/6,
Conversation 6/6, Small Group 1/6, Restaurant 4/4, Large Group 0/12 and
Non-Speech Noise 0/6. The twelve Large Group variants are two recordings, not
twelve independent sessions. The score probe failed to distinguish group size
and the vacuum recording. It supplies neither a qualified replacement nor
calibrated switching confidence; changing text prompts after these observations
would be another development experiment, not fresh held-out evidence.

Host audio preprocessing/inference took approximately 453–4735 ms per case,
excluding text-encoder preparation and model loading. These are not Android or
Wear measurements and cannot be compared directly to the phone timings above.
The student/source licenses are Apache-2.0; the Microsoft teacher/text weights
use MS-PL as identified upstream. Nothing from this probe was added to app
dependencies/assets, and no phone/watch runtime or battery qualification is claimed.

## Remaining work

A suitable environment classifier still needs representative, independently
reviewed recordings and creator/session-separated evaluation across all six
classes. The current generic AudioSet event scores do not reliably encode these
contexts. Any replacement model or learned classifier head needs license/runtime,
size, latency, memory and physical phone/watch validation; an overfit head on
these seven clips would not resolve that requirement.

The user reported the watch unavailable. No new watch installation or physical
microphone acceptance is claimed. Calibration, confirmed/cooldown behavior,
override/sleep priority and real physical switching acceptance must pass before
enabling automatic dispatch. No Play release/submission approval is claimed.
