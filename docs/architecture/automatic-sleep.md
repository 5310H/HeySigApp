# Automatic sleep design — October 21, 2026

The owner explicitly chose scheduled automatic program switching, including
while locked, replacing the earlier reminder-only requirement. Reminder mode
remains optional. Automatic is default-off; migration never arms it. Reviewed
saved revision, exact-alarm access and visible notifications are required.
Changing the saved routine or fitting disarms automatic and invalidates review.

Shared Kotlin owns clock planning, event-admission decisions, mode/override
priority, target validation and tone generation. Android owns alarms, preferences,
permission UI, notifications, wake locks and service lifetime. An immutable,
non-exported alarm intent carries due time, generation and revision. The receiver
validates all three against storage, consumes that occurrence, journals a unique
claim, rearms that side's next future occurrence and dispatches the service.
It never plays audio itself.

The non-exported media-playback foreground service consumes the claim once,
publishes Stop, acquires a bounded wake lock and calls the existing ManualRuntime
executor. It owns no microphone. Competing commands/reviews, expired claims and
permission changes block playback. Existing speaker/DND/volume/audio-focus and
route checks apply. A live claim grants locked-phone authority only for that
operation. No independent player, command queue, retry or level baseline is added.

The service is START_NOT_STICKY and bounded to 55 seconds. Stop invalidates
generations, removes future occurrences and requests cancellation of its own
operation. Permission/revision/override changes invalidate authority during
playback. Emitted tones cannot be undone. A durable claim remains until completion;
process death cannot replay it. Boot/update/clock recovery only schedules future
events. A leftover claim becomes interrupted/uncertain and disables automatic.

Manual changes pause automatic. Events more than two minutes late are consumed
and skipped. Failure disables automatic or retains manual override until review.
Due records, outcomes, generations/revisions and one-use claim metadata remain in
local sleep preferences, covered by export/deletion. This scheduler adds no raw
audio, transcript history, sleep tracking, analytics or network collection.

Android sources consulted October 21:

- [Exact alarms and special access](https://developer.android.com/develop/background-work/services/alarms)
- [User-requested exact-alarm foreground-service exemption](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Media-playback foreground service type](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Audio focus and foreground requirements](https://developer.android.com/media/optimize/audio-focus)

The manifest adds SCHEDULE_EXACT_ALARM and FOREGROUND_SERVICE_MEDIA_PLAYBACK.
This does not establish Play approval or medical/regulatory classification.
Submission requires foreground-service suitability/declaration and exact-alarm
scope review, updated release lint/signed bundles/Data safety, and physical
locked-phone, power, audio-route, notification-stop, permission-revocation and
interruption acceptance. Fake transport is not a hearing-aid receipt claim.
