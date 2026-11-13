# Live speech-aware Automatic service review — November 9, 2026

The owner initially requested a chirp-disabled preview, then expressly authorized
physical hearing-aid changes and directed “remove the preview and make live.”
This supersedes the earlier preview/acceptance enable lock for this new path.
Detector/physical acceptance remain **unaccepted** and are not fabricated.
The older six-scene monitor's two acceptance gates remain false and unchanged.

The visible Automatic On/Off action now starts SpeechAutomaticService, replacing
level-only dispatch for normal phone Automatic. The old level controller remains
available to development regressions and legacy level-only tools; it is not run
concurrently. The speech service reuses the existing foreground-service design:
explicit feature disclosure and runtime permissions before capture; foreground
microphone/mediaPlayback type; visible notification with Off; partial wake lock
bounded to eight hours; START_NOT_STICKY; in-memory one-use launch token; no boot,
alarm, persisted On or automatic replay. Its notification must remain enabled
and present. Existing manifest permissions are reused.

Only the service's capture permission treats the notification-backed session as
microphoneSession, allowing screen-off sampling. Other speech/noise checks retain
their existing foreground restrictions. The existing built-in microphone route,
MicrophoneLease, bounded three-second EnvironmentSampleWorker, cancellation checks
and PCM erasure are reused. The unmodified pinned local YAMNet encoder supplies
event evidence and pooled embeddings to one new phone-only 99KB linear head.
Four additional memory-only waveform features measure envelope variation,
4–16Hz envelope modulation, pitch periodicity and zero crossings. These are
speech-focused features, not speech intelligibility or speaker identity.
There is no extra inference runtime or dependency, model download on the phone,
transcript processing, recording API, analytics or audio upload in this path.

Shared Kotlin validates evidence, separates sound type from masking, requires
at least two fresh agreeing captures (or the higher saved count), holds for
uncertainty/unassigned/protected choices, honors the saved cooldown, tracks
pending tokens, suppresses duplicate target sends and halts after failed or
interrupted playback. Quiet and clear speech must independently achieve fresh
agreement before returning to Everyday. Explicit On resumes the existing manual
override through the existing adapter; manual selection stops the service.
Therapy, changed fitting/choices/settings, permission/notification removal,
microphone/model failures, app/task stop and session expiry stop future work.
Speech/audio priority pauses capture and clears confirmations. No future command
is queued from uncertain or pre-interruption evidence.

Every real program request goes through ManualRuntime.execute with
ManualRequest.SelectProgram and the existing validated serialized chirp transport,
including its journal, fitting validation, duplicate protection and stored
per-program setting restoration. Capture waits during playback and microphone
settling. Off cancels owned playback and future requests; emitted tones cannot
be undone. The service contains no alternate tone generator.

New local preferences heysig-masking-v1 store consent and three role choices
bound to the exact fitting signature. Missing choices initially reuse saved
levels 1/4/6; invalid or changed fittings do not redirect old role choices. The
six legacy assignments, calibration, names, fitting, thresholds and snapshots
are neither migrated nor rewritten. Only explicit program actions change the
existing last-requested/runtime journal state. In-memory graph: <=120 scalar
points. Other retained results: latest type/severity, confirmation state and
bounded session counters; no audio/transcript/answer history is persisted.

Device capture/cancellation, screen-off operation, notification Off, executor
behavior, upgrade preservation and short CPU/charge observations must be recorded
as actual results. Short tests do not establish all-day battery impact or actual
hearing-aid reception. No Play submission, medical classification, compatibility
claim or approval is asserted. Release review must cover the changed local
model, service usage, data disclosures, field errors, accessibility and hardware.

## Noise-suppression decision evidence — November 9 follow-up

The owner requested using noise cancellation to determine program switches.
A fixed HSGMASK2 second-opinion head adds six scalar features from shared Kotlin spectral
noise suppression to the existing encoder and four speech features. It estimates
a temporary per-frequency background floor inside each existing three-second
capture and measures retained/removed power and envelope structure. This is an
analysis-only Wiener-style suppression, not headphone active cancellation,
target-speaker separation or measured intelligibility. No waveform is reconstructed
or played. Arrays are erased after extraction; no floor persists between captures.

No extra microphone windows, service lifetime, permission, network connection,
transcript, stored audio, wearer enrollment, noise-profile setup or background
service capability is introduced. Existing role choices, six legacy assignments,
confirmation count, cooldown, manual/Therapy priority and the validated shared
chirp executor are unchanged. The unchanged HSGMASK1 head must agree with
the second-opinion head before evidence reaches that policy. Disagreement holds
the current program; the heads share an encoder and are not independent. The same owner-authorized live mode remains live;
no preview or acceptance lock is added. Existing quiet/music/transient/uncertainty
guards still apply. Steady-floor estimation can mistake continuous speech for
background and cannot identify the desired talker. Accuracy and physical
acceptance remain separate from enabling Automatic. See the follow-up
[evaluation](acceptance/noise-cancellation-switching-2026-11-09.md).

## November 10 phone replacement

Normal phone Automatic replaces HSGMASK2 spectral-suppression agreement with
HSGMASK1 plus a pinned RNNoise neural denoiser, aligned reference/residual
measurement and fixed shared Kotlin bands. Native libraries are packaged for
four Android ABIs; this is a new native dependency and needs release inventory
and device verification. PCM and neural working buffers remain local and are
erased; no extra capture, microphone permission, upload, persistent profile or
cleaned-audio playback is added. The three-second schedule, session limits and
shared validated executor are unchanged. Universal replaces Everyday in visible
role wording; saved keys and fitting names are untouched. The earlier two-head
path is retired from normal Automatic. See the [current report](acceptance/phone-neural-noise-2026-11-10.md)
and [completion checklist](phone-noise-completion-checklist.md).

## November 13: explicit Watching TV context

The owner confirmed an existing TV program in slot 5 and requested adding TV to
Automatic. A fourth optional role (`television`) and explicit `watchingTv` choice
extend the existing version-1 fitting-bound preferences; absent fields default
to unassigned/Off without changing the three previous choices. Enabling Watching
TV requires an assigned TV role. No slot or manufacturer capability is inferred.
The user selects their own role in Choose programs. Therapy-labelled programs
remain excluded and all four roles are validated against the current fitting.

The shared `MaskingChoices.target` maps valid CLEAR/MODERATE/HEAVY evidence to
TV when this context is on; QUIET maps to the existing Universal role and UNKNOWN
still holds. Shared confirmation, freshness, cooldown, pending/completion tokens,
duplicate prevention, manual/Therapy priority and failure halt remain unchanged.
No classifier score/threshold or detector asset changed. Quiet must be repeated
before return; clear speech remains TV. The choice persists until explicitly
turned off, so later speech can select TV again after a quiet return. A specific
first-use explanation and visible context status describe this behavior. It is
not automatic TV recognition or a correction to normal-mode TV misclassification.

The phone owns this routing even with watch input; the existing watch evidence
protocol/model profile is unchanged. No new permissions, microphone windows,
background lifetime, model, network request, data recipient, audio/transcript
storage or aid transport was introduced. Only the two new choice fields and
TV disclosure flag persist locally. All commands still use the existing shared
validated executor and per-program saved-setting restoration. This is not fitting
software and does not define or alter what the audiologist's TV program changes.

Validation and uncompleted physical checks are recorded in
[the TV update report](acceptance/tv-automatic-2026-11-13.md).
