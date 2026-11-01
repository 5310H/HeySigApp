# Six-scene head development — October 31, 2026

Automatic switching is **not accepted**. Both existing release gates remain false. This work replaces the inadequate event-label mapping in the phone/watch inference path with a small supervised scene head over the existing YAMNet embedding. It adds no Android runtime dependency and sends no hearing-aid commands during diagnostics.

The encoder only exposes an existing embedding output; weights/operators are unchanged. The head is 98,392 bytes. Phone/watch exchange its pinned profile and reject missing/mismatched profiles before monitoring results can be consumed. All inference decisions remain shared Kotlin. Capture stays bounded, foreground-only and cancellable; PCM and temporary waveform buffers are cleared. Original fitting/settings and prior evidence are retained.

The [frozen corpus and reproduction instructions](../../shared/src/commonTest/fixtures/environment/scene-head/README.md) contain 28 references, with 26 usable sources: 17 development recordings from 15 source groups and nine validation recordings from nine groups. Freesound creators and AMI participants/session identities are separated across partitions. Two sub-three-second sources are excluded. Labels are provisional source descriptions, not independently reviewed microphone annotations. AMI headset mixes and a synthetic crowd training source limit domain relevance. Quiet references are not measured SPL.

Regularization C=10 and temperature=.5 were selected by development-only source-group cross-validation log loss. Windows, durations and gains are correlated and do not create independent samples. Shared Kotlin supplies every decision below, including the unchanged .65 switching threshold, novelty, ambiguity, music and speech guards.

| Scene | Development correct/wrong/abstain | Validation correct/wrong/abstain |
|---|---:|---:|
| Quiet | 16 / 0 / 0 | 6 / 0 / 8 |
| Conversation | 15 / 0 / 1 | 0 / 0 / 8 |
| Small Group | 29 / 0 / 23 | 18 / 0 / 18 |
| Restaurant | 14 / 0 / 0 | 0 / 0 / 16 |
| Large Group | 2 / 0 / 20 | 2 / 0 / 18 |
| Non-Speech Noise | 44 / 0 / 2 | 5 / 0 / 9 |

Validation totals: **31 correct, zero wrong, 77 abstentions across 108 correlated variants**. This does not qualify six-scene accuracy: Conversation and Restaurant have no accepted independent examples, coverage is poor and the corpus is small. Forced top-1 labels are not app decisions and must not be presented as switching accuracy. Lowering thresholds to force answers is unsupported.

Per-window decisions and fit metadata are preserved in [scalar decisions](data/environment-scene-head-2026-10-31.tsv) and [fit metadata](data/environment-scene-head-fit-2026-10-31.json). No raw user audio/transcripts are retained. Public-source PCM instrumentation fixtures are explicitly licensed, test-only development regressions. Their success cannot establish held-out accuracy, microphone acceptance, watch layout acceptance or battery endurance.

Remaining acceptance: independently reviewed representative recordings for all six scenes, physical phone/watch microphone and transport integration, accessible physical watch UI, override/sleep/cooldown scenarios and sustained battery/memory tests. The watch remains unavailable. This development work is not Play submission approval.

A same-input refit selected the same C/temperature and all 274 shared decisions. Twelve stored floats differed by at most 2.98e-8, so training is not claimed bit-for-bit reproducible. The runtime uses the original pinned head; no unreviewed refit replaces it. Derived encoder and test fixture manifest regeneration matched exactly. Four new corpus integrity tests, four legacy corpus tests and the standalone shared noise suite passed.

Both debug application and both instrumentation APKs built successfully. All 32 Gradle shared tests passed with zero failures/errors/skips. The final privacy disclosure addition passed the follow-up builds and all 32 shared tests. Both debug lint tasks passed; final artifact hashes and device results are recorded below.

## Final build and phone device verification

Verification recorded across October 31–November 1, 2026. The corpus/fit date remains October 31.

Final phone APK SHA-256: `78714655f7f41d559a30b2d8b99e777e4731f5008a1bd7625908ff250e5d55bc`.

Final watch APK SHA-256: `9adc37f3f6ce68aa863430fb272453b3be2bbded48b0a68418ddb965b9003a1f`.

Both final debug APKs passed pinned encoder/head/native inventory and 16 KB ELF/ZIP alignment validation; test recordings are absent from main artifacts. The phone APK also passed its required DEX-class check. Lint had zero errors/fatal issues, with 26 phone and 18 watch warnings (unchanged counts). All 32 shared Gradle tests passed after the final privacy update. No signed release or Play acceptance is claimed.

An in-place Pixel 9a update passed the command-free instrumentation suite, including all six licensed prerecorded development fixtures through the real native encoder and shared head. Result scores matched independent scalar references; per-fixture inference took 12–39 ms. These are training/development regressions, not held-out accuracy. Native synthetic 3/5/10-second benchmarks used 149/163/68 ms wall and 142/158/67 ms thread CPU. Process PSS was 110869 KB before, 159034 KB after model checks and 133017 KB at completion, including instrumentation overhead; this is not endurance evidence.

The previously authorized 3/5/10-second foreground microphone windows passed, taking 3436/5257/10278 ms including capture, with inference-result ages 64/71/100 ms. All abstained. There was no fresh user environment reference, so no scene-accuracy conclusion is drawn. Stop, guarded access loss and leaving-screen cancellation passed. The 192dp round/font-scale 1/2 layout checks passed on the phone runtime; they do not replace physical Wear accessibility testing. All 25 existing preference files were byte-identical after installation/testing. No aid commands were sent and no raw microphone recording/transcript was retained. Detailed scalar output remains local at `/tmp/heysig-phone-scene-head-2026-10-31.txt`.

The watch APK is built but not installed or physically accepted because the watch remains unavailable. Classifier coverage and watch/aid/endurance acceptance remain outstanding. Both automatic dispatch gates remain false.
