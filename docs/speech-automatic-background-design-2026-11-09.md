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
