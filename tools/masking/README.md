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

## Noise-suppression evidence

Compile the shared Kotlin feature extractor, then pass it to the corpus runner:

```
kotlinc shared/src/commonMain/kotlin/com/heysig/noise/NoiseCancellationSignal.kt tools/masking/CancellationFeatures.kt -include-runtime -d /tmp/heysig-cancellation-features.jar
python tools/masking/evaluate.py --speech /tmp/heysig-masking-corpus --background /tmp/heysig-environment-corpus --output shared/build/masking-cancellation --cancellation-jar /tmp/heysig-cancellation-features.jar
python tools/masking/fit.py --work shared/build/masking-cancellation
python tools/masking/report.py --work shared/build/masking-cancellation
```

The HSGMASK2 head includes six additional analysis-only spectral suppression
features. The runner feeds bounded PCM to the actual Kotlin implementation over
stdin and receives only six scalars, avoiding a separate Python approximation.
The first 2052 features, event scores, source splits and decision policy remain
the same as the baseline. HSGMASK1 remains readable for baseline comparisons.
Do not compare a v2 head against v1 feature rows or reuse a stale reporter JAR.
To evaluate the shipped agreement policy, add:
`--baseline-head androidApp/src/main/assets/masking/masking-baseline.txt` to the
report command. Without that option, the reporter measures the candidate alone.
The candidate-alone result is retained separately; it is not the live policy.

A per-bin 20th-percentile power estimate within each three-second capture supplies
a temporary background floor. A bounded Wiener-style attenuation estimates retained
and removed spectral power. Features describe removed fraction, speech-band
retention, retained-envelope range/variation, floor flatness and active-frame
fraction. No cleaned waveform is reconstructed or played. No noise profile is
carried between captures. These quantities are not target-speaker SNR, clinical
intelligibility or proof that competing talkers have been separated. Continuous
speech, music and overlapping voices can contaminate the estimated floor.

The model combines these features with original speech/event evidence; cancellation
strength alone never selects a program. Tune coefficients, uncertainty and the relative weight of the six cancellation
features using development-speaker folds only. The fixed candidate scale grid is
1/4/16; the selected scale is folded into serialized weights, not user settings. The existing outer set has already been viewed:
new scores are regression evidence, not a fresh blind acceptance study.

## Current phone neural path (November 10)

The live phone path now uses HSGMASK1 plus RNNoise retained/residual bands.
HSGMASK2 is archived at `third_party/masking/legacy-cancellation-head.txt` and
is not packaged. Its reproduction commands above remain historical research.
`evaluate-separation.py` executes licensed mixtures through the aligned native
resampler/denoiser and `SeparationReport.kt`; `fit-separation.py` fits fixed bands
on development mixtures only. `SeparationDecisionReport.kt` applies the shared
Kotlin policy to those measurements and baseline decisions.
`summarize-separation.py --work shared/build/neural-separation-native --baseline shared/build/masking/features.npz`
produces scalar regression summaries without heldout refitting.
The saved evidence and limitations are under
`docs/acceptance/data/phone-neural-noise-2026-11-10`. Native fixture consistency
is a separate Android instrumentation check, not accuracy or aid reception.
