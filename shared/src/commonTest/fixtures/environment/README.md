# Development recordings and evidence

`recordings.json` pins four upstream WAV hashes and a small, fixed YAMNet label
subset. WAVs are downloaded to a developer-only cache such as `shared/build` or
`/tmp`; no recording is redistributed here or shipped in either APK. Fetch each
URL with its listed local filename, then run:

```
python3 tools/check-environment-model.py --fixtures /tmp --output shared/src/commonTest/fixtures/environment/evidence.tsv
bash tools/check-noise.sh
```

Use an isolated host Python environment with `ai-edge-litert==1.4.0` and NumPy.
The Android runner uses pinned LiteRT 1.4.2; host inference is not Android runtime
acceptance. The tool tests 3/5/10-second windows at two digital gains; it does
not assign calibrated SPL or imply gain invariance. Scalar evidence can be
reviewed/regenerated without keeping recordings in source control.

Sources: Microsoft MS-SNSD, Reddy et al., *A Scalable Noisy Speech Dataset and
Online Subjective Test Framework*, Interspeech 2019, pp. 1816–1820.
[Dataset and original terms](https://github.com/microsoft/MS-SNSD#dataset-licenses).
The code's MIT license is **not** a blanket audio license. Upstream identifies
noise sources as CC0 Freesound and CC BY-SA 3.0 DEMAND; per-file author/source
mapping is not provided there. These are development downloads only. Before
redistributing excerpts, resolve per-file provenance, attribution and share-alike
requirements. DEMAND: Joachim Thiemann, Nobutaka Ito, Emmanuel Vincent,
[Diverse Environments Multichannel Acoustic Noise Database](https://zenodo.org/records/1227121).

Current reference labels come from upstream filenames, not independent human
annotation. Restaurant/crowd accuracy, held-out conversation and small/large
groups, quiet recordings, provider/device behavior and battery remain acceptance
work. In particular, the model often calls Restaurant_1 generic Speech; passing
this regression preserves evidence of that failure and does not qualify it.
