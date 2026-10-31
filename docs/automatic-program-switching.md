# Automatic program switching — development build

Open **Noise → Automatic program switching**. The new semantic monitor is
separate from the existing relative-level experiment and its saved calibration.
Neither fitting nor legacy calibration is migrated or erased.

1. Assign your configured program names to Quiet, Conversation, Small Group,
   Restaurant, Large Group and Non-Speech Noise. Leave unused environments
   unassigned. No program names or fitting values are assumed. Renamed/replaced
   assignments need review; they never redirect silently.
2. Choose cooldown (0–600 seconds), stability (0–120 seconds), manual hold and
   whether successful manual program changes restart cooldown. Defaults are
   120-second cooldown, 15-second stability and 120-second manual hold. Save.
   Stability requires repeated fresh samples; a 30-second sampling interval can
   take longer than a requested 15-second stability window. Zero cooldown also
   disables a cooldown that was already running; manual hold remains separate.
3. Start monitoring, review the microphone explanation and grant permission
   only if you want audio sampling. Keep HeySig visible and unlocked. Stop,
   leaving/locking, denial/revocation or session expiry ends capture. Calibration
   is optional for this path.
   If an enabled Sleep schedule is manually paused, that pause keeps priority;
   monitoring does not resume the Sleep schedule. Review/resume that routine
   explicitly when appropriate before expecting automatic decisions.
4. Automatic On arms decisions; it does not start monitoring. This build still
   blocks **real automatic dispatch** pending physical acceptance. This gate is
   separate from optional acoustic calibration. Monitoring shows experimental
   environment/event confidence and the last requested program. No electronic
   aid readback is available; completed tones do not confirm reception.
5. Use **Simulate an environment · no microphone** to exercise timing and
   assignments without audio or aid commands. Enable automatic decisions and
   save assignments first. The screen explicitly labels the simulation baseline;
   results never become actual requested settings. **Start simulation** instead
   uses the microphone model with command-free simulated dispatch. Simulation
   cooldown is isolated from the live cooldown.

The optional legacy **Learn sound levels** flow is retained unchanged for the
level-based preview. Its six increasing levels are not the six semantic
environments. Do not make unsafe sounds to calibrate it.

## Voice and shake

On the Automatic screen, configure phone shake On, sensitivity and a 3–15-second
recognition timeout. Three separated peaks, hysteresis and a ten-second trigger
cooldown reduce accidental activation. **Speak a command** works without shaking.
This control uses no wake word and works only while HeySig is foreground/unlocked.
Sampling stops before recognition. On-device speech recognition is preferred;
fallback recognition providers may use a network. Read its separate explanation.
Your existing independent prompt/reply switches remain authoritative. Visible
listening/error status and vibration work when speech output is Off.

Commands: “Automatic on/off”, “Start/Stop monitoring”, “Quiet mode”,
“Conversation mode”, “Small group mode”, “Restaurant mode”, “Large group mode”,
“Noise mode”, “Cooldown two/five minutes”, “What's my current program?” and
“What's my status?”. “Normal mode” resolves only to an actual configured program
named Normal; it has no universal mapping. Manual program commands retain the
existing validated executor and stop monitoring. Explicitly restart after a
manual selection; the saved manual hold/cooldown still applies. Status queries
do not request a program. Conflicting speech alternatives send nothing.

## Watch

Use the existing paired selected-node Watch connection with matching app
variant/signing identity. End the phone hands-free session and keep the phone
app visible/unlocked for this monitor. On the watch choose **More → Environmental
monitoring → Connect / refresh status**, then **Start monitoring**. The watch
explains its microphone use and requests access only at your request. Starting
monitoring ends watch shake voice so the microphones do not conflict.

Phone settings select 15/30/60-second intervals, 3/5/10-second samples and
15/30/60-minute timeouts or Until stopped. Defaults are 3 seconds every 30
seconds for 30 minutes. Watch capture/classification is local; only a compact
classification crosses Data Layer. The phone owns settings/programs/dispatch.
No raw audio or transcript is transferred by monitoring. Google's transport may
route messages through encrypted Google servers. Manual program controls and
Automatic On/Off are available; Voice command returns to the existing bounded
watch Speak flow. A button is the baseline; no Pixel system gesture is claimed.

Leaving or locking this watch screen stops sampling. Phone pause/disconnect,
invalid grants/session/sequence, stale samples and missing results stop or reject
monitoring. Reconnect and restart explicitly; no audio or commands are queued
or replayed. The phone detects a missing sampling heartbeat within two intervals
plus sample duration and ten seconds if a Stop packet cannot arrive. Monitoring
grants have a separate bounded inventory and cannot consume manual-command
grants. The foreground connection must be explicitly renewed if that inventory
is exhausted (4096 monitoring requests).

## Validation and known limits

Run `bash tools/check-noise.sh`, `bash tools/check-manual.sh`,
`bash tools/check-sleep.sh`, and Gradle `:shared:test`, both `assembleDebug`
and `lintDebug` tasks. On this ARM host use the documented machine-local aapt2
override. Run `python3 tools/validate-native-apks.py --variants debug` after both
APK builds. These commands install nothing and send no aid commands.

`tools/check-environment-model.py` exercises a pinned offline model on four
development-only WAVs at 3/5/10 seconds and two gains. See the
[fixture instructions](../shared/src/commonTest/fixtures/environment/README.md).
Recorded scalar outputs are regression fixtures; synthetic event-score tests
exercise all six mappings but do not establish audio accuracy.

Observed limits: Restaurant_1 is mapped to generic Conversation in all six
tested cases; babble depends strongly on digital gain, and short traffic
windows often abstain. Vacuum noise is recognized more consistently. Therefore
the initial sampling schedule and six-way mapping are **not qualified for real
automatic aid switching**. A stronger/validated overlap/context approach and
held-out quiet/conversation/group/crowd recordings remain necessary. No measured
phone/watch RAM, CPU, battery/endurance or Pixel Watch 3 runtime result is claimed.

Device acceptance still needs microphone denial/revocation/privacy switches,
pause/lock/Stop races, voice/model conflicts, watch disconnect/reconnect and
timeout, round screens at large fonts/touch targets, and model timing/memory/power
measurements. Physical aid acceptance requires explicit real-user testing;
never change programs merely to verify installation or UI. Release additionally
needs the [dated Play gates](play-store-readiness.md), final signed artifacts,
provider/data/health reviews and Play test-track evidence.

Capture access is checked before and after inference as well as during recording.
If access ends without an explicit Stop, the worker reports failure so the UI
leaves its active-sample state and stops monitoring. Device acceptance must
include a voice/audio-priority interruption during inference, then an explicit
monitoring restart; a discarded sample must never request an aid program.
