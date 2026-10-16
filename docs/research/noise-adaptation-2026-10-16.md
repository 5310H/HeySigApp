# Improving HeySig noise-driven program selection

Research date: October 16, 2026. Findings and proposed experiments; not implemented or accepted behavior. This report supplements the [coding specification](../coding-spec.md) without replacing its validated direct-control boundaries.

## Main finding

A loudness-only controller cannot distinguish a clear nearby voice from difficult conversation masked by background sound. Better hearing systems classify the acoustic scene and steer processing to match it. HeySig can borrow scene classification, stable decisions and user preference learning. It cannot acquire premium aid-internal beamforming or speech separation simply by sending program commands.

The goal should be better speech understanding, acceptable comfort and fewer unwanted changes, not maximum noise reduction or classifier accuracy alone. Existing programs must actually offer useful differences for this user; a more accurate selector cannot repair an unsuitable fitting.

## What other systems do

| System | Documented mechanism | Transferable lesson for HeySig |
|---|---|---|
| Phonak AutoSense OS 7.0 | Acoustic scene classification drives program activation/blending and appropriate processing features | Separate scene detection from policy; loudness is one feature, not the decision |
| Oticon Intent | Combines acoustic level, SNR and conversation activity with head/body movement | Listening purpose and context matter; do not infer wearer intent from sound level alone |
| Signia IX | Analyzes conversation layout and uses multiple binaural focus streams with separate surrounding processing | Nearby wanted speech differs from background babble; spatial capability is inside the aid |
| ReSound Vivia | Intelligent Focus is an on-demand focused mode using DNN-based processing | Provide a dependable user-invoked conversation mode as well as automatic selection |
| Starkey Edge AI | Describes real-time soundscape classification, speech enhancement and noise reduction | A scene classifier and a signal-processing engine are different components |
| Widex SoundSense Learn | A/B listening comparisons guide preference optimization | Ask which setting helps the person, instead of assuming every classified scene has one best setting |

Sources: [Phonak technical paper, August 2025](https://www.phonak.com/content/dam/phonak/en/evidence-library/white-paper/technical-paper/PH_Insight_AutoSenseOS7.0_210x297_EN_029-1459-02_V1.00.pdf), [Oticon technology](https://www.oticon.com/en-us/professionals/hearing-solutions/technology), [Signia IX technical paper](https://www.signia-pro.com/en/signia-library/2023-09-power-the-conversation-with-signia-ix/), [ReSound Vivia](https://www.resound.com/en-us/hearing-aids/resound-hearing-aids/resound-vivia), [Starkey Edge AI](https://www.starkey.com/hearing-aids/edge-artificial-intelligence-hearing-aids), [Widex user–AI collaboration article](https://pmc.ncbi.nlm.nih.gov/articles/PMC8463120/).

These describe particular systems, not universal features of each brand or every technology tier. Manufacturer explanations establish design direction; their performance claims are not a head-to-head ranking or proof of benefit for this user's aids. Widex's article describes preference optimization, including Bayesian optimization; that does not establish that its proprietary implementation can be copied or that a complex optimizer is necessary here.

## What HeySig can and cannot change

Current verified controls are configured program slots, volume, Sound Sharpness, tinnitus and audible battery requests. We have not established commands for microphone beam width, directionality, noise-reduction strength, compression, frequency-band gains or access to aid microphone signals. Program slots are discrete; rapidly alternating them is not equivalent to the internal blending described by some manufacturers.

Program selection may activate useful processing already fitted into the aids. Program names such as Noisy and Very Noisy do not establish their actual processing. First establish their intended use and settings from the fitting information, and assess them with the user. Where fitting review is needed, ask the hearing professional what differs in directionality, noise management and speech audibility between slots; HeySig itself must not invent these settings.

Neither the current acoustic controller nor Signia provides actual aid readback in this setup. Any adaptive decision is based on phone observations and remembered requests; it is not a measurement of what reached the eardrum. Slot beep counts can interrupt listening, so program changes need an explicit switching budget and stable decisions.

NIDCD notes that hearing aids do not completely separate wanted and unwanted sound, and that settings may need adjustment. [NIDCD hearing-aid guidance](https://www.nidcd.nih.gov/health/hearing-aids).

## Recommended architecture

Pipeline: microphone quality check → acoustic features and speech/scene estimates → temporal smoothing → user-goal policy → stable program recommendation → existing priority/audio guards → discrete program transmission → optional user feedback.

Keep a simple deterministic policy before introducing personalization. A local language model can interpret “I cannot follow the conversation,” but should not perform continuous acoustic classification or invent a noise-removal setting. Use an audio classifier for audio and ordinary validated control code for actions.

### 1. Validate the sensor before tuning thresholds

Phone placement, orientation, pocket/bag covering, handling noise, wind and distance from a talker can change the measurement independently of what the aids receive. Phone motion does not establish head motion, and a wrist microphone is not an ear microphone. Use a declared measurement placement during calibration and test deviations. If quality is unreliable, hold the last requested program and report why; do not treat an occluded phone as a quiet environment.

Check microphone source and preprocessing. Android's UNPROCESSED source is available only on supporting devices and may fall back; it must not be assumed raw. Automatic gain or noise processing can make RMS thresholds misleading. Record the actual supported capture configuration and repeat calibration after changes. Keep indices as relative units, not calibrated SPL. [Android capture guidance](https://developer.android.com/media/platform/mediarecorder), [AudioSource API](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource).

Calculate frame-level RMS, clipping fraction and spectral features, then summarize across a capture. Use a robust background proxy from lower-energy frames only when enough non-speech evidence exists. Continuous babble, music and reverberation can invalidate that approximation: expose unknown background estimates instead of a fabricated value.

An RMS index measures the mixture of wanted speech and noise. Voice activity alone cannot distinguish a conversation partner from unwanted speech. Do not label a VAD/noise-floor heuristic as true target SNR; a validated target/noise estimator would be separate research.

### 2. Add speech and scene evidence

Start with speech activity plus broad, nonexclusive evidence for speech, music, traffic/mechanical noise and unknown scene. A model such as YAMNet is a candidate for a benchmark, not a preselected production classifier. TensorFlow documents 521 audio-event classes; Android's audio-classification guide supports model-based microphone classification. [TensorFlow YAMNet](https://www.tensorflow.org/hub/tutorials/yamnet), [Android audio classification](https://developers.google.com/edge/mediapipe/solutions/audio/audio_classifier/android).

Audio-event scores are not automatically calibrated probabilities, speech intelligibility scores or the user's desired target. Validate locally and allow simultaneous speech and music/noise labels. TV speech and restaurant babble are important adversarial cases. An “unknown” result must be a normal outcome.

Proposed policy states: quiet/no speech, speech in quiet, speech with noise, sustained noise without speech, music, and uncertain/unreliable capture. Wind/handling quality flags may veto changes rather than forcing a dedicated program. Do not claim reliable near/far talker identification from a single phone microphone.

### 3. Add explicit listening goals

Voice goals: “Help me follow this conversation,” “I am listening to music,” “Keep my surroundings audible,” and “Make this more comfortable.” Goal choice constrains eligible programs. A noise-only environment need not trigger the same program as speech-in-noise at the same measured level.

Map scenes/goals to enabled configured slots through a user-reviewed table. Do not automatically assign TV because speech plus music was detected, or Outdoor solely because traffic was heard. Keep Sleep and tinnitus-only/low-awareness choices excluded from ordinary noise automation unless explicitly reviewed for that use.

All goal, feedback, pause, undo and explanation workflows must work by voice from phone and watch. Watch controls use the same phone-owned state and policy; initial sensing remains on the phone.

### 4. Improve temporal stability and responsiveness

Separate short feature windows from slower program decisions. Evaluate approximately one-second feature summaries and aggregate them over several seconds; determine the capture cadence by battery and lifecycle tests. The earlier five-second capture plus ten-second wait can miss rapid transitions and is an initial baseline, not an optimized target.

Retain hysteresis, fresh confirmations, cooldown, override, sleep priority and self-audio exclusion. Experiment with asymmetric transitions: enter a confirmed difficult scene sooner, leave only after longer stable evidence. Candidate starting values are 5–10 seconds of stable entry evidence, 20–30 seconds for exit and a 60-second minimum dwell; these are tuning hypotheses, not clinical or release defaults.

Prefer confidence-weighted evidence with an abstain state over more threshold bands. A door slam must not cause a program change. A short pause in conversation must not cause immediate return. Preserve state across ordinary inter-word gaps, but clear stale candidate evidence after a genuine capture/resource gap.

Provide Conservative / Balanced / Responsive policy choices, each with documented timing. Add a configurable maximum automatic changes per time window; exceeding it pauses switching and asks for feedback. Start with program-only automation; automatic multi-slider changes would complicate attribution and restoration.

### 5. Learn personal preferences through guided comparisons

Offer “Compare conversation programs.” Suspend automation, explain the starting state, then let the user explicitly try two eligible programs under similar conditions. Ask separately “Which made words easier?” and “Which was more comfortable?” Accept first/second/same/unsure. If the environment changes substantially or reception is uncertain, discard the comparison rather than recording a preference.

Begin with per-scene/goal pairwise counts or explicit preferred-program rules. Consider Bayesian preference learning only if enough reliable feedback demonstrates a need. No silent exploration while the user is trying to hear someone. Never optimize an unbounded level search or alter tinnitus as a noise-control technique.

“Go back” uses an explicit known previous target, rechecks guards and reports what was sent. It cannot guarantee restoration of unknown aid settings. “That is better/worse” requires a recent unambiguous comparison context; otherwise ask what the user means.

Store only opted-in scene summaries, goal, compared programs, fitting revision and explicit ratings. Fit changes invalidate dependent preferences. Provide voice review/forget/reset. No raw speech recording by default.

## Proposed implementation phases

| Phase | Deliverable | Gate before advancing |
|---|---|---|
| N1 — Sensor and fitted-program audit | Native capture quality/preprocessing report; known slot purposes; placement tests | Reliable relative measurement and useful program differences established |
| N2 — Shadow scene detection | Speech/activity classifier, quality flags, temporal summaries; Observe only | Evaluate false scenes, TV/babble/music confusion and uncovered/pocket cases without playback |
| N3 — Goal-aware recommendations | Voice-selectable goals, editable scene-slot map, reason/explanation | User can accept/reject recommendations from phone/watch; no automatic learning from silence |
| N4 — Stable switching pilot | Conservative dwell/hysteresis/cooldown and switching budget | Fewer inappropriate changes and improved listening outcomes over level-only baseline |
| N5 — Personal comparison learning | Explicit A/B workflow, bounded preferences, forget/reset | Repeatable user benefit, valid comparison context and no automatic exploration |
| N6 — Endurance and advanced options | Combined voice/noise/sleep trial; optional specialized estimator or sensor study | Measured resource/lifecycle reliability and incremental benefit before release claims |

These are proposed phases for review, not changes to current working aids or fitted programs. All future implementation uses main; proto remains the saved snapshot.

## Evaluation that answers the actual problem

Compare manual user choice, the existing level-only policy and the proposed scene/goal policy. Include speech in quiet, speech with fan/traffic, competing voices, TV/music, quiet-to-loud changes, short impacts, wind/handling and covered-phone cases. Use the same phone placement and comparable conditions for initial comparisons; then deliberately test placement changes.

For speech understanding, use permission-cleared speech material and score correct words at matched source conditions, with equivalent material and counterbalanced program order to reduce practice/order effects. Personal comfort/preference is a separate outcome; quieter is not automatically more intelligible. Formal speech-in-noise assessment and fitting verification can be done with a hearing professional where useful. Do not promise a specific improvement from a classifier benchmark.

Record unwanted switches/hour, missed useful transitions, transition delay, reversals, user corrections, confidence/abstention, unknown samples, phone/watch command latency and battery/memory. Record program transmission separately from user-confirmed reception. Run unattended lifecycle/endurance tests after the recommendation policy proves useful, not as a substitute for listening benefit.

## When selecting a program is insufficient

An external selector cannot supply missing microphone directionality, higher-quality noise processing or a better target signal. Where available on the actual aids, a compatible remote microphone/loop or direct source connection may address the signal path more directly. Compatibility, available accessories and acceptable latency must be established before proposing a HeySig integration. NIDCD describes assistive listening paths that reduce competing background noise. [NIDCD assistive listening devices](https://www.nidcd.nih.gov/health/assistive-devices-people-hearing-voice-speech-or-language-disorders).

Phone capture → neural denoising → streamed listening would be a different audio product requiring a compatible delivery path and tests of latency, direct/processed mixing, intelligibility and battery. The existing speaker command channel cannot deliver cleaned conversation audio into the aids. Do not conflate that future research with noise-driven mode selection.
