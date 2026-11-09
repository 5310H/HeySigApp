# Reproduce the speech masking experiment

Development-only tools process licensed public sources, never wearer audio.
No transcript is extracted and no sound is played. Source audio stays in a
developer cache and must not be packaged in applications.

Use Python 3.11, NumPy 2.4.4, SciPy 1.17.1, ai-edge-litert 1.4.0,
scikit-learn 1.7.2, threadpoolctl 3.8.0 and FFmpeg. Android retains its existing
LiteRT 1.4.2 runtime. No new Android inference dependency is used.

```
python tools/masking/prepare-corpus.py --cache /tmp/heysig-masking-corpus
python tools/environment/fetch-scene-corpus.py --manifest shared/src/commonTest/fixtures/environment/scene-head/expanded-corpus.json --cache /tmp/heysig-environment-corpus
python tools/masking/evaluate.py --speech /tmp/heysig-masking-corpus --background /tmp/heysig-environment-corpus --output shared/build/masking
python tools/masking/fit.py --work shared/build/masking
python tools/masking/report.py --work shared/build/masking
python tools/masking/make-fixtures.py --work shared/build/masking --speech /tmp/heysig-masking-corpus --background /tmp/heysig-environment-corpus --output androidApp/src/androidTest/assets/masking-fixtures
```

Downloads are explicit and bounded. Existing background hashes and licenses are
verified by the existing corpus loader. The speech archive has a fixed SHA-256;
selected members have independent hashes in the recorded source manifest.
No automatic source replacement after a failed hash or download is permitted.
Short backgrounds (minimum half a second) can be explicitly tiled to three
seconds for mixing; foreground speech is never padded or tiled. Repeated
background variants are correlated and never counted as independent recordings.

Four development speakers and five development backgrounds fit the head.
Four other speakers and five other background recordings are reserved for
evaluation, including separate overlapping voices from their own partition.
Speaker groups and all background recordings are disjoint across the outer
split. The first heldout results were viewed before the regularization follow-up;
they are regression evaluation, not a fresh blinded field test. C and temperature
are selected using development-speaker folds only; background recordings are
shared within those inner folds. Neither controls nor outer heldout examples
fit coefficients or choose parameters.

Mixes are +15/+3/-8dB foreground/background RMS, referenced as clear/moderate/heavy
for this experiment. These operational labels are not clinical boundaries.
Every SNR has matched overall -30/-45dBFS RMS and near/low-pass+echo variants.
The digital filter/echo is a position proxy, not a real phone-placement test.
TV/music/quiet controls include explicit surrogates; representative real TV,
music and field microphone data remain missing. Generalization to other languages,
hearing-aid fittings, intended talkers and phone placements is not qualified.
YAMNet pretraining may have seen public source families; these sources are held
out from this new head, not guaranteed absent from pretrained encoder training.

The reporter invokes the actual shared Kotlin classifier/reducers. The level
comparison uses the same three repeated checks with fixed illustrative six-level
mapping 1/1/2/2/3/3, default thresholds and program roles 1/2/3, not the owner's
private choices. `--classes` can reuse compiled shared classes; `--compiled-jar`
can explicitly reuse the reporter binary. Default behavior recompiles sources.
The fit's selected temperature is folded into the serialized coefficients.
Softmax is a model score, not a calibrated probability of wearer intelligibility.

The fixed head is hash-pinned in Gradle and the Android loader. Fitting on another
solver/platform need not be byte-identical; compare decisions, then intentionally
review any new artifact pins. Native fixtures compare DSP, pooled encoder
features and Kotlin decisions with host extraction; they never establish field
accuracy or hearing-aid reception. All source credits and transformation notes
accompany fixtures and the packaged head.
