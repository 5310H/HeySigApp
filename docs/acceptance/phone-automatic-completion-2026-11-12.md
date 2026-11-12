# Phone Automatic completion follow-up — November 12, 2026

Owner asked to finish the phone checklist and selected TV with a nearby speaker
as the first listening situation. Existing fitting, saved levels, routine choices,
Automatic roles and live detector rules remain unchanged.

## Implementation

- Optional Noise → Compare saved programs: explicitly review A, B and a return
  program. No command is sent by opening, reviewing or starting the comparison.
  Try and Return use the existing manual executor and saved per-program targets;
  each request stops Automatic. Therapy is excluded, stale fitting is rejected,
  pending playback blocks duplicate requests, and audio readiness is rechecked.
- Four independent categorical observations (understanding, effort, comfort,
  awareness) remain in Activity memory only. Leaving/clearing/destroying the
  screen discards them. They do not train the detector or gate Automatic.
- Return is a visible manual action near the top. Leaving does not send a return
  command. No microphone, permission, dependency or service is added by the UI.
  See [data flow and behavior](../program-comparison.md).
- Added explicit full-native phone interruption instrumentation: real bounded
  microphone/YAMNet/RNNoise analysis, isolated fitting/preferences and a transport
  that cannot emit sound. Scalar estimates, counts, timing and silent requests
  are emitted only by the explicitly invoked test runner, never production analytics.

## Validation and limitations

Manual control regressions passed locally, including 433 voice phrase cases and
fitting/import/persistence guards. Both 0b55016 and b7dddd5 cloud candidates
passed 37 shared tests with no failures/errors/skips. Two shared comparison unit tests check explicit
return identity, independent feedback/clear and changed-fitting/unreviewed-target
rejection. Android UI regression uses injected confirmation/silent callbacks to
exercise review, Therapy exclusion, A/B/return, pending/audio/stale guards and clear.

[Build 0b55016 cloud run](https://github.com/5310H/HeySigApp/actions/runs/43129874950)
passed shared tests, both builds/lint, pins/alignment and API 36 emulator smoke,
including the comparison UI check. Both physical devices upgraded in place;
13 phone and one watch user-preference files matched before/after hashes.
The source commit was pushed before APK generation/installation.

The first phone test attempt lost its ADB connection before capture. A second
attempt exposed a missing instrumentation manifest registration, also before
capture; saved preferences were unchanged and no microphone/service remained.
Correction `b7dddd5` registers the runner. Its [cloud run](https://github.com/5310H/HeySigApp/actions/runs/43254909222)
passed all 37 shared tests, both builds/lint, native validation and API 36 smoke.
Both devices upgraded in place with all real user-preference hashes unchanged.
The installed label is **Build b7dddd5 · Nov 12, 2026**. No interruption pass is attributed to these failed setup attempts.

All 48 phone and 25 watch packaged asset/native entries are byte-identical to
Build 4a25d01: no added, removed or changed entries. Model weights, bands and
licenses did not change. The physical Build 0b55016 phone smoke/comparison
regression passed on SDK 37 at font scale 1.5 and 411dp width; real preferences
matched afterward. The actual comparison Return button was visible near the top
of the phone viewport. Full watch behavior is not requalified by a phone test. Final installed phone and
watch APK hashes matched the signed b7dddd5 artifacts, both labels were HeySig,
and only the current app/test packages remained installed. The watch briefly
went offline during final hash inspection; reconnecting its existing port allowed
the check to complete. This was not a capture/accuracy test failure.

## Native physical-phone interruption checks — Build b7dddd5

- Notification Off: two complete full-native samples; stopped both service and
  microphone, zero silent requests, real settings unchanged.
- Microphone privacy: after two full-native samples, enabling the phone-wide
  microphone privacy toggle caused invalid/silent capture rejection and shutdown.
  The prior toggle state was restored and verified. Shell toggle semantics were
  checked against [AOSP SensorPrivacyService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/sensorprivacy/SensorPrivacyService.java). No service/microphone remained
  active and real settings were unchanged.

- Microphone permission removal: after two full-native samples, Android terminated
  the test process on revocation. This is expected permission-change process loss,
  not a passing instrumentation result. The host verified capture/service absent,
  restored the exact prior grant/flags, and matched all real preference hashes.
- Notification permission removal: Android likewise terminated the test process;
  service/microphone absence, exact permission grant/flag restoration and real
  preference preservation were verified. No automatic restart was observed after
  restoration. Reopening is checked separately.
- Explicit process stop: host deliberately force-stopped the isolated test after
  two samples. Expected process termination was recorded; service/capture remained
  absent and real preferences matched. Reopening phone and watch without On did
  not restart capture. This is not a maximum-session-expiry or recents-swipe test.
- Manual priority: after two full-native samples, one isolated manual request used
  silent transport and stopped capture. Real preferences matched afterward.
- Real call interruption was explicitly deferred by the owner; it remains open.

These are lifecycle checks. The estimates in uncontrolled capture windows have
no independently labelled wanted speaker or expected severity and are not accuracy
measurements. The runner's `finalStatus` is a retained service-object summary;
`stop`, service inventory and AppOps records establish actual stopped state.

## Owner-confirmed reception

The owner explicitly authorized existing saved settings for Very Noisy → Universal.
Normal UI requests on Build 4a25d01 completed both stages; fitting, saved snapshot
and per-program target hashes were unchanged across that sequence. On November 12
the owner confirmed **“Heard both; back in Universal.”** Together with the earlier
Noisy → Universal result, reception is confirmed for the three daytime roles in
these manual sequences. This is not detector or listening-benefit qualification.

## Preliminary listening comparison

On installed Build 0b55016, the owner reported TV playing and another person ready.
The instructed sequence was Universal baseline, Noisy for about 30 seconds, then
Return to Universal using the new comparison controls. The app reported both
requests completed. Existing saved per-program levels applied; any difference is
not attributable to noise processing alone. Owner observations: **understanding
about the same; effort, comfort and awareness uncertain; heard both changes and
back in Universal.** No benefit advantage was demonstrated in this trial.
This is one unblinded, unrecorded listening trial, not a measured word score,
controlled target-speaker SNR or general field-accuracy result.

## TV-only native control

The owner confirmed TV-only readiness with no nearby conversation. The explicit
60.089-second observation used five bounded three-second captures (15 seconds of
captured audio, discarded in memory), full native analysis and the isolated silent
executor. All five outcomes were UNKNOWN holds: mixed media, isolated/interrupted
sound, or disagreement. Zero program requests; the isolated Universal role stayed
selected. Total native analysis CPU was 1.156119302 seconds. Real preference hashes
matched afterward and capture/service stopped.

This is one new owner-labelled environment, not a blind corpus, target-speaker
recognition, a reliable false-switch-per-hour estimate or evidence of improved
moderate coverage. Test confirmations=2 and cooldown=10 seconds are isolated test
settings; the owner's assignments/timing were not altered. TV content, distance
and acoustic level were not measured or recorded. No raw audio was retained.

## Ten-minute screen-off observation

Final-build native observation lasted **600.117 seconds** with the phone screen
reported off. It completed 43 three-second samples (129 seconds captured and
processed in memory), with 40 UNKNOWN holds and three HEAVY estimates. No program
request occurred. Native analysis CPU totaled **18.637242492 seconds**; this is
analysis-thread CPU, not whole-app CPU or a power measurement.

| Endpoint | Before | After |
| --- | ---: | ---: |
| Battery percentage | 88% | 88% |
| Battery charge-counter raw value | 4,182,500 | 4,155,000 |
| Battery temperature raw value | 261 | 251 |
| Android thermal status | 0 | 0 |

The phone was discharging, not externally powered. Percentage resolution obscures
small changes; charge counter decreased by 27,500 reported units. There was no
matched Automatic-Off baseline, no continuous thermal maximum and no controlled
acoustic label during this run. Do not extrapolate all-day life or attribute the
whole-phone drain to Automatic. Three HEAVY estimates are unlabelled, not measured
errors or successes. Isolated test confirmations/cooldown differ from saved owner
timing, which was untouched. This is a bounded lifecycle/resource pilot, not field
accuracy or sustained battery qualification.

The runner stopped at the requested interval, Android microphone activity and
Automatic services were absent afterward, and every real user-preference hash
matched. Raw audio/transcripts were never retained. A full maximum-session expiry,
recents-swipe behavior and longer representative endurance remain separate.

## Still separate

Independent unseen-scene accuracy, improved wrong-switch/moderate coverage,
representative quiet/placement controls, audiologist program configuration,
sustained endurance, actual calls/audio interruptions and release/Play acceptance
must be supported by their own evidence. No release submission is prepared.
