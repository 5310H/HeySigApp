# Scene sensitivity and reference audit — November 1, 2026

The unanimous-model experiment is **not promoted**. The verified phone/watch head and automatic gates remain unchanged. These are offline public-data diagnostics; no device microphone, user recording, transcript history or hearing-aid command is involved.

## Model sensitivity

A fixed research policy requires the full development-fitted head and all 20 source-excluded development heads to agree on the same environment at the unchanged 0.65 threshold. Any abstention or disagreement abstains; the reported score is the minimum component softmax, not a calibrated probability. Every component prediction runs the shared Kotlin head and its existing guards. The consensus decision itself is platform-independent Kotlin in the developer tools. No Python model/decision mirror is used.

The full head and every fold are pinned by hash. Reports must have identical windows/reference metadata, valid outcomes/scores and distinct head hashes. The models have overlapping training data; 21 votes do not provide 21 independent observations. This diagnostic is not a replacement for source-held-out cross-validation. Its development results measure sensitivity on fitted sources.

| Previously viewed evaluation: 124 correlated variants | Correct | Wrong | Abstain |
|---|---:|---:|---:|
| Shipped head | 33 | 0 | 91 |
| Expanded candidate | 37 | 9 | 78 |
| Unanimous 21-head policy | 6 | 0 | 118 |

The policy removes the candidate's nine known errors, but also loses most correct decisions. It accepts four Small Group and two Restaurant variants; Quiet, Conversation, Large Group and Non-Speech Noise all abstain. Its limited coverage rules out adopting it as the six-scene solution. Development sensitivity gives 77/0/135 over 212 variants; these are not unseen-source accuracy results.

All evaluation recordings have now been viewed. Follow-up summaries use `--all-validation-viewed`; the original reserved-source split remains historical provenance, not a claim of fresh evidence. Evidence: [decisions](data/scene-consensus-2026-11-01.tsv), [summary](data/scene-consensus-summary-2026-11-01.json), [component hashes and policy](data/scene-consensus-provenance-2026-11-01.json).

## Public AMI timing audit

The existing AMI references describe four-person meetings. Short windows need more specific evidence about audible activity. The audit extracts only `w`-element start/end timestamps from sixteen public AMI annotation files; punctuation/vocal-sound elements are excluded. Sources are individually hash-pinned. The timestamp-only derivative includes attribution, CC BY 4.0 links and transformations. No reference labels or model fits are changed by this audit.

Of 36 distinct 3/5/10-second windows, **28 contain words from fewer than three annotated speakers**. Requiring at least 0.5 seconds of annotated words per speaker yields 29 such windows. The two gain variants share timestamps and do not double the independent timing evidence.

| Public excerpt | Windows with fewer than three speakers having words / nine |
|---|---:|
| ES2002a (development) | 5 / 9 |
| ES2003a (development) | 9 / 9 |
| IS1000a (evaluation) | 5 / 9 |
| TS3003a (evaluation) | 9 / 9 |

All nine TS3003a windows contain words from one annotated speaker. Word timing is a limited activity proxy: it does not establish acoustic voice activity, group size, people present, room context or independent scene truth. The original labels remain unchanged. Meeting-level labels alone are insufficient to qualify short-window discrimination of Conversation and Small Group. The audit identifies a reference-quality problem requiring suitable per-window annotations and representative sound scenes, rather than threshold relaxation.

The default audit reads only the numeric fixture. Explicit `--fetch` verification receives public XML containing word text in memory, inspects only timing attributes, then discards it; no word text is printed or saved. It downloads no audio and never accesses user audio. All sixteen XML hashes and derived timestamp arrays reproduced exactly. See [timestamp fixture and credits](../../shared/src/commonTest/fixtures/environment/scene-head/ami-word-timings.json) and [scalar audit](data/ami-word-timing-audit-2026-11-01.json).

## Verification and remaining work

The Kotlin consensus policy checks all six unanimous outcomes, threshold equality, minimum-score behavior, disagreement, abstention and invalid/oversized votes. Three report-integrity tests cover altered hashes, duplicate heads, path escapes and mismatched windows. Three timing tests cover text-independent extraction, punctuation filtering, clipping, same-speaker overlap merging, interval bounds and fixture metadata. Three scalar-summary tests, six corpus checks and four legacy corpus checks pass. The Kotlin reporters compile and execute on all 21 component heads.

App/runtime sources, assets and fitting/settings are unchanged; no new APK installation is needed for these developer tools. Automatic switching still requires adequate classifier evidence, physical watch/phone integration, aid acceptance and endurance/accessibility checks. The watch remains unavailable. No release or Play approval is claimed.
