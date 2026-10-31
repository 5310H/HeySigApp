# Provisional environment corpus

This manifest pins seven public CC0 Freesound previews. Audio is downloaded only
with an explicit `--fetch` into a developer-supplied cache; no recordings are
bundled with the app. Source pages, authors, license URLs and byte hashes are
recorded per clip. Labels reflect creator descriptions, not independent review.

Use an isolated Python environment with `ai-edge-litert==1.4.0`, NumPy and FFmpeg:

```
python tools/check-environment-corpus.py --cache /tmp/heysig-environment-corpus --fetch
bash tools/report-environment-corpus.sh shared/build/environment-corpus/evidence.tsv shared/build/environment-corpus/references.tsv shared/build/environment-corpus/report.tsv
python3 tools/test-environment-corpus.py
```

The scorer uses the pinned shipped model. The report invokes the actual shared
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
