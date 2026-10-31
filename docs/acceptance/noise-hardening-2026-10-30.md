# Noise hardening and device acceptance — October 30, 2026

Follow-up to [the initial implementation checks](automatic-program-switching-2026-10-30.md).
The user authorized bounded microphone tests on both devices after disclosure
that PCM remains in memory, is discarded, and no hearing-aid commands are sent.
The user described the environment as a quiet room. No raw audio/transcript was
retained. Existing fitting, calibration and preferences are preserved.

## Changes and source checks

- Generic Speech/Narration no longer implies Conversation. The unchanged 24
  recording-score fixtures now yield 17 abstentions, including all restaurant
  and babble cases. This is correction of an unjustified inference, not proof
  of successful six-environment classification or independent held-out accuracy.
- A separately testable audio worker clears PCM on all completion paths, rejects
  concurrent samples, supports immediate restart from completion, and suppresses
  results after cancellation. Model inference checks cancellation between frames.
  An invalid native model signature also closes its interpreter.
- Watch samples cannot refresh a session after its heartbeat deadline, including
  the interval before the periodic timeout checker runs.
- The actual watch monitoring layout uses shared watch styling and is measured
  at 192dp round, font scales 1 and 2, with >=48dp targets, complete action text
  and scroll reachability. This does not replace TalkBack or physical layout review.
- Debug-only phone/watch acceptance components have no fitting, preferences,
  companion connection or aid executor access. Release validators reject those
  components and now account for pinned LiteRT/model assets in both form factors.
  Both debug and release runtime dependency configurations are hash-checked.

The standard `tools/check-noise.sh` passed, including cancellation/access-loss,
buffer erasure, restart, capture-failure and expired-heartbeat regressions.
Gradle shared tests passed: 30 tests in 8 suites, zero failures/errors/skips.
Both debug APKs built and passed pinned native/model/hash and >=16 KB ELF/ZIP
alignment checks. The phone required-DEX-class check and Python compilation of
validation/acceptance tools passed. No signed release artifacts are claimed.
Both AndroidTest APKs compiled and both debug lint tasks passed with no
errors/fatal issues. Phone lint reported 26 warnings and Wear lint 20; five new
warnings per module concern untranslated text in the debug-only test screen.

## Pixel 9a

The isolated `NoiseAcceptance` runner passed on the user-provided
`192.168.69.130:39139` endpoint. The driver installed both APKs in place and
verified all 25 pre-existing preference files remained byte-identical afterward.
No new microphone permission was needed; the existing grant was preserved.

| PCM / capture window | Synthetic model wall / thread CPU | Live capture + inference wall | Live inference age | Live result |
|---|---|---|---|---|
| 3 seconds | 63 / 60 ms | 3406 ms | 48 ms | Uncertain, score 0 |
| 5 seconds | 33 / 33 ms | 5218 ms | 62 ms | Uncertain, score 0 |
| 10 seconds | 58 / 57 ms | 10285 ms | 104 ms | Uncertain, score 0 |

Synthetic PCM benchmarks include model construction/disposal; they are not
accuracy tests and produce no sound. All live inference ages met the production
five-second freshness limit. The quiet-room reference did **not** produce a
positive Quiet classification, so this is not quiet-classifier acceptance.

Process PSS was 115079 KB before benchmarking, 162481 KB after model benchmarks
and 147700 KB at completion. These include instrumentation and test-layout
overhead; they are neither production-only RAM measurements nor battery evidence.

Real microphone Stop, capture-access interruption and leaving-screen tests
passed. The worker became idle; cancelled/paused samples returned no result,
and access interruption returned failure. Interruption was induced through the
capture-access guard; this does not claim OS permission-revocation, privacy-switch
or actual speech-provider interruption testing. The round/large-font layout
checks also passed on this device using isolated watch configurations.

Phone APK SHA-256:
`aa6b3c22aecd8c84ca46e896d9ba62fe3f22bf1f3def5fcf6d94616ef66d0127`.
The scalar runner output remains in `/tmp/heysig-phone-noise-acceptance-2026-10-30.txt`.

## Pixel Watch 3 and remaining acceptance

Watch test APKs compiled. The connection dropped during builds; the first
acceptance attempt could not fingerprint settings and stopped before installation.
The latest attempted endpoint `192.168.69.190:42881` subsequently refused
connections. A current endpoint is required to install/run these new watch tests.

Full completion remains blocked on reliable, independently validated quiet,
conversation and group/context recognition, held-out six-environment evidence,
watch runtime checks, real Data Layer interruption/reconnect/no-replay checks,
OS microphone denial/revocation/privacy switches, voice conflicts, accessibility,
battery/endurance and physical hearing-aid reception/priority acceptance.
`EnvironmentReleaseGate.CLASSIFIER_ACCEPTANCE_PASSED` and
`NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED` remain false. The quiet-room result
and conservative abstentions do not justify enabling real Automatic dispatch.
