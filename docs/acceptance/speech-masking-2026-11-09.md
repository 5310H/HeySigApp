# Speech-aware Automatic — November 9, 2026

The owner first requested a preview, then explicitly authorized real hearing-aid
program changes and directed removal of the preview. Normal phone Automatic is
therefore live at the owner's request, **before detector or physical acceptance**.
This is experimental development behavior, not clinical intelligibility,
compatibility, regulatory, store or physical reception approval. Existing
six-scene classifier gates remain false. See the [service review](../speech-automatic-background-design-2026-11-09.md)
and [plain-language guide](../speech-aware-automatic.md).

## Detector and first use

The existing pinned YAMNet encoder is unchanged. Its event labels alone do not
represent foreground/background ratio or identify a desired talker. A new fixed
phone-only linear head uses the 2048 pooled embedding values plus four speech
features: envelope depth, 4–16Hz modulation, pitch periodicity and zero crossings.
It predicts clear/moderate/heavy constructed masking references, with minimum
score .65 and margin .15. Weak/invalid/music/isolated/clipped evidence holds.
Quiet needs very low microphone RMS and positive silence evidence. Neither
calibration nor wearer recordings/examples, learning, enrollment or acceptance
locks are used at runtime.

Artifact: `masking-head.txt`, SHA-256
`884b7e0f1a925b3714f9c9b85a7bbce47a33b3c16ea891c1c6bf4e57a02e1812`.
The approximately 99KB head reuses the existing LiteRT runtime; no extra Android
runtime/dependency is added. The separately investigated Microsoft pDNSMOS model
was not packaged: host ONNX runtime 1.23.2 and 1.20.1 failed CPU initialization;
the default ONNX reference evaluator exhausted available resources. No quality
or performance result is claimed for that rejected integration attempt.

## Licensed development experiment

Source credits, original hashes, partitions, licenses and transformations ship
in the head's `sources.json`. Fixed [LibriSpeech CC BY 4.0](https://www.openslr.org/12/)
archive SHA-256 `76f87d090650617fca0cac8f88b9416e0ebf80350acb97b343a85fa903728ab3`.
No transcripts were extracted. Eight readers, two ten-second utterances each:
four readers for coefficient fitting and four disjoint evaluation readers.
Five development and five disjoint evaluation background recordings cover cafe,
crowd, traffic and steady appliances. A sixth overlapping-talker condition uses
another reader from the same outer partition. Sources are CC0/CC BY 3.0/4.0,
verified by the existing corpus loader. Foreground speech is never tiled; short
backgrounds are explicitly tiled to three seconds for mixing.

Each speech/background pairing has +15/+3/-8dB foreground/background RMS, called
clear/moderate/heavy **for this experiment**, matched to -30/-45dBFS total RMS.
Near and low-pass+echo conditions are digital position proxies, not actual phone
positions. There are 576 development and 576 heldout mix variants, plus fifteen
controls per partition. Variants are correlated, not 1,152 independent trials.
SNR labels are not measured wearer intelligibility. Pretrained encoder source
overlap cannot be ruled out.

Development-speaker CV selects C=.1, temperature=1; backgrounds are shared within
the inner folds. Final heldout speakers and backgrounds never fit coefficients
or select C/temperature. The initial C=1 heldout result had already been viewed;
the final heldout set is **viewed regression evaluation**, not fresh blind data.
No parameter was selected by the heldout score. Reproduction commands and pinned
tool versions are in [tools/masking](../../tools/masking/README.md).

## Measured Kotlin decisions

The reporter executes the actual shared Kotlin head and reducers, including
three fresh agreeing samples with chirps absent from the reporter. The level-only
comparison uses the same measurements/checks, default thresholds and illustrative
level assignments 1/1/2/2/3/3; it does not use private wearer settings. Stored
[cases](data/speech-masking-2026-11-09/cases.json),
[decisions](data/speech-masking-2026-11-09/decisions.tsv),
[CV fit](data/speech-masking-2026-11-09/fit.json), and
[summary including controls](data/speech-masking-2026-11-09/summary.json)
record the actual results.

Heldout confusion (192 variants per reference):

| Reference | Everyday/clear | Noisy/moderate | Very Noisy/heavy | Uncertain hold |
|---|---:|---:|---:|---:|
| Clear | 163 | 7 | 2 | 20 |
| Moderate | 34 | 48 | 64 | 46 |
| Heavy | 28 | 0 | 145 | 19 |

- Correct severity: **356/576 (61.8%)** versus level-only **200/576 (34.7%)**.
- Noisy/Very Noisy cross-confusions: **64/384 moderate-or-heavy variants (16.7%)**,
  or 64/576 (11.1%) of all mixes. All 64 are moderate called heavy.
- Uncertain holds: **85/576 (14.8%)**. Accepted wrong labels remain; repeated
  checks do not cure a persistent model error.
- Equal-loudness clear/buried pairs both correct: **119/192 (62.0%)**. Loudness
  alone cannot distinguish the three ratio variants sharing the same level.
- Fourteen nonquiet controls: false changes **4/14 from Everyday** and **6/14
  from Noisy**. Four background-only controls propose Very Noisy; both TV voice
  surrogates propose Everyday from Noisy. Synthetic music and a short isolated
  impulse hold. These few correlated/surrogate controls are not reliable field
  false-switch estimates. The quiet forest surrogate holds instead of returning
  to Everyday; quiet recognition is not universally accepted.

Moderate masking is weak, isolated intended-talker identity is unresolved, and TV
voices can count as speech. These results do **not** qualify unattended accuracy.
Live functionality is provided because of the owner's subsequent explicit
direction, not because these errors passed an acceptance threshold. Noisy or
Very Noisy can select only the wearer's configured waking programs.

## Validation and remaining acceptance

The full noise shell suite passed, including new sustained return, unassigned
hold, fresh/duplicate/stale checks, cooldown, manual/Therapy priority, token
completion and invalid evidence tests. Gradle shared tests: **35 passed**, zero
failures/errors. Phone/Wear debug builds passed. Six Android 13 JNI fixture decisions match
host classes and scores within .01. The initial overly strict pooled-feature
check failed: maximum scaled-feature differences .1305–.1959 (features are
pooled values times 20), despite matching DSP features within 1e-7. Host LiteRT
1.4.0 and Android 1.4.2 quantized encoder results are not bit-identical. The
regression now bounds maximum scaled-feature drift at .25 and cosine at .99,
while retaining unchanged classes and score tolerance .01. This is six-fixture
consistency evidence, not native evaluation of all 576 mixes. Phone and watch lint passed with zero errors (33/18 warnings). The initial
local lifecycle harness stalled without a completed result, so screen-off and
executor acceptance are not credited from that run. The revised harness emits
progress and avoids synchronously waiting for a reused Activity. Final
upgrade, screen-off,
executor and Pixel battery/physical observations will be recorded after their
actual runs. Six licensed development/heldout JNI fixtures are test assets only,
never played and never packaged in the main application.

Outstanding accuracy work includes more independent speakers/backgrounds,
representative real music/TV and transient controls, actual phone placements,
languages/room acoustics, target-talker ambiguity, reliable quiet return, and
better moderate/heavy discrimination. Outstanding device acceptance includes
actual aid reception confirmed by the wearer and sustained screen-off/battery
testing. Enabling or successfully synthesizing chirps cannot establish either.
