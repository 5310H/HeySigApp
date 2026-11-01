# Restricted Small Group fallback — November 1, 2026

The Small Group-only combination is implemented as an **offline Kotlin research policy**, tested and evaluated. It adds twelve correct Small Group decisions on the curated evaluation windows, without the full candidate's four wrong labels. It is **not promoted to either application**: Conversation coverage remains zero, the improvement does not extend to the historical evaluation set, and classifier/physical acceptance remains incomplete.

## Fixed policy and scope

The installed primary head is `0500150700fc75a8fc4b4c59f0cd284fea0b8d61d9b5fe6de074b24358233bfd`; the curated specialist is `b32f1124b31e4961f52c1153fa8220d3cc10b374292dfdb199b7bcb852e56be8`. Both component reports come from the actual shared Kotlin classifier and its existing event/novelty guards.

The policy preserves an accepted primary prediction unless a confident Small Group specialist prediction contradicts it, in which case it abstains. When the primary abstains, only a confident Small Group prediction may fill the gap. Other specialist predictions are ignored. Accepted primary confidence is preserved; fallback confidence is the specialist score. These scores are not calibrated probabilities or independent votes. Selection of Small Group follows its development-source-held-out diagnostic (33 correct predictions across four source groups, zero false positives); this is not an independently validated guarantee.

The experiment uses a fixed .65 threshold. It does not implement the application's configurable .5–1 minimum-confidence semantics. A future runtime proposal needs an explicit policy for that setting, compatible phone/watch classifier profiles, asset/license pins, native regression fixtures, APK/lint checks and device acceptance. None is silently bypassed by this experiment.

## Results

| Evidence | Installed primary correct / wrong / abstain | Restricted combination correct / wrong / abstain |
|---|---:|---:|
| Curated evaluation, 130 variants / 30 recordings / 13 groups | 31 / 0 / 99 | 43 / 0 / 87 |
| Historical expanded evaluation, 124 variants / 11 recordings / 11 groups | 33 / 0 / 91 | 33 / 0 / 91 |
| Curated development, 210 variants / 37 recordings / 22 groups | 125 / 10 / 75 | 134 / 10 / 66 |
| Same development with source-excluded specialist folds | 125 / 10 / 75 | 133 / 10 / 67 |

All twelve evaluation additions are previously uncertain Small Group windows. No existing accepted evaluation label changes. Combined curated per-class correct / wrong / abstain: Quiet 6/0/8, Conversation 0/0/10, Small Group 28/0/14, Restaurant 2/0/28, Large Group 2/0/18, Non-Speech Noise 5/0/9.

The ten development errors already occur in the installed primary; adding this specialist does not correct them. The mixed fold diagnostic excludes each group from specialist fitting only. The installed primary still includes its original development sources; this is **not full-combination source-held-out validation**. Hyperparameters were selected using specialist development groups. The reported gains do not establish generalization to new sources.

All evaluation sources were viewed before this follow-up. Overlapping windows/gain variants, shared recording sources and AMI headset mixes are correlated; word-activity proxies are not independent human scene labels or far-field phone/watch acceptance. No new unseen evaluation or physical accuracy result is claimed.

## Reproduction and verification

First build the shared head reporter using the [existing pipeline](../../shared/src/commonTest/fixtures/environment/scene-head/README.md), then:

```sh
bash tools/check-scene-specialist.sh
python3 tools/test-scene-specialist.py
java -cp shared/build/scene-specialist-check.jar:shared/build/scene-head-report.jar \
  com.heysig.tools.SceneSpecialistReportKt \
  docs/acceptance/data/window-curation-baseline-2026-11-01.tsv \
  docs/acceptance/data/window-curation-candidate-2026-11-01.tsv \
  /tmp/reproduced-small-group-fallback.tsv
cmp /tmp/reproduced-small-group-fallback.tsv \
  docs/acceptance/data/small-group-fallback-curated-2026-11-01.tsv
```

The same reporter combines the saved historical baseline and historical component, or the ordered primary development report and source-excluded specialist report. Development reordering uses the exact `(clip, seconds, gain, offsetSeconds)` key, verifies identical key sets and changes no component decisions. Reports reject mismatched sources/windows, duplicate variants, inconsistent outcomes, unsupported windows, invalid scores and acceptance below .65 before writing output.

Kotlin checks cover all six specialist labels, preservation, conflict abstention, threshold boundaries, confidence preservation and invalid numeric inputs. Three report-integrity tests pass, including rejection cases. No Android/runtime source changes require new APK builds in this increment. The prior phone installation and build evidence remain applicable; no microphone, fitting write or aid command occurred.

Evidence: [curated report](data/small-group-fallback-curated-2026-11-01.tsv), [curated summary](data/small-group-fallback-curated-2026-11-01.json), [historical component](data/small-group-fallback-historical-component-2026-11-01.tsv), [historical report](data/small-group-fallback-historical-2026-11-01.tsv), [historical summary](data/small-group-fallback-historical-2026-11-01.json), [ordered primary](data/small-group-fallback-primary-cv-order-2026-11-01.tsv), [mixed fold report](data/small-group-fallback-mixed-cv-2026-11-01.tsv), [mixed fold summary](data/small-group-fallback-mixed-cv-2026-11-01.json), [hash provenance](data/small-group-fallback-provenance-2026-11-01.json).

## Remaining completion requirements

The requested six-way automatic feature remains incomplete. Classifier acceptance still needs independent, representative evidence for the weak Conversation/Restaurant/Large Group distinctions, and resolution of existing wrong labels. Physical watch microphone/Data Layer acceptance, endurance and actual aid confirmation/priority acceptance remain outstanding. The watch was reported unavailable; no watch result is fabricated. Both automatic release gates remain false. This research increment does not constitute Google Play submission readiness.

Later implementation uses a [stricter primary-preserving policy](environment-primary-preserving-fallback-2026-11-01.md) in the experimental monitor. This document's fixed-threshold policy remains an offline experiment and must not be used as the runtime reference.
