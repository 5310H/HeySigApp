# Per-window reference curation — November 1, 2026

The requested per-window reference work is implemented and tested. The curated-data classifier improves Small Group coverage on reserved participants, but the **full candidate is not promoted** because it introduces four wrong evaluation decisions. The installed/packaged phone/watch model, saved fitting/settings and both automatic gates remain unchanged. No device microphone or aid command is used in this work.

## Frozen curation protocol

The [plan](../../shared/src/commonTest/fixtures/environment/scene-head/curation-plan.json) was fixed before feature extraction/model scoring. It selects the earliest eligible integer-second windows, trying 10 seconds, then 5, then 3, at most two per duration per meeting. Each selected window requires:

- At least three distinct annotated speakers, each contributing at least 0.5 seconds of word intervals.
- At least 40% word-activity coverage, with at most 20% overlapping word activity.
- Five-second spacing between selected windows; reserved regions around the previous 120–150-second excerpts are excluded.

The plan includes four development meetings (ES2002a/ES2003a/ES2004a/ES2005a) and four evaluation meetings (IS1000a/TS3003a/IS1001a/TS3005a). Participant identities cannot cross partitions. IS1001a and TS3005a contribute eight participants not present in earlier experiments or fitting. Their windows were reserved before inference. The previously viewed participant groups remain separate regression evidence.

The rule yields **38 excerpts**: 16 ten-second, 14 five-second and eight three-second windows. Seventeen excerpts are development; 21 are evaluation, including eleven from the two new participant groups. Missing qualifying windows are left missing. Selection does not pad, loop or stretch audio and consults no classifier outputs.

These references are stronger word-activity proxies for short multi-speaker samples. They still require acoustic/scene review: word alignment is not voice activity, room occupancy or complete listening-scene truth. AMI headset mixes differ from far-field phone/watch audio. The four old context-labelled AMI excerpts remain preserved in historical manifests/evidence and are not used as training references in this experiment. Other FSD sources retain their original provisional creator descriptions.

## Reproduction and data handling

The [timestamp fixture](../../shared/src/commonTest/fixtures/environment/scene-head/curation-word-timings.json) stores numeric intervals, public source URLs, hashes, CC BY 4.0 credit and transformations. The participant metadata mapping and all 32 XML source hashes/extractions were verified. Optional network verification receives public XML in memory, inspects only timing attributes and discards text; no word text is printed or saved. No user audio or transcript is collected.

The [selected windows](../../shared/src/commonTest/fixtures/environment/scene-head/curated-windows.json) and [69-source corpus](../../shared/src/commonTest/fixtures/environment/scene-head/curated-corpus.json) reproduce byte-for-byte from the frozen inputs. Audio downloads use bounded HTTP ranges for only the selected 3/5/10-second excerpts. Recordings stay in the developer cache. A three-second independent re-download matched its frozen WAV hash; all other source-cache hashes passed. Two original sub-three-second FSD clips remain excluded, yielding 67 usable recordings.

Feature extraction now honors explicit annotated windows. A ten-second Small Group reference does **not** silently create three-/five-second Small Group references. Truncated annotated windows, duplicate/invalid windows and inconsistent excerpt dimensions are rejected. All 264 existing non-AMI feature/event arrays are byte-identical after this extraction change. The 340 total duration/gain variants remain correlated, weighted by source group during fitting. Test fixture JSON/data is outside application asset source sets.

## Actual shared Kotlin decisions

Regularization C=10 and temperature=.5 were selected using development-only source-group cross-validation, with the same .65 switching threshold and existing contradiction/novelty guards. No evaluation recording fits coefficients or selects hyperparameters. Both models run through the actual shared Kotlin classifier.

| Evaluation | Shipped head correct / wrong / abstain | Curated candidate correct / wrong / abstain |
|---|---:|---:|
| Previously viewed sources/participants (108 variants) | 21 / 0 / 87 | 27 / 4 / 77 |
| Reserved new participant groups (22 variants) | 10 / 0 / 12 | 17 / 0 / 5 |
| Combined evaluation (130 variants) | 31 / 0 / 99 | 44 / 4 / 82 |

All 22 reserved variants are Small Group references: eleven excerpts, two sessions, eight participants, two gain views. They are not 22 independent examples and do not validate the other five classes. They have now been viewed and cannot be called unseen in later tuning.

On combined Small Group evaluation, the candidate has 28 correct/0 wrong/14 abstentions versus 16/0/26 for the shipped head. The full candidate's remaining errors are three Large Group → Restaurant and one Quiet → Non-Speech Noise. Those regressions block replacing the installed head.

Source-held-out development diagnostics produce **91 correct, 32 wrong, 87 abstentions** over 210 correlated variants from 37 recordings/22 groups. Fitted-source results are 167/0/43 and do not establish generalization. Small Group predictions in the source-held-out diagnostic include 33 correct results from four source groups and no false positives; other errors remain. Hyperparameters were chosen using these development groups, so these diagnostics are not nested unbiased validation or physical acceptance.

Evidence: [baseline decisions](data/window-curation-baseline-2026-11-01.tsv), [candidate decisions](data/window-curation-candidate-2026-11-01.tsv), [baseline summary](data/window-curation-baseline-2026-11-01.json), [candidate summary](data/window-curation-candidate-2026-11-01.json), [source-held-out decisions](data/window-curation-development-cv-2026-11-01.tsv), [source-held-out summary](data/window-curation-development-cv-2026-11-01.json), [fold provenance](data/window-curation-development-folds-2026-11-01.json), [fit metadata](data/window-curation-fit-2026-11-01.json). Candidate hash: `b32f1124b31e4961f52c1153fa8220d3cc10b374292dfdb199b7bcb852e56be8`.

## Status

Five new curation checks cover fixed selection/spacing, absent qualifying samples, participant leakage, exact full-window extraction, truncated/invalid windows, unchanged non-AMI source metadata, new participant separation and annotation/hash reproduction. Existing corpus/timing/report checks pass. Kotlin report and source-held-out tools execute successfully. This follow-up changes developer data/tools only; APK/runtime sources are unchanged and the verified installation remains in place.

The [restricted Small Group fallback follow-up](environment-small-group-fallback-2026-11-01.md) tests safe combination of the two heads without promoting the full candidate. Better independent references for the other scene distinctions remain necessary. Watch/phone integration, physical aid acceptance and endurance/accessibility acceptance remain outstanding. No Play submission approval is claimed.

Subsequent implementation: the [primary-preserving monitor](environment-primary-preserving-fallback-2026-11-01.md) integrates only complete-abstention Small Group fallback into both apps. Its stricter behavior and four added evaluation detections supersede the offline combination for runtime reporting; full replacement and automatic qualification remain rejected.
