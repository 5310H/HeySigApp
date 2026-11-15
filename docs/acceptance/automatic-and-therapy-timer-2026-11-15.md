# Automatic routing and Therapy timer — November 15, 2026

Owner field report: a very noisy Automatic session selected TV and did not return
when noise ended. Owner confirmed Watching TV was On. This is a reported physical
failure, not detector acceptance. No recording or scalar capture from that event
is available, so its original detector labels and reason for holding cannot be
reconstructed.

Source review found Watching TV overrode both MODERATE and HEAVY targets with TV.
The correction routes CLEAR to TV only in explicit TV context, MODERATE to saved
Noisy and HEAVY to saved Very Noisy regardless of context. QUIET returns Universal;
UNKNOWN holds. There is no television-source recognition. Existing fitting-bound
role choices and six legacy assignments are retained. A new TV behavior disclosure
is shown even when the earlier TV disclosure was accepted.

A second policy issue reset agreement when quiet and clear alternated despite
both selecting Universal outside TV context. Confirmation now compares intended
program targets. Universal returns use two fresh agreeing checks; other entries
keep saved confirmations (at least two). Unknown, stale or interrupted evidence
clears agreement. Cooldown, manual/Therapy priority, pending tokens, duplicate
suppression, executor and all existing capture limits remain. Detector/model
weights, thresholds and strict quiet corroboration are unchanged; uncertainty
never becomes quiet simply because a timeout passed.

Therapy now offers 5–60 minute timers in five-minute increments. Owner explicitly
selected automatic switching to saved Wake at expiry. See the
[timer design](../architecture/therapy-timer.md) for authorization, lifecycle,
recovery, no-retry and data-flow details.

Pre-build checks passed: all `tools/check-noise.sh` suites, including the full
TV → Very Noisy → Noisy → TV → quiet policy sequence; quiet-room recorded
fixtures and stale/unknown/cooldown/protected-program regressions; all
`tools/check-sleep.sh` checks including twelve durations and invalid/overflow
rejection; `tools/check-manual.sh` (433 voice cases); and 37 Gradle shared tests.
Phone Kotlin compilation passed. Phone test-package and Wear compilation,
final APK builds, lint and silent device checks are still pending at this source
commit. Initial local build failed on the x86 resource compiler; the documented
ARM aapt2 override is used for subsequent local validation.

Added silent Android acceptance covers the timer's elapsed deadline, every
duration, early/duplicate/cancelled/late/manual/permission-blocked events,
reopening without extension, one-use Wake claim through the shared executor,
saved routine/snapshot preservation, scheduling failure and interrupted-claim
recovery. These test cases are not yet recorded as device passes here.

The supplied phone ports 38443 and 38357 at 192.168.69.130 did not connect;
phone installation has not occurred. Read-only emulator settings fingerprints
were recorded before any update. Existing unrelated local research and
acceptance artifacts are preserved outside this change.
Actual noisy-room performance, TV intent, quiet-return reliability in the reported
room and physical timed Wake reception remain unaccepted until measured.

## Initial CI result and final status follow-up

[CI for e4eb48e](https://github.com/5310H/HeySigApp/actions/runs/43748307475)
passed phone/Wear debug and test-APK builds, both debug lint tasks, 37 shared
JUnit tests, shell regressions, native APK pins/alignment and the silent API 36
emulator runner. Timer acceptance passed with fake alarms and transport: every
duration, single Wake request, cancel/late/revoked/manual cases, saved-state
preservation and interruption recovery. The runner also exercised real compact
watch layouts in isolated round contexts at font scales 1 and 2. This did not
start microphone capture or verify physical aid reception.

Final review corrects three timer status issues: append the timer arming outcome
to the final visible/spoken playback result (including a failed-arm instruction
to use Wake now); refresh controls after the one-shot timer finishes; and make
the saved-routine preview describe timer mode rather than a daily automatic plan.
An isolated acceptance assertion checks that timer preview keeps daily switches
Off. Follow-up compilation and exact final artifact checks are recorded separately
from the initial CI result above.
