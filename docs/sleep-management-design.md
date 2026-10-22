# Sleep management — October 17, 2026

Historical reminder-only design. The owner's October 21 request supersedes this
scope with optional [automatic sleep switching](architecture/automatic-sleep.md).
The receiver-only notification design below still applies to reminder mode.

The owner chose notifications requiring a tap, rather than automatic locked-phone
program switching. Alarm receivers have no playback path, microphone capture or
foreground service. They post local notifications that open sleep settings.
Sleep now / Wake now remain explicit commands, including voice in an already
authorized hands-free session. Buttons show the saved target before confirmation.

Shared Kotlin owns validation, parsing, mode transitions and reminder eligibility.
Android owns preferences, alarms, notifications and lifecycle. All program
commands use ManualRuntime and the existing validated transport. Successful
sleep/wake commands set the routine mode. Failed/interrupted playback and pending
transitions recovered after restart become Unknown with override On, with no
replay. Mode describes routine commands, not sensed sleep or aid feedback.
Sleep/wake commands preserve the independent saved restoration snapshot.

Manual program/level commands, including no-op requests, pause automation.
Explicit Resume permits future reminders and sends nothing; Sleep now/Wake now
clear override on success. Asleep/unknown/pending modes and override block noise
automation, whose separate physical-acceptance gate remains disabled.

Reminders require explicit enable, valid saved choices and notification access.
Saving choices turns reminders Off; fitting changes invalidate them and turn
reminders Off. Enabling explicitly resumes future reminders. Two inexact
AlarmManager RTC_WAKEUP alarms use setAndAllowWhileIdle. Shared clock planning
follows timezone changes, skips nonexistent DST times and uses the first repeated
occurrence. Android may delay delivery; events over 15 minutes late are skipped
with local status. Due timestamps are consumed before delivery, then future alarms
are rearmed, preventing duplicate delivery. Boot, app update and clock/timezone
changes replan only future events. Old notification intents only open settings;
they never carry an executable target. No past-event playback/catch-up occurs.

Local preferences include choices, fitting/revision context, mode, override,
pending transition, reminder enable/due times and the last reminder outcome.
No new network, analytics, audio/transcript or sensor collection is added. Existing
explicit export/deletion controls cover this state. Android owns alarms and
PendingIntents and removes them on app-data deletion/uninstall.

Android's [alarm guidance](https://developer.android.com/develop/background-work/services/alarms)
supports inexact scheduling when precise timing is unnecessary. No exact-alarm
permission or background foreground-service launch is added. Physical notification,
Doze, reboot and supported-device acceptance remain required. No Play approval is
claimed.
