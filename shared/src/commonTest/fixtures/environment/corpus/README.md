# Provisional environment corpus

This manifest pins seven public CC0 Freesound previews. Audio is downloaded only
with an explicit `--fetch` into a developer-supplied cache; no recordings are
bundled with the app. Source pages, authors, license URLs and byte hashes are
recorded per clip. Labels reflect creator descriptions, not independent review.

Use an isolated Python environment with `ai-edge-litert==1.4.0`, NumPy and FFmpeg:

```
python tools/check-environment-corpus.py --cache /tmp/heysig-environment-corpus --fetch
bash tools/report-environment-corpus.sh shared/build/environment-corpus/evidence.tsv shared/build/environment-corpus/references.tsv shared/build/environment-corpus/report.tsv shared/build/environment-corpus/verification.tsv
python3 tools/test-environment-corpus.py
```

The scorer uses the pinned original event-only model archive. The report invokes the actual shared
Kotlin classifier and switching confidence threshold. It does not dispatch any
commands. Sources, creators and source groups cannot cross development/validation
partitions; duplicate audio hashes are rejected. Duration and gain variants of a
recording are correlated, not independent observations. Short sources are never
padded/looped to fabricate longer cases. Decoded PCM stays in host memory; saved
evidence contains scalar event scores and hashes only.

Six development recordings cover all six labels by creator description; three
share one creator/session group. The single held-out recording covers only Large
Group. This is a regression probe, not sufficient training or qualification data.
See the October 31 acceptance report for results and unresolved recognition gaps.

`quiet-original.tsv` and `quiet-verification.tsv` retain only the shared mapper's
18 scalar labels for four known false Quiet cases and six room-tone cases.
`quiet-integrity.json` pins their bytes, model and source recording hashes.
Regenerate explicitly with `--regressions shared/src/commonTest/fixtures/environment/corpus`.
Digital gain variants are first quantized to PCM16, matching the device input;
the verification view uses the same bounded RMS/headroom gain as the shared
Kotlin implementation. This is not an SPL estimate or acoustic calibration.
The paired recordings are exercised by `tools/check-noise.sh` and Gradle tests.

October 31 scene-head update: these event-only probes now use the hash-pinned original encoder archive at `third_party/environment/yamnet-original.tflite`. This preserves historical scores and mapper fixtures. The app runtime uses the derived embedding encoder and separate scene head; see `scene-head/README.md` for current inference, corpus and acceptance limits.
