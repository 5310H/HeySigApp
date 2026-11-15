# Television recognition: first diagnostic probe — November 15, 2026

Owner request: start with automatic recognition of television sound, eventually
selecting the saved TV program. The existing Watching TV setting is an explicit
listening choice, not recognition. This probe does not change that behavior.

## What changed

The existing pinned YAMNet encoder has Television and Radio outputs. The audio
adapter previously discarded them. It now includes those scalar outputs in the
existing in-memory frame evidence. Shared Kotlin validates and summarizes them;
phone Automatic diagnostics show mean/peak TV scores and mean radio score.
Missing labels from older/watch evidence mean unavailable, not TV absent.
No new model, microphone session, capture duration, persistence, network call,
program request, permission, or assignment is introduced.

The model scores are not calibrated probabilities. No switching threshold has
been selected. Manual Watching TV behavior, priorities, cooldown, executor and
legacy scene decisions remain as implemented.

## Reproducible measurement

`tools/environment/probe-television.py` runs the exact bundled encoder offline,
with the app's bounded input gain and 15,600-sample windows / 7,680-sample hop.
It verifies model and source SHA-256 pins, checks complete bounded windows,
and exports scalar frame scores. Source audio is developer test material only;
none is packaged in an app. No microphone or hearing-aid APIs are used.

Use an isolated Python environment with NumPy 1.26.4, tflite-runtime 2.14.0 and
FFmpeg. Cache the exact files in [sources.json](data/television-probe-2026-11-15/sources.json).
The new TV source is o_ciz's CC0 Freesound preview; other sources retain their
original provenance and license fields. Cache entries may be symlinks to the
existing environment corpus. There is no automatic downloader or network access
in the probe.

```sh
python tools/environment/probe-television.py \
  --manifest docs/acceptance/data/television-probe-2026-11-15/sources.json \
  --cache /tmp/heysig-tv-corpus \
  --output shared/build/television-probe/results.json
```

The committed [results](data/television-probe-2026-11-15/results.json) contain
18 windows from seven recordings: one uploader-described TV recording (three
sections at two durations), plus six conversation/group/noise recordings at two
durations. Windows from a source are correlated, not independent examples.
The TV source was not independently verified as a particular physical TV model;
it is background TV ambience rather than a comprehensive TV-content collection.

## Initial result

Mean Television scores for the TV source:

| Source offset | Three seconds | Ten seconds |
| --- | ---: | ---: |
| 0 seconds | 0.011719 | 0.004523 |
| 15 seconds | 0.002344 | 0.002056 |
| 30 seconds | 0.004687 | 0.002878 |

Speech/music scores dominate these sections. The largest mean Television score
among the six non-TV sources was 0.001563. These few values do **not** justify
fitting a tiny separating threshold: one positive source cannot establish
generalization, and radio/music/phone playback confounds are missing.

Longer sampling did not strengthen the mean Television score in these three
paired sections. This is evidence against solving this example just by extending
capture, not proof that duration never helps.

The built-in Television output alone has not demonstrated usable recognition.
No accuracy, detector acceptance, phone/watch physical acceptance, automatic TV
switching, or Play approval is claimed. Two additional Freesound candidates were
unavailable (HTTP 403); they were excluded, not treated as test results.

## Next implementation step

Build a TV-versus-other corpus with independently identified TV setups, content
(dialogue, news, sports, music, advertisements), rooms, distances and soundbars.
Include difficult negatives: live conversation, radio, podcasts, phone/laptop
playback and music. Split by recording/setup/content source before fitting;
never put nearby windows from one recording into both training and evaluation.

Evaluate the existing output first on this broader set. If inadequate, train a
small TV-specific head on the existing embeddings, following the repository's
pinned model/provenance process. Report missed TV, false TV, time to enter/exit,
and results by source on untouched evaluation sources. Select thresholds and
temporal confirmation from development sources only. Compare three and ten
seconds on paired sections without changing the live capture design yet.

Only then connect validated TV evidence to saved TV assignment through shared
Kotlin policy and the existing executor. Specify separate TV entry/exit rules,
unknown handling and heavy-noise precedence; retain manual/Therapy protections.
The microphone cannot establish physical screen identity in every case: another
speaker playing identical content may be acoustically indistinguishable.

Before any further device installation, create a restorable private app-data
backup and verify its contents, rather than relying on checksums alone. The
earlier phone update lost saved data; this probe does not restore or invent it.

Primary model reference: [TensorFlow YAMNet documentation](https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/README.md).
