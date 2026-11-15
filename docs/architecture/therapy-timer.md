# Therapy timer — November 15, 2026

Owner requested 5–60 minutes in five-minute increments and confirmed automatic
return to the saved Wake program. The phone Therapy screen offers those twelve
durations and reviews both programs, duration, locked-screen playback and Stop
behavior before sending anything. The saved daily routine and fitting are not
edited. Starting a timer turns daily automatic scheduling and reminders Off;
neither resumes when the timer ends.

The existing shared validated ManualRuntime executor sends the saved Therapy
program. Only successful playback with unchanged reviewed routine and permitted
alarms/notifications arms the timer. Its start is playback completion, not physical
hearing-aid receipt. An arming failure explicitly leaves timer Off and tells the
user to use Wake now. No microphone or recording is started.

Shared Kotlin validates the twelve durations and overflow-safe elapsed deadline.
AutomaticSleepScheduler stores a separate timer mode in the existing local
routine preferences, plus elapsed due time, boot count, generation and reviewed
revision. AlarmManager ELAPSED_REALTIME_WAKEUP with the existing exact-alarm
access requests one Wake action. The existing receiver claims a single-use token;
AutomaticSleepService performs its existing mediaPlayback foreground-service
handoff and validated serialized executor operation. No long-running service or
wake lock is added for the countdown. The existing bounded playback wake locks,
audio checks, retained program targets and no-retry failure handling remain.

A countdown notification exposes Cancel timer. The Therapy screen shows remaining
minutes and Cancel Therapy timer. Cancel/Stop cancels future switching and owned
playback; it does not send Wake or undo emitted tones. A manual control command
cancels a waiting timer through the runtime state callback. Therapy remains
protected from noise Automatic. A timer never enables microphone Automatic.

Expiry validates current generation, revision, boot, permissions, fitting,
Therapy state, no manual override/pending action and the existing two-minute
lateness bound. Duplicate, stale or early alarms never dispatch. A blocked,
failed, interrupted or late action is not retried; successful Wake disables the
one-shot mode without scheduling another occurrence. Clock/boot/update/alarm
permission broadcasts cancel timer mode. Reopening can display an existing
unclaimed timer but never rearms or replays it. Recovered pending claims halt as
unknown. After force-stop Android can remove the alarm; reopening after expiry
reports the missed timer without catch-up. Users should use Wake now if needed.

The feature reuses existing alarm permission and foreground-service declarations;
no target SDK, dependency, production signing, data recipient or capture flow
changes. It is development work. Locked-screen timing, denial/revocation,
notification cancellation, force-stop/boot, accessibility and actual hearing-aid
receipt require device acceptance; local checks are not Play approval or physical
acceptance.
