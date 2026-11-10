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
