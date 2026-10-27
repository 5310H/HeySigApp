# Pixel Watch connection work — October 26, 2026

The owner reports phone voice commands working without apparent issues. This
is user-reported phone evidence, not watch acceptance.

Both the Pixel phone and Pixel Watch 3 were connected over authorized wireless
ADB. Both have separate development and release-candidate HeySig packages.
No APK was installed, no app data was cleared, and no fitting, requested aid
levels or restoration snapshot was edited.

## Checks performed

- The existing development relay was provisioned to the tested development
  phone and watch. Its phone loopback forward and foreground Watch Wi-Fi
  switch were enabled. A status-only request returned a FINAL reply with the
  phone's remembered program and levels. This did not emit aid control tones.
- All six automated relay checks passed. The configured Chromebook LAN relay
  address timed out, so this does not establish watch-to-relay acceptance.
- The installed direct-connection phone candidate already contained a fitting
  and selected Pixel Watch 3. Its remembered levels differ from the tested
  development phone; neither copy was changed or merged.
- The existing selected watch was enabled through the phone candidate's
  displayed Data Layer disclosure. The visible phone status confirmed
  “Enabled while this phone stays open and unlocked.”
- The owner was asked to select Connect phone and Read status on the watch.
  The result and actual watch speech recognition remain pending.

The installed direct candidates predate the latest tested development voice
implementation. Enabling their connection does not establish feature parity.
The attempted pinned Gradle shared-test/watch-build invocation stopped during
configuration because the SDK path in local.properties no longer exists.
No successful fresh build or shared-test result is claimed for this session.

## Remaining work

Verify watch status and a spoken read-only command, restore the Android build
environment, and update/validate matching direct candidates before claiming the
latest phone voice behavior on the watch. Preserve and review existing fitting
and settings during any migration. Physical aid-control acceptance, connection
loss/recovery and confirmation tests remain required. Nothing here establishes
Play readiness or submission approval.


## Watch speech completion work in the Kotlin app

Continued in `/home/kjgerhart/HeySigApp`, not the historical Tasker checkout or
its nested scaffold. Preserved the existing pending changes to the release watch
and connection guide. The release watch now sends a freshly recognized command
directly after one Data Layer reconnect, without a separate Review/Send dialog.
The shared phone parser still receives all bounded recognition alternatives;
existing ambiguity/restoration confirmations and one-use phone grants remain.

Added `WatchSpeechHandoff` in shared Kotlin. A requested recognition must return
within two minutes. Returned text is ephemeral, bounded to five alternatives of
1–512 nonblank characters, and expires after 15 seconds including reconnect.
The handoff is consumed once. Cancellation, invalid results, explicit disconnect,
Stop, a later capture, normal navigation after capture, and destruction discard
pending text. No transcript persistence, background microphone, command retry,
new aid transport or fitting migration was added.

Shared regression results: 18 JUnit checks passed, including three new speech
handoff checks for one-use delivery, preservation of alternatives, expiry during
reconnect, cancelled/unsolicited/late results and invalid input. These are logic
checks, not evidence that the watch microphone recognized the user's speech.

The default Maven resource compiler cannot execute on this ARM Chromebook. The
local build uses the already installed ARM `aapt2` override and disables SDK
auto-download. These are local command-line options; production build settings
and target SDKs were not changed. Build/install and real speech results are
recorded below when completed. No physical hearing-aid control is used for the
read-only voice/status acceptance.

### Updated watch artifact

`wearApp:assembleRelease` passed, including release lint-vital checks. The
updated `com.heysig.app` watch candidate (version code 2000003) installed with
`adb install -r` successfully; no app data was cleared. APK signature verified
as the existing HeySig Upload identity, SHA-256
`11e06091042c782f1c4c04fe786d9ff5e7177a307c575080a0c3071131934a5a`.
APK SHA-256: `0863ab30bf6755db0820f74e6bbc6360c18666556c3113bcd9190d77d4a85658`.
This is a development acceptance candidate, not a published Play release.

### Matching phone artifact

`androidApp:assembleRelease` passed, including release lint-vital checks.
Phone package `com.heysig.app`, version code 1000009 (0.9), targets API 36;
watch version code 2000003 targets API 35. Both APK signatures verify with
the same existing upload signer. Phone APK SHA-256: `0bcc90aa0566f211191e41a15781423b76a734a3efff9601e740d8bd04a9d095`.

Before the phone update, the updated watch completed a real Data Layer hello
and Read status request/reply against the previously installed direct phone
candidate. The watch displayed the phone's remembered Sleep/program 2 and
unknown/unset slider values. Those were existing candidate settings; they were
not copied from or reconciled with the separate development app. The test
requested no aid playback. An idle pause disconnected the watch as designed;
the successful status check used an uninterrupted foreground session.
Real microphone recognition and a fresh post-update status test remain separate
acceptance checks. Installed watch recognition handlers include Google
WearRemoteInputActivity and GoogleTTSActivity; availability alone is not voice
acceptance.

### Post-update read-only acceptance

The matching phone APK installed successfully with `adb install -r`; no app
storage was cleared. Reopening showed the existing Sleep selection and the
preserved Pixel Watch 3 selection. The same selected watch was explicitly
re-enabled through the phone disclosure. After both updates, Connect → Pixel 9a
→ Read status completed successfully and returned the same remembered
Sleep/program 2 and unset slider fields. These are last-requested app values,
not live hearing-aid telemetry. The checks did not request hearing-aid playback.

The owner has been asked to tap Speak and say “status” for the real microphone
recognition test. Until that result is recorded, connection/build/logic checks
are accepted within the scope above, while actual watch speech acceptance
remains pending. No direct program/slider/battery command is sent as part of
this test. Existing physical aid acceptance is not extended by these results.

### Owner-confirmed watch speech acceptance

After both candidate updates, the owner tapped Speak on the Pixel Watch 3,
said “status”, and reported: **“It recognizes status and shows the reply.”**
This confirms actual watch recognition, fresh speech handoff, phone processing
and a displayed watch reply for the read-only status command. No extra
Review/Send screen was required. The foreground phone connection prerequisite
was included in the test instructions. The test requested no aid control tones.

This supersedes the pending microphone/status acceptance notes above for this
specific path. Spoken TTS output, ambiguous/restore confirmations, disconnect
recovery during recognition, large fonts and physical aid-control commands are
not newly accepted by this test. No Play submission or approval is claimed.

## Follow-up: simpler shake voice, including screen asleep

The owner accepted basic recognition but reported excessive prompts and asked
for shake → prompt → command → result, with watch speech following the phone's
settings. The owner explicitly requested screen-asleep operation too. Earlier
Activity-based status acceptance does not establish this new session's physical
microphone or screen-asleep behavior.

Implemented a separately documented microphone/motion foreground session,
started visibly after a one-time explanation and Android permissions. It owns
wake-up sensor detection, optional prompt completion, up-to-ten-second command
recognition, fresh one-use Data Layer submission and optional result speech.
No background Activity launch or notification tap is used per shake. The phone
continues to own all hearing-aid playback and meaningful confirmations.

Both phone connection paths now attach their effective prompt/response flags to
authenticated replies. The watch uses these independent switches, not its old
local choices. Missing fields default off. Normal connection/progress states
are visual rather than spoken/modal. The saved phone is used without repeated
selection. Session notification text is generic and private, not program/level
text. Stop, revocation, Recents removal, expiry or process death ends capture;
the microphone is idle between turns. Eight-hour authority uses the already
tested shared elapsed-time session gate. Shakes are batched briefly and use
sensor timestamps rather than delivery times.

All 21 shared JUnit checks passed, including new one-turn ownership, stale
callback/expiry rejection and independent phone speech-choice cases. Phone and
watch Kotlin compilation passed. Pixel Watch 3 exposes a wake-up accelerometer
and its configured GoogleTTSRecognitionService. These checks are prerequisites,
not physical screen-asleep acceptance. The initial matching APK builds passed;
a final watch build/lint includes subsequent permission/cleanup refinements.
Phone 0.10 / version code 1000010 installed successfully with `install -r`,
preserving app storage and the existing upload signer. Watch 0.4 / version code
2000004 is the corresponding new session candidate. No fitting, requested level
or restoration snapshot is changed to test this feature.

See [background session design review](../watch-shake-voice-design.md) and
[setup](../production-watch-connection.md#shake-voice-session-october-26-update).
Final artifact and runtime results follow below when completed.

### Final watch candidate and platform checks

Final watch 0.4 / 2000004 passed `assembleRelease` and full `lintRelease`:
0 errors, 4 warnings. The APK signature matches the existing upload identity.
The initial streaming transfer timed out while the watch was PIN-locked;
no credential was entered or bypassed. After the owner unlocked it, a complete
file/push installation with `install --no-streaming -r` succeeded and preserved
app storage. Final watch APK SHA-256: `b3f9ee8528db153204320961d5a1fb4e2ab17f36d0ebd2dab2e505a3a12d98da`.

The owner then started the new voice session. Android reports
`WatchVoiceSessionService` running foreground with microphone and special-use
types, started from a TOP/visible app. RECORD_AUDIO app-ops show a completed
3.466-second capture and no running marker at inspection. This establishes
session start and a released capture, not by itself correct recognition, spoken
output or screen-asleep shake acceptance. Those await the owner's report.

The final phone UI candidate 0.11 / 1000011 also built successfully, including
release lint-vital checks. It moves shared prompt/response switches above
hands-free setup and removes the stale separate-watch-choice description.
Installation is deferred until the live shake test is finished to avoid
interrupting it. Existing phone speech choices were not changed by automation.

### Owner failure report and active-session lock fix

The owner reported no shake response, then clarified that the watch remains
locked. The 0.4 service explicitly rejected capture while Keyguard was locked,
including before haptic acknowledgement. Android diagnostics showed the service
still foreground and its wake-up accelerometer registered with sensor access;
there was no evidence of sensor deregistration. The lock rejection was therefore
a definite blocker to the clarified requirement.

Watch 0.5 removes that rejection for an already-authorized session. It does not
unlock the device, dismiss Keyguard, enter credentials, show a lock-screen Activity
or change lock settings. Unlocking is still required to start a new session;
its bounded authority may then capture after a shake while the PIN remains
locked. Permission/notification revocation and session expiry still stop capture.
Shared WatchVoiceAccess tests explicitly cover locked active capture, locked
startup rejection, absent authority and revoked permissions. The session Stop
path only requests a phone Stop when a command may be pending, rather than when
merely connecting, prompting or capturing.

This corrects the earlier locked-capture exclusion in this record and the design
review. The owner's clarified request authorizes voice during the active session
while locked, not PIN unlocking. Physical locked-watch shake acceptance remains
pending the 0.5 update and retest.

### Owner retest: 0.5 still gives no shake response

Owner reported “speech off on watch”, then “nothing on watch”, and confirmed no
vibration. Read-only checks showed WatchVoiceSessionService still foreground,
wake-up accelerometer registered with sensor access and no suspension, and only
the initial approximately 5.3-second microphone capture. No crash was reported in
the inspected system error buffer. Phone UI previously showed both speech
switches off. Shake detection is the current suspect, not a confirmed root cause.

Candidate watch 0.6 adds a wrist-specific gate, faster motion sampling and
non-persistent aggregate service diagnostics. Physical retest remains pending.

Watch 0.6 validation: Gradle shared tests, watch release APK and full release lint
completed successfully in 10m37s. All 27 shared JUnit checks passed (zero failures,
errors or skips); lint reported zero errors and four warnings. Verified package
com.heysig.app, version 0.6/code 2000006, target 35 and unchanged signing certificate
SHA-256 11e06091042c782f1c4c04fe786d9ff5e7177a307c575080a0c3071131934a5a.
APK SHA-256: 31f8d90960a1d82bd0af7f9ea4f62ce7fa480b1baedb2333608082e011e9df8d.
Initial installation attempt could not reach the watch; reconnect to its current
wireless debugging address before installing. Installation and motion-counter
verification are pending. No successful physical shake result is claimed.

Watch 0.6 subsequently installed successfully through the updated wireless
debugging port, preserving app storage. Package manager confirmed version
2000006/0.6. Starting Speak created the foreground session. Its new service dump
reported active=true, stage=LISTENING, 187 motion samples, maximum 1.096 g,
last sample age 67 ms and zero shake triggers. Both effective phone-owned
speech switches were true. Sensor delivery and preference propagation are
confirmed for this fresh session; deliberate-shake and locked-watch acceptance
remain pending.

Owner 0.6 retest: vibration occurs but no spoken result; owner clarified the
watch is completely silent after vibration, including no initial prompt.
Service diagnostics showed two triggers, 4617 motion samples, maximum 3.231 g,
IDLE and both speech choices true. App-ops showed a recent 8.299-second audio
capture. Shake detection and entry into recognition are confirmed; successful
recognition, command result and audible TTS are not yet established. Current
media volume was 5/15, unmuted and routed to speaker. Google TTS is installed
and enabled. Those facts do not establish that HeySig initialized a usable voice.
Next diagnostic is the visible final message and TTS readiness/callback state.

## Paused checkpoint requested by owner

Owner read the final watch message as No speech recognized. The 0.6 build
therefore confirms wake-up shake triggering and bounded microphone access, not
a successful voice command or audible prompt/reply. No new hearing-aid commands
were sent for verification. Both phone-owned speech flags were true. Current
watch media audio was unmuted at 5/15 on speaker. The default recognition
provider is GoogleTTSRecognitionService in com.google.android.tts; the installed
Google synthesis engine was visible in system text-to-speech settings.
No voice data, engine or audio setting was changed during inspection.

At the owner request, stopped further troubleshooting and force-stopped the
watch app to end its foreground motion/microphone session. A fresh session
must be started from the unlocked watch when resuming.

Resume with speech-engine diagnostics: record non-content initialization and
language readiness results, synthesis enqueue/callback error codes and
recognition error codes. First establish audible system/app sample speech and
a deliberate foreground status command, then repeat screen-asleep/PIN-locked
acceptance. Do not infer transcription success from app-ops microphone access.
Do not publish this checkpoint as a working end-to-end voice release.
