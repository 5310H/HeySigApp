# Expanded scene experiment — November 1, 2026

The expanded-data candidate is **rejected**. The phone/watch model from `fcbde43` remains installed/packaged and both automatic gates remain false. This experiment sends no aid commands and captures no user audio.

The [expanded manifest](../../shared/src/commonTest/fixtures/environment/scene-head/expanded-corpus.json) adds seven individually licensed, hash-pinned FSD50K-derived recordings: five development sources and two new evaluation sources. All original source entries, partitions and hashes are unchanged. Extra development sources describe two-person conversations and dining/bar chatter. The 35 references yield 33 usable recordings (22 development/20 groups; 11 evaluation/11 groups); two sub-three-second originals remain excluded. Creator/participant partitions are checked. References remain provisional descriptions, not independent acoustic annotations. A dining/bar label does not establish competing speech in every window.

Two new sources are explicitly separated: `fsd-401794` (conversation, Sean-TE0) and `fsd-17118` (cafe voices/dishes, dcaudio). They contributed no fitting coefficients or hyperparameter selection. The former is shorter than five seconds and supplies only two three-second gain variants. These two sources cannot establish six-class generalization. The original evaluation set is now called a previously viewed regression set because its failures motivated the expansion. The new sources are also now viewed; future tuning cannot call them pristine unseen validation.

Per-clip licensing was checked against the archived official FSD50K metadata. Five new sources are CC0; Ryding's restaurant development source and dcaudio's cafe evaluation source are CC BY 3.0, with creator/title/source/license links and transformations recorded. No newly downloaded audio is in the repository or app. The rejected candidate head stays in the developer cache; no new runtime model/license combination is distributed.

A fixed regularization/temperature grid again selected C=10, temperature=.5 using only leave-source-group-out development log loss. All final decisions below run the actual shared Kotlin head, contradiction guards and unchanged .65 switching threshold. Duration/gain variants are correlated.

| Evaluation | Shipped head correct / wrong / abstain | Candidate correct / wrong / abstain |
|---|---:|---:|
| Development (212 variants) | 129 / 10 / 73 | 159 / 0 / 53 |
| Previously viewed evaluation (108 variants) | 31 / 0 / 77 | 31 / 9 / 68 |
| New evaluation (16 variants, two sources) | 2 / 0 / 14 | 6 / 0 / 10 |

The nine introduced errors are five Small Group → Conversation, three Large Group → Restaurant and one Quiet → Non-Speech Noise. Improved fitting scores and recognition of the two new references do not compensate for these regressions. No threshold is lowered, model replaced, or physical/Play acceptance claimed.

Evidence: [baseline decisions](data/scene-expansion-baseline-2026-11-01.tsv), [candidate decisions](data/scene-expansion-candidate-2026-11-01.tsv), [baseline summary](data/scene-expansion-baseline-2026-11-01.json), [candidate summary](data/scene-expansion-candidate-2026-11-01.json), [fit metadata](data/scene-expansion-fit-2026-11-01.json). Candidate hash: `c157c694677fb4e9ed74696561f9080cc2021dfb5973394dfe35bca86c70f48d`.

The report summarizer verifies source/partition identity, window uniqueness, finite scores, and consistency with actual Kotlin outcomes, and separates newly reserved sources from previously viewed evaluation. Corpus checks now cover CC BY attribution fields, reject noncommercial/unreviewed licenses, preserve original partitions, and reject invalid/duplicate/development-only fresh evaluation IDs.

## Development source-held-out diagnostic

The trainer can now export one head per held-out development source group, excluding that group from **both coefficients and centroids**. The Kotlin reporter optionally restricts decisions to that development group; the runner checks every head hash, source-group inventory and held-out case count. It rejects missing/duplicate folds and validation-only group reports. Hyperparameters were selected with these development groups, so this is a diagnostic, not nested unbiased validation.

Across 20 source folds and 212 correlated variants, the candidate produced **78 correct, 30 wrong and 104 abstentions**, compared with 159/0/53 on its fitted development sources. Large Group had zero correct accepted decisions and six wrong decisions. Conversation had two correct, two wrong and 38 abstentions. This exposes source-specific fitting before considering any additional independent evaluation and is another reason not to promote this candidate.

Evidence: [source-held-out decisions](data/scene-expansion-development-cv-2026-11-01.tsv), [summary](data/scene-expansion-development-cv-2026-11-01.json), [fold hashes/provenance](data/scene-expansion-development-folds-2026-11-01.json). No runtime/app sources or artifacts changed in this follow-up; the verified phone installation remains unchanged. Six corpus checks and two report-integrity tests pass, along with four legacy corpus checks and successful Kotlin reporter compilation/execution. The stronger reporting separates fitted-source success from behavior on excluded sources.
