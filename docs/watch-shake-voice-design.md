# Screen-asleep watch shake voice — design review, October 26, 2026

The owner requested shake → prompt → command → result with phone-owned speech
switches, including when the watch screen sleeps. The earlier notification-tap
flow and independent watch switches do not meet that request.

## Android boundary and implementation

A motion event alone does not authorize opening a background recognition
Activity. This implementation uses an explicitly started microphone foreground
service instead. The owner starts it while HeySig is visible after a feature
explanation and runtime microphone/notification permission. It declares
`microphone|specialUse`, the microphone FGS permission, and an unexported service.
There is no background Activity launch, overlay permission, full-screen intent,
boot receiver or continuous wake-word/microphone capture.

The platform review follows official [microphone foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone),
[while-in-use start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions)
and [background Activity launch restrictions](https://developer.android.com/guide/components/activities/background-starts).
These support the architecture; real watch microphone/provider acceptance is
still necessary. Google Play FGS declarations, privacy/Data safety and release
quality checks require a separate release review before distribution.

## Operation and interruption

A wake-up accelerometer processes motion only in memory. Three peaks trigger
one owned turn. A partial wake lock is held for at most 60 seconds per turn,
not for the eight-hour session. The watch reconnects only to the already selected
phone, preferring its explicitly enabled hands-free capability when present.
No phone picker appears during a normal command. An unavailable phone yields a
visible failure and no pending command.

Authenticated phone replies carry effective Voice prompts and Spoken responses
choices. Missing fields mean off. Prompt playback completes before recognition
starts. Recognition uses the watch's installed service for at most ten seconds;
raw audio and transcripts are not retained by HeySig. That provider may process
or send audio over its own network. Recognized alternatives remain bounded,
unchanged, and pass through the phone's existing ambiguity/confirmation parser.
The microphone is cancelled/destroyed before submission. The text must still be
fresh when a new one-use phone grant is consumed. No command is retried.

The turn gate prevents double capture from repeated shakes and rejects stale
callbacks after cancellation. Prompts/results each play once according to their
phone switch. Stop first cancels local capture/speech. If a command might already
be in flight, it makes one fresh best-effort phone Stop request; it never resends
the original command. The original lookup ID is preserved. Phone connectivity
loss may leave the aid outcome unknown; playback completion is not aid receipt.

The persistent notification contains generic session/microphone state rather
than private program/level values. A displayed result remains only in service/UI
memory. Existing phone replay/result retention and recognition-provider data
practices remain applicable. Export contains saved setup/disclosure flags, not
in-memory speech. Deleting app data, Stop, Recents removal, process death or
session expiry ends the service. It never restarts after reboot. A new session requires an unlocked watch. Once explicitly started, its bounded
authority also works while the screen/PIN is locked. The PIN itself is never
unlocked or disabled. This matches the owner's clarified hands-free requirement.

## Required evidence

- Shared turn tests: one prompt/capture per turn, busy suppression, stale callback
  rejection, expiry and independently disabled phone speech switches.
- Build both matching package/signer candidates and check merged microphone/FGS
  permissions; preserve all fitting and requested-state records.
- Accept real foreground and screen-asleep shake → prompt → spoken status result.
- Verify both speech switches independently, no repeated connection/review
  dialogs, mic release at timeout/cancel, Stop and notification denial.
- Test lock, disconnect, process death and long-session battery behavior without
  automatically issuing aid controls. Until recorded, these remain pending.

## Watch shake detection follow-up

The owner reported no vibration with 0.5. Android showed the microphone foreground
service and wake-up accelerometer active with sensor access; app-ops showed only
the initial Speak capture. This supports investigating detection, but does not
prove a threshold was the sole cause. Watch 0.6 uses a watch-only gate: three
separated peaks of at least 1.8 g within 1.8 seconds, resetting below 1.3 g, with
the existing 180 ms separation and ten-second cooldown. Phone defaults remain
2.7 g / 1.2 seconds. The watch requests 50 Hz with 100 ms maximum batching instead
of about 15 Hz / 250 ms. Actual rates remain hardware-dependent.

The private foreground service exposes session-only aggregate sample count,
maximum acceleration, trigger count, sample age, turn stage and effective speech
switches through Android service dumps. It retains no raw samples, audio or
transcripts. These counters reset with the service and are not sent to the phone.
Lower thresholds and the higher rate need physical false-trigger and battery
acceptance; no successful locked-watch result is claimed yet.
