# Selectable phone sleep schedules — October 14, 2026

The Pixel 9a passed selectable sleep settings, native Tasker clock events, program readback, foreground blocking, and restoration. This acceptance covers an unlocked phone with Signia open and the existing audio requirements satisfied. It does not certify switching on a locked phone, overnight endurance, restart recovery, or a sleep-tracking provider.

## User controls

Run `SIG3_SleepSettings` to choose both programs and clock times. Saving turns automation off. Run `SIG3_SleepEnable` to arm future events, or `SIG3_SleepDisable` to stop them. Enable does not immediately change the hearing program. Clock times follow the phone's local clock.

The project includes `SIG3_SleepSchedule` and `SIG3_WakeSchedule`. Each uses the same global time variable for From and To, giving an instant event with no exit restoration. Its entry task loads and validates persisted settings and submits an explicit sleep state through the worker. See [Tasker's Time-context documentation](https://tasker.joaoapps.com/userguide/en/timecontext.html).

Settings reloads leave unchanged time variables untouched. Missing settings, a changed program mapping, a load failure, or stale globals disable automation. Save and Disable clear the sleep-active noise-suppression state. Foreground deferral, manual override, cancellation, and audio preflight remain in force. A missed event is not retried automatically.

## Live evidence

| Check | Verified result |
| --- | --- |
| Program selector | All six configured choices appear; Save requires both selections |
| Clock picker | 24-hour picker opens; sleep time edited to 22:15 |
| Save/reopen | Saved choices reopen correctly; Save leaves automation off |
| Cancel | Reopened dialog cancelled without replacing saved choices |
| Sleep clock event, 13:22 | Noisy selected; SleepOK and CommandOK 1; SleepActive 1; no error |
| Wake clock event, 13:49 | Universal selected; SleepOK and CommandOK 1; SleepActive 0; no error |
| Foreground block | CommandOK 0 with “Automatic change deferred until Signia is foreground”; no program change |
| Restore | Universal, volume 10, balance −4, tinnitus 0; exact saved-record match |

The visible app independently confirmed Noisy and Universal after their successful events. These are app-state checks; acoustic receipt by the aids was outside the selected acceptance scope. Full records are in [the evidence JSON](evidence/HeySig3-sleep-acceptance-2026-10-14.json).

An earlier wake event overlapped result capture in Tasker, and another ran during continuous UI inspection. Both remained blocked and are retained in the evidence. The phone was awake with Signia open during the second case. UI inspection interference is a likely explanation: Android's UiAutomation can suppress other accessibility services by default ([Android API documentation](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)). The successful wake run made no UI inspections until after the event finished.

Those failed attempts exposed a reporting problem: Tasker can abort a Java action before its following error guard, leaving a generic worker failure. The automation guard now stores its reason before throwing. The improved foreground reason passed a fresh phone check; the allowed path is unchanged.

## Cleanup and limits

The original program and three controls were independently reread. The private saved record was restored atomically and reread byte-for-byte. Temporary sleep choices were removed, with SleepEnabled and SleepActive both 0 and both time variables empty. The initial 22:00/07:00 suggestions remain display defaults only; the user must choose and save programs and times.

The cleaned phone export was independently checked: all seven sleep-related tasks match the repository's ordered action codes, Java/argument strings, and conditions; exactly one native time profile exists per event; the acceptance caller is absent. Temporary native reference profiles, superseded duplicate profiles, the acceptance caller, and its input/evidence files were removed. Standalone profile updates require removing the previous copy first because Tasker imports profiles as new entries. Keep exactly one sleep profile and one wake profile.

Local checks cover persistence and failed writes, invalid choices/times, mapping changes, stale-global disarming, unchanged reloads, worker routing, disabled/override/cancel/foreground guards, and failure-reason retention. Broader optional automation and reliability checks remain on [the remaining-work list](https://github.com/5310H/HeySig/blob/HeySid3/docs/HeySig3-remaining-work.md).
