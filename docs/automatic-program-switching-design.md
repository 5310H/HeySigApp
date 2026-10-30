# Intelligent automatic program switching — October 30, 2026

## Repository investigation

The phone is a native Kotlin Activity application (`HomeActivity`, `ManualRuntime`),
with a JVM Kotlin shared core and an existing Wear OS module. Both Android build
variants use the direct selected-node, one-use-grant Data Layer connection.
`Program`/`Fitting` enumerate the user's configured slots and real labels. There is
no electronic discovery/readback of the aids. Setup/import is the available
program source. `ManualRuntime.execute` and `CommandTransport` serialize validated
speaker playback. Successful playback records **last requested**, not confirmed
aid reception. Do not change encoding or infer hardware acknowledgement.

`NoiseAccumulator` computes digital scalar statistics; `observeNoise` uses five
RMS boundaries. `NoiseFeature` persists six level assignments, thresholds and
optional measured calibration in `heysig-noise-v1`. Its calibration requires
three measurements in each of six increasing levels with nonoverlapping ranges.
Automatic preview currently requires this calibration; real switching is source
locked apart from an explicit bounded debug trial. The calibration requirement
cannot make these levels identify speech or count speakers.

`PhoneNoiseSampler` already provides bounded, foreground, cancellable PCM capture
with microphone routing and `MicrophoneLease`. Phone hands-free uses local
keyword detection and platform speech; phone and watch already have three-peak
shake gates. Watch voice has explicit foreground-session lifecycle and bounded
recognition. Reuse those paths. No Pixel-specific system gesture is assumed.
The existing watch UI button is the reliable activation control.

Shared executable checks live in `shared/src/commonTest` and `tools/check-*.sh`;
device acceptance is under `androidApp/src/androidTest` and `docs/acceptance`.
Existing acceptance records/settings must survive. No installation or aid
playback is needed to verify source changes.

## Incremental implementation plan

1. Add a separate shared environment settings/reducer in
   `shared/.../noise/EnvironmentSwitching.kt`: six named environments, nullable
   slot/label bindings, optional calibration, monotonic stability/cooldown,
   manual priority, completion tokens, and explicit simulation. Preserve the
   legacy calibration and its storage. Production acceptance remains a separate
   dispatch gate, not a requirement to calibrate digital levels.
2. Add independent phone settings/storage/UI (`EnvironmentFeature`) on Noise,
   using configured program names, two-minute cooldown/15-second stability,
   last-requested status, explicit monitoring and Stop. Reuse the single executor.
3. Add offline YAMNet inference in a small Android source directory shared by
   phone/Wear. Keep score interpretation/uncertainty in shared Kotlin. No
   transcript, recording file, raw-audio transfer or model download at runtime.
4. Extend existing command routing for the requested environment commands and
   cooldown controls; add bounded foreground shake-to-command controls without
   a wake word. Preserve existing independent prompt/reply choices.
5. Extend the existing Wear module with explicit intermittent foreground audio
   sampling and compact result RPCs. Phone owns assignments and switching;
   disconnect invalidates sessions, results expire, reconnect never replays.
6. Add regression/simulation checks and update usage, permission/data-flow and
   acceptance notes; build both debug APKs. Physical, accuracy, accessibility and
   battery evidence remain distinct from source/build checks.

## Model and dataset investigation

Google's [YAMNet model card](https://www.kaggle.com/models/google/yamnet/tfLite/classification-tflite/1)
provides an Apache-2.0 quantized mobile model taking 15,600 mono 16-kHz float
samples and returning 521 event scores. The approximately 4.13 MB model can run
offline with LiteRT. Speech, conversation, babble and crowd labels are useful
evidence; scores are not calibrated probabilities. YAMNet does **not** count
people or reliably identify a restaurant. Six-way interpretation is experimental,
and ambiguous/conflicting results must abstain. Group and restaurant distinctions
need representative recordings and physical validation before qualification.
See [upstream implementation](https://github.com/tensorflow/models/tree/master/research/audioset/yamnet).

[Silero VAD](https://github.com/snakers4/silero-vad) is MIT licensed, supports
8/16 kHz and small streaming windows. It detects voice activity, not group size
or location. It is a candidate for later validation if YAMNet speech evidence
proves inadequate; adding a second model/runtime now would increase CPU/memory
and integration cost without establishing accuracy. Existing RMS/ZCR statistics
alone are inadequate as a speech detector. No device CPU/RAM/battery measurements
have yet established a winning model or adequate sample schedule.

[FSD50K](https://zenodo.org/records/4060432) supplies diverse environmental clips
with per-clip Creative Commons licenses and attribution metadata. Review each
clip's license (including noncommercial restrictions) before redistribution;
retain author/title/source/license and hashes. Do not bundle its large corpus
in the app. [AudioSet downloads](https://research.google.com/audioset/download.html)
provide annotations/features; their CC license does not automatically license
the underlying YouTube recordings. Neither source establishes absolute SPL.
Development fixtures need quiet, single conversation, small/large groups,
restaurant, traffic and household recordings at multiple gains and mixtures.
Record held-out confusion/abstention rates separately from reducer tests.

## Watch architecture and lifecycle

Option A (local classification) sends only compact scores/results; it avoids raw
conversation transfer and radio traffic but costs local model memory/CPU.
Option B moves CPU to phone but transfers about 96 KB for three seconds of PCM,
adds latency/disconnect exposure and expands sensitive data handling. Choose A;
measure actual power/latency before claiming superiority. A 3/30-second schedule
has 10% nominal microphone duty cycle, excluding inference/radio/display costs;
it is a starting configuration, not evidence of battery life or accuracy.

New monitoring is explicitly started and Activity foreground-only. It stops on
pause/lock, timeout, denial, cancellation or disconnect. No new background
microphone service or boot receiver is authorized by this design. This follows
[while-in-use microphone restrictions](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone).
Existing separately reviewed hands-free services retain their existing scope.
Recognition prefers [on-device SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
when available; fallback providers may use a network and require disclosure.

## Acceptance boundaries

Calibration is optional for the new semantic path. The old level-based path and
all existing fitting/calibration data remain available. Enabling a preference
does not establish hardware acceptance. Real automatic dispatch retains
`NoiseReleaseGate`; simulation is explicitly labelled and never presented as a
real program change. No Play submission, regulatory assessment, physical aid
confirmation or measured classifier/battery qualification is implied.
