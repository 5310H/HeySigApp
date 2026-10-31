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
