# Hearing-aid adaptation concepts — November 11, 2026

## Scope and evidence

Owner supplied [BLUEMOTH Hearing's Starkey Omega AI+ vs. Phonak EON Sphere comparison](https://www.youtube.com/watch?v=smvoPL8I0yg)
and [Doctor Cliff's BEST Hearing Aids for Background Noise in 2026](https://www.youtube.com/watch?v=ksn5CKXzoPM).
Both titles/authors were verified through YouTube oEmbed. Full watch pages and
captions were inaccessible (web cache misses; direct requests returned HTTP 429).
This is therefore a manufacturer-source review prompted by those videos, not a
completed viewing or transcript analysis. No rankings, quotations or timestamps
are attributed to unseen video content.

## What HeySig shares with hearing aids

The common control loop is: observe the acoustic scene, estimate listening
difficulty, select an appropriate strategy, and avoid unstable changes.
HeySig selects existing saved programs. Its local RNNoise analysis is evidence
for selection; it does not deliver cleaned audio to the ears. Hearing aids
process their own microphone signals and can adjust frequency-dependent gain,
directionality and noise suppression continuously. A phone in a pocket or wrist
microphone does not measure the same scene as microphones at the ears.

Headphone-style active noise cancellation uses an opposing acoustic signal.
Hearing-aid speech separation, directional processing and scene classification
are distinct operations, even when marketing calls them noise cancellation.
[Phonak's AI explanation](https://www.phonak.com/en-us/professionals/campaign/all-about-ai-dnn)
distinguishes scene classification from speech separation; its
[Target fitting guide](https://www.phonak.com/content/dam/celum/phonak/master-assets/en/documents/resources/phonak-target/ph-guide-target9.0-step-by-step-210x297-029-1324-02-en.pdf)
describes Dynamic Noise Cancellation as spatial processing with a beamformer.

Neither the RNNoise retained/residual ratio nor classifier confidence measures
the wearer's speech understanding. Reducing a dominant voice can even remove
the person the wearer wants to hear. The app cannot infer actual aid reception
or perceptual improvement from successful command playback.

## Current manufacturer offerings and transferable ideas

These are source-verified offerings as of this review, subject to regional
availability. Performance descriptions are manufacturer claims, not comparable
head-to-head results or evidence that HeySig achieves the same benefits.

| Manufacturer / offering | Source-described approach | Useful HeySig concept |
| --- | --- | --- |
| [Phonak Audéo EON Sphere](https://www.phonak.com/en-us/hearing-devices/hearing-aids/audeo-eon-sphere) | Spheric Speech Clarity 3.0 separates speech/background in real time. | Keep acoustic scene selection separate from speech-separation evidence. |
| [Starkey Omega AI+](https://www.starkey.com/hearing-aids/omega-artificial-intelligence-plus-hearing-aids) | Adaptive speech/noise processing and on-demand Edge Mode. | An explicit user-requested assessment can supplement Automatic without overriding manual intent. |
| [Signia MaX](https://www.signia-pro.com/en/signia-library/2026-08-24-wp-holistic-ai-driven-enhancement/) | Four coordinated DNNs; group-conversation, wind and transient handling. | Track different failure causes separately rather than equating all loud sound with difficult speech. |
| [Oticon Reveal](https://www.oticon.com/inside-oticon/press/2026/2026-15) | Speech AI plus Context AI; conversation, head/body movement and acoustic inputs. | Preserve environmental awareness; context matters alongside speech prominence. Phone/watch orientation cannot substitute for ear/head sensors. |
| [ReSound Sensia](https://pro.resound.com/en-us/products/hearing-aids/sensia) | Automatic DNN denoising; Intelligent Focus combines four-microphone beamforming with DNN reduction. | Intended focus and wearer control matter. Distributed phone/watch recordings cannot be assumed to form a synchronized binaural microphone array. |
| [Widex Allure](https://www.widex.com/en-us/hearing-aids/allure/) | Speech Enhancer Pro balances speech and environmental sound; app offers assisted personal sound choices. | Separate comfort, clarity and awareness in evaluation rather than optimizing one noise score. |

## Comparison with current implementation

`audioAndroid/.../SpeechSeparationDetector.kt` already obtains an original speech
estimate before RNNoise corroboration. `shared/.../SpeechNoiseSeparation.kt`
contains the platform-independent separation policy; `SpeechMasking.kt` owns
saved-role mapping, repeated fresh confirmation, cooldown and Therapy/manual
protections. Phone and watch use the same pinned assets, but that does not
qualify wrist-microphone accuracy. The older six-scene gates remain separate.

No live decision thresholds, models, permissions, settings or fitting were
changed for this research. Manufacturer architecture alone does not establish
which policy change would improve this wearer's result.

## Recommended implementation sequence

1. **Make uncertainty understandable.** Show the active microphone source,
   speech-mask estimate, hold reason and proposed saved role together. Describe
   a switch as a program request, never as proven improved intelligibility.
   Reuse current bounded samples; add no audio history or extra capture.
2. **Add an optional comparison workflow.** On explicit wearer request, compare
   two already saved daytime programs with manual control and an immediate
   return action. Ask separately about words understood, effort, comfort and
   awareness. Keep answers memory-only by default; any saved feedback needs an
   explicit review. Do not change the fixed live model or add an enrollment,
   learning or acceptance requirement to Automatic.
3. **Evaluate artifact-specific holds offline.** Build licensed fixtures for
   wind, wrist rub/pocket occlusion, own voice, TV/music, multiple talkers and
   distant speech. Check missed useful changes as well as wrong changes and
   switching churn. Validate any new hold against quiet/clear returns so a
   stronger rejection rule does not leave the wearer stuck in a noisy program.
4. **Measure source-specific field performance.** Compare phone and wrist
   placements, time to correct request, false-switch rate, battery and wearer
   preference. Use held-out scenes and actual physical results. Adding sensor
   fusion or a target-speaker model requires separate design and qualification;
   there is no current basis to claim speaker identity or head direction.

Every future implementation retains saved assignments/fitting, optional
calibration, explicit Automatic on/off, manual/Therapy priority, confirmation,
cooldown and the shared validated executor. No cleaned-audio streaming,
background design expansion or release/medical claims follow from this review.
