# Primary-preserving Small Group monitor — November 1, 2026

This increment integrates a conservative Small Group specialist into the experimental phone/watch monitor. It fills only a complete primary abstention. **Every non-null primary estimate, including scores below .65, is preserved unchanged.** It does not enable real automatic commands or qualify six-way accuracy.

This policy is stricter than the [preceding offline experiment](environment-small-group-fallback-2026-11-01.md), which treated sub-.65 primary estimates as abstentions and could suppress conflicting accepted predictions. That experiment's twelve additional evaluation detections do not describe the runtime policy. The implemented policy adds four.

## Implementation and identity

`EnvironmentSceneFusion` in shared Kotlin accepts only a Small Group specialist result with score >=.65 when the primary environment is null. Other specialist labels and weak scores are ignored. The unchanged switching reducer still applies the user's configured minimum, stability, cooldown, override and sleep priority. No manual program mapping, fitting, aid protocol or executor changes are made.

Both heads use the same 2048 pooled YAMNet features and original speech/music/novelty guards. The adapter evaluates the specialist only after primary abstention. Encoder inference, bounded foreground capture, cancellation and PCM erasure remain unchanged. There is no new runtime dependency, permission, background service or audio transmission.

| Component | SHA-256 |
|---|---|
| Encoder | `b2fc8fc3ff9c960bde60b5fea71335d4cb2a35c4d1b011c3a80927fd64d8a2e4` |
| Preserved primary head | `0500150700fc75a8fc4b4c59f0cd284fea0b8d61d9b5fe6de074b24358233bfd` |
| Small Group head, 98,392 bytes | `b32f1124b31e4961f52c1153fa8220d3cc10b374292dfdb199b7bcb852e56be8` |
| Combined monitoring profile | `8d1cb1d1d8137accce10091a3c364dc99b1193aa998e9da5243d96732a403ece` |

The profile is SHA-256 of `HSGENV2\n`, encoder hash plus newline, primary hash plus newline, specialist hash plus newline and `primary-preserving-small-group-v1\n`. Phone/watch now send and require that combined identity. Legacy primary-only identities fail before monitoring results are consumed; update both form factors together. Ordinary manual controls retain their existing behavior.

The specialist is the curated development fit described in the [window-curation report](environment-window-curation-2026-11-01.md). Its other five labels never enter runtime decisions. Both applications package the expanded creator/source/license/transformation credits, including Ryding's CC BY 3.0 source and the additional CC BY 4.0 AMI excerpts/annotation derivatives. No source recordings or word text ship in main assets. APK/AAB validators pin both heads and attribution notices and reject test PCM assets in main artifacts.

## Actual shared decisions

Both heads and the composition execute in Kotlin against the same frozen feature/event evidence. Existing score thresholds and guards are unchanged.

| Evidence | Previous primary correct / wrong / abstain | Implemented monitor correct / wrong / abstain |
|---|---:|---:|
| Curated evaluation: 130 variants, 30 recordings, 13 groups | 31 / 0 / 99 | 35 / 0 / 95 |
| Historical evaluation: 124 variants, 11 recordings, 11 groups | 33 / 0 / 91 | 33 / 0 / 91 |
| Curated development: 210 variants, 37 recordings, 22 groups | 125 / 10 / 75 | 129 / 10 / 71 |

The four evaluation additions are correlated Small Group views from two AMI sessions. All existing accepted decisions and all nonzero-score uncertain primary estimates remain identical in the scalar reports. Existing ten development errors remain; this change does not correct them. Conversation remains 0 correct/0 wrong/10 abstentions in curated evaluation. Sources were previously viewed and references remain provisional; neither these results nor the restricted fallback establish independent far-field or six-class acceptance. Both automatic gates stay false.

Evidence: [curated decisions](data/primary-preserving-fallback-curated-2026-11-01.tsv), [curated summary](data/primary-preserving-fallback-curated-2026-11-01.json), [historical decisions](data/primary-preserving-fallback-historical-2026-11-01.tsv), [historical summary](data/primary-preserving-fallback-historical-2026-11-01.json), [hash provenance](data/primary-preserving-fallback-provenance-2026-11-01.json).

## Reproduction and checks

Use the [existing feature pipeline](../../shared/src/commonTest/fixtures/environment/scene-head/README.md) with the curated manifest and cached licensed sources, then run:

```sh
bash tools/report-scene-head.sh \
  audioAndroid/src/main/assets/environment/yamnet.tflite \
  audioAndroid/src/main/assets/environment/scene-head.bin \
  shared/src/commonTest/fixtures/environment/scene-head/curated-corpus.json \
  /tmp/curated-evidence.bin /tmp/curated-evidence.tsv /tmp/monitor-decisions.tsv \
  --small-group-head audioAndroid/src/main/assets/environment/small-group-head.bin
python3 tools/environment/make-small-group-regression.py \
  --cache /tmp/heysig-environment-corpus --decision-report /tmp/monitor-decisions.tsv \
  --output /tmp/fallback-regression
bash tools/check-noise.sh
python3 tools/test-scene-corpus.py
```

Shared tests cover every primary/specialist class pairing, preservation of .5/.649999/.65/.99 primary scores, the exact specialist threshold, weak/unknown specialist results and old/missing/mismatched profiles. Corpus checks pin both assets, derive the combined profile, verify source licensing/credits and the five-second test fixture. Existing six native fixtures remain byte-identical; one additional licensed **development** recording exercises complete primary abstention and specialist output. It is test-only, never played, and not accuracy acceptance. Instrumentation asserts the specialist explanation as well as its expected native score, so an accidental primary-only path cannot satisfy that regression.

## Build and physical phone verification

Both debug apps and both instrumentation APKs built successfully. All 32 shared Gradle tests passed with zero failures/errors/skips. Both debug lint tasks passed with zero errors/fatal issues; existing warning counts remain 26 phone and 18 watch. Both APKs passed the pinned model/attribution/native inventory and 16 KB ELF/ZIP alignment checks; no test PCM is packaged in main APK assets. The phone required-DEX-class check passed. Seven corpus checks and four report checks passed. The current reporter reproduced the saved combined decisions exactly, and the new PCM/metadata fixture regenerated byte-for-byte.

Both Gradle `verifyEnvironmentModel` tasks also passed after adding explicit specialist/head-notice/profile-notice input hashes. These verification-only build-script additions do not alter the tested APK bytes.

Final phone APK SHA-256: `f8787ae0670e3801d95827c7b4f0dcd01f38ddf5412ac4ea6876560ef39e4442`.

Final watch APK SHA-256: `304bad1cd30055ebb39a686292fd7f54038a352cafed5b8dbb88c9763fd157db`.

The phone was updated **in place** and passed the command-free instrumentation suite. All 25 existing preference files remained byte-identical. The original six licensed native regressions retained their reference scores. The additional fallback fixture used the specialist path and matched its host score exactly: `.7081885333731266`. No audio was played or aid command sent.

Authorized foreground microphone captures at 3/5/10 seconds completed in 3327/5255/10294 ms, with result ages of 68/73/108 ms. Stop, access loss and leaving-screen cancellation passed. There was no fresh user environment reference, so these are capture/lifecycle results, not accuracy evidence. The diagnostic round-layout checks at 192dp and font scales 1/2 passed on the phone; they do not substitute for a physical Wear test.

Synthetic model benchmarks used 141/68/64 ms wall and 127/67/63 ms thread CPU for 3/5/10-second inputs. Process PSS was 114900 KB before, 164481 KB after model checks and 139512 KB at completion, including instrumentation overhead. These short measurements do not establish sustained memory or battery behavior. [Scalar phone evidence](data/primary-preserving-fallback-phone-2026-11-01.json) omits live microphone labels/events and all settings contents. The full diagnostic report remains local.

At this checkpoint, the watch APK was built but not installed or physically tested because the watch was unavailable. The subsequent [phone/watch update](phone-watch-update-2026-11-01.md) confirms installation and launch of the newer development candidates on both devices with preferences preserved. Watch/aid/endurance acceptance and stronger independent scene references remain required for completion of the requested automatic feature. Both automatic gates remain false. No signed release, Play submission or approval is claimed.
