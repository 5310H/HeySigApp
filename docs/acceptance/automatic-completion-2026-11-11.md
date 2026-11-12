# Automatic completion follow-up — November 11, 2026

Owner requested continuation of the checklist with the manufacturer research
considered. The audiologist's program configuration has not arrived; that item
remains open. Research/video-access limits remain recorded in the
[research review](../research/hearing-aid-concepts-2026-11-11.md).

## Focused implementation

Initial status update: pushed application commit
`190c832933d16b98390f82d4f75cd8a9d191f9e4`:

- Show the active microphone, masking estimate, hold/confirmation/cooldown status,
  program considered and last program requested. In-progress requests use the
  pending target retained by the decision state, including playback priority holds.
- Distinguish a request from confirmed aid reception or improved understanding.
- Put watch Automatic On/Off above status text; verify its initial viewport
  position in round/large-font contexts.
- Separate explicitly requested paired lifecycle checks from recorded native
  fixtures; native mode always runs fixtures, and skipped fixtures are labelled
  as skipped. Unknown test modes fail before capture. Fixture progress is visible
  in instrumentation output.

No detector weights, speech/noise decision thresholds, microphone timing,
permissions, saved assignments or fitting were changed. No wearer learning,
recording, enrollment or enable lock was added. Test inputs use the existing
isolated fitting and a transport that cannot emit sound.

## Results

The first paired attempt on earlier Build e6b3f5b passed all six native fixtures
again but failed at the watch unlock/focus check. Its phone ADB connection also
expired. No paired-transition pass is claimed for that attempt. The phone was
reconnected using the owner's replacement connection port.

The preceding status change `1a80d9b` passed
[cloud run 43112159417](https://github.com/5310H/HeySigApp/actions/runs/43112159417).
The final pending-label correction is tracked by
[cloud run 43115479360](https://github.com/5310H/HeySigApp/actions/runs/43115479360).
Device results are added only after the matching artifact passes and runs.

### Build 190c832 device results

Both devices upgraded in place with all 13 phone and one watch user-preference
files unchanged. Phone SDK 37 smoke checks passed. Cloud results: 35 shared tests,
zero failures/errors/skips; lint zero errors (phone 35 warnings, watch 23).
All packaged model/native entries compared unchanged with the earlier fixture
candidate (phone 41 entries, watch 25). Fixture mode was explicitly skipped during
paired tests; previous native consistency evidence is retained separately.

| Check | Observed result |
| --- | --- |
| Controlled paired transitions | PASS: six accepted scalar samples, three silent requests, Noisy → Universal → Very Noisy; phone RECORD_AUDIO inactive. |
| Real capture and phone notification Off | PASS: two actual watch captures, two phone-accepted samples, 8.959s watch inference CPU, Off stops both sessions; zero silent plays. |
| Manual priority | PASS: two watch captures/accepted samples, 9.223s watch inference CPU, isolated manual request stops both sessions; one silent manual play. |
| Watch process loss | PASS on phone: four accepted samples before deliberate watch test-process termination; contact timeout stops authority; zero plays. Watch instrumentation reports “Process crashed” because the host deliberately stopped it, not a passing watch runner. |
| Reopen without On | Both apps reopened with no Automatic service; RECORD_AUDIO showed completed durations rather than an active operation. |

These runs used foreground screens, not screen-off capture. All completed runs
retained byte-identical saved user settings. Short CPU/battery readings under ADB
and active screens are not sustained endurance or isolated feature power costs.

Failed attempts are retained: watch ambient/foreground focus failures; generic
phone discovery registration failure before capture; and an ineffective package
AppOps-denial setup while the UID foreground mode continued to permit capture.
That denial attempt timed out, was cleaned up, restored the original package
mode and preserved all settings. A subsequent UID-denial attempt failed discovery
before applying a block, so no UID change occurred in that attempt. None counts
as microphone-denial acceptance. Starting a fresh session recovered a connection
and produced the capture pass above, but repeated registration failure still
required a lifecycle follow-up.

### Restart lifecycle follow-up

Pushed application commit `9a23fb866a6542cff6fd84126836d86fdfbe5ed4` serializes
capability add/remove tasks across sessions, recognizes only the SDK's matching
already-added/already-absent statuses as idempotent, and retains all other errors.
Discovery is not command authority: selected node, new session, one-use grant,
sequence/profile/age and executor checks are unchanged. Old queued start intents
no longer erase a newer explicit start token; unmatched intents stop only their
own service start ID. No automatic restart or microphone retry is added.

The phone test now waits for actual discovery readiness. Fake-SDK regression
tasks cover operation order, cancelled registration, duplicate/missing states
and failure recovery in the cloud smoke runner. Explicit rapid On/Off/On device
checks are separately selectable. The two new task-based classes passed a small
local Kotlin type check against the existing pinned SDK dependencies before push.
The exact SDK status of earlier generic device failures was not logged, so they
are not retroactively labelled as a measured duplicate-capability error.

Official API references: [capability operations](https://developers.google.com/android/reference/com/google/android/gms/wearable/CapabilityClient),
[wearable status codes](https://developers.google.com/android/reference/com/google/android/gms/wearable/WearableStatusCodes)
and [task continuations](https://developers.google.com/android/reference/com/google/android/gms/tasks/Task).

Build 9a23fb8 passed [cloud run 43120776753](https://github.com/5310H/HeySigApp/actions/runs/43120776753),
including the new fake-task lifecycle regression on the emulator, 35 shared tests,
both apps/test packages, lint and pinned artifact checks. Both devices upgraded
in place with unchanged saved settings. A device attempt reached phone discovery
readiness after explicit pending-start cancellation/restart, but again failed
watch foreground focus; it does not establish a paired pass for this build.

Test-only follow-up `c53746edc5c6b64cc6dc1b3579be002034967f2b` requests screen
wake and a setup-only keep-screen-on flag before the test Activity's creation.
It clears those flags before deliberately sleeping a device in screen-off tests.
Actual window focus, unlock and microphone permissions remain required. This
changes test setup rather than production capture. See
[Activity screen wake](https://developer.android.com/reference/android/app/Activity#setTurnScreenOn(boolean))
and [start-ID-specific service stopping](https://developer.android.com/reference/android/app/Service#stopSelf(int)).
The matching [cloud run](https://github.com/5310H/HeySigApp/actions/runs/43122852859)
passed, including the lifecycle regression. Both devices were updated in place to
**Build c53746e · Nov 11, 2026**; all 13 phone and one watch user-preference
files matched before/after hashes. The separate HeySig installation was retained.

The matching physical paired test passed explicit rapid On/Off/On, six accepted
controlled watch samples and three silent executor transitions (Noisy → Universal
→ Very Noisy). Phone microphone worker and Android RECORD_AUDIO stayed inactive.
The watch passed 192dp round layouts at font scales 1 and 2. Native inference was
explicitly skipped in this paired run; earlier pinned-fixture results remain
separate. A deliberately terminated armed phone test process was recorded before
the next fresh explicit paired start; no automatic restart was requested.

### Build c53746e real capture and recovery

- After deliberately terminating the armed phone test process, the next explicit
  paired start passed with rapid restart enabled on phone and watch. Four actual
  watch captures reached the phone; its microphone remained inactive.
- Temporarily setting the watch UID RECORD_AUDIO AppOp to ignore caused invalid/
  silent capture detection and session shutdown. Phone and watch passed, no aid
  output occurred, and the exact prior UID foreground/package allow modes were
  restored. This tests recording access loss, not notification-permission loss.
- Both screens-off watch capture passed: two actual watch captures reached the
  phone, phone microphone remained inactive, and the watch notification Off
  action stopped both sessions. Watch inference CPU was 2.839739112 seconds for
  this short run; this is not endurance evidence.
- A subsequent phone notification Off repeat could not arm because the phone
  was locked after the screen-off case. It failed before capture and preserved
  settings; unlock is required for another explicit start. Earlier Build 190c832
  foreground passes remain separate. After owner unlock, the final-build phone
  notification Off repeat passed: two actual watch captures/phone samples, no
  phone microphone use or aid output, and both sessions stopped.

- Final-build manual-priority repeat passed: two actual watch captures reached
  the phone; an isolated manual request used the silent executor once, stopped
  both sessions, and preserved every real user-preference file.

- Final phone silent smoke passed on SDK 37. Reopening each development app
  without pressing On left Automatic/capture services absent and no running
  RECORD_AUDIO operation reported. No aid command was sent during this check.

### Owner-confirmed physical reception

The wearer confirmed readiness and Universal as the starting program, then
explicitly authorized restoring existing saved per-program targets. Through the
normal HeySig Dev program selector and shared validated executor, Noisy was
requested, completed, then Universal was requested and completed. Universal
restored its existing saved targets. No fitting, role assignments, routine choices
or saved targets were edited. The owner reported: **“Heard both changes; back in
Universal.”** This establishes reception for this short manual sequence, not
automatic detector accuracy or improved speech understanding. Unlike silent
tests, this authorized sequence legitimately updated remembered requested state.

## Remaining evidence

Audiologist program configuration, listening benefit, independent field accuracy, sustained
battery/thermal measurements and release/Play requirements remain separate.
Optional program-comparison UI is proposed work, not implemented by this update.
