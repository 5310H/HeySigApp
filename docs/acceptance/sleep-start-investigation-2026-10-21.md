# Sleep routine start investigation — October 21, 2026

The user reports that both Sleep now and scheduled reminders failed during
their testing. Read-only inspection of the connected Pixel 9a found saved test
times 12:20/13:00, bedtime slot 2 (Sleep), morning slot 1 (Universal), reminders
Off and manual override On. Stored mode ASLEEP and remembered program Universal
were inconsistent as a description of current aid state; these fields describe
separate earlier requests, not a live aid query. DND was Off and media unmuted.
There was no persisted Sleep/Wake error identifying the earlier physical failure.
The exact cause of the reported failed aid switch has not been established.

## Initial reminder-only changes

Sleep/Wake and read-only audio checks are placed first. Reminder Start/Resume
and Stop are separate explicit controls, and setup review offers Save & start
reminders or Save only. Existing saved times/programs are preserved. Starting
reminders clears manual pause without sending a program command.

A notification-only Test reminder uses the same notification channel and
app-opening path, with a separate notification ID. It leaves stored routine
choices, scheduled due times and reminder enablement unchanged. Its permission
request is distinct from starting reminders, including after activity recreation.
Receivers still cannot play aid commands or capture audio.

Reminder arming now reports failure if scheduling returns without arming both
events. Stop and failed starts clear due-time records. The dashboard uses those
recorded requests rather than calculated hypothetical next occurrences.

The shared executor records the latest Sleep/Wake result locally, including
preflight rejection, so an audio blocker remains available after navigation or
restart. This is a command result, not a transcript or audio recording. It is
included in the existing sleep-preference export/deletion flow. Playback still
uses the single validated executor and preserves the independent saved snapshot.
Touch confirmation rejects stale routine/fitting choices or expiry.

Inexact scheduling and the existing 15-minute late-event cutoff are retained.
[Android documents](https://developer.android.com/develop/background-work/services/alarms)
that inexact alarms may arrive within an hour or later under power restrictions;
this remains a limit on timing reliability. No exact-alarm access, automatic
overnight aid changes, changed aid fitting or new background microphone is added.

## Owner clarification: automatic switching

The owner clarified that scheduled automatic program changes while locked were
intended, rather than reminders asking for a tap. The implementation therefore
adds a separate reviewed automatic mode; the reminder-only changes above remain
available under Reminder options. Setup review now offers Save & start automatic
or Save only. The previous routine was successfully saved with test times
20:45/21:00 during this investigation; those user edits must be preserved.

See the [automatic design](../architecture/automatic-sleep.md) for exact-alarm
access, the media foreground service, claim/generation/revision checks, two-minute
late cutoff, Stop and manual priority. Migration does not arm automatic or grant
permissions. Service/clock/permission and physical aid reception acceptance remain
separate from fake scheduling/executor checks.

Before this clarification, the reminder-only candidate passed the pinned build
and isolated Chromebook checks, including fake notification/scheduling and audio
blocker tests. That candidate was not installed on the phone. Automatic-mode
validation follows below.

## Automatic-mode validation

The final pinned build passed shared tests, phone and Wear debug APK builds,
Android instrumentation APK assembly and Android lint. Lint reported zero errors
and 49 warnings; this is not a warning-free or release validation result.

The final Chromebook smoke passed on SDK 33, including isolated automatic-alarm
authorization, reviewed revisions, duplicate/stale/early/late/paused/revoked
occurrences, one-use claims, locked shared-executor authorization, saved-snapshot
preservation, scheduling/dispatch failures and interrupted recovery. Alarm and
aid transport tests use fakes. Navigation, reminder, sleep management and compact
round-watch layout checks also passed. Evidence is retained privately at
`private/sleep-automatic-2026-10-21/chromebook-smoke-final.txt`.

The final development APK and test APK were installed on the Pixel 9a. Semantic
preference fingerprints confirmed preserved fitting, requested and saved settings,
routine choices, speech preferences and personal phrases. Automatic mode remains
Off; exact-alarm access has not been granted on the owner's behalf. No aid
commands were sent for installation or UI verification.

The phone UI smoke could not complete because the phone was locked and asleep;
the screenshot layout check failed at the volume screen. The owner deferred
unlocking. Review/activation controls and the real notification path therefore
still need phone acceptance. Real scheduled playback while locked, physical aid
receipt, Android 16 behavior and overnight reliability remain unverified. The
owner can later open Sleep routine, review the saved times/programs, start
automatic mode and grant the requested Android access. Past times schedule the
next future occurrence; no missed event is replayed.
