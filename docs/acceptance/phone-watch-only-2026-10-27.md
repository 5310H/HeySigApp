# Direct phone/watch connection repair — October 27, 2026

The owner reported the Watch connection screen said it was unavailable in a
development installation and requested removal of the computer runtime/relay.
The exact message was confirmed in the debug-only CompanionConnection stub.
Debug watch used a different package and signer from the phone, so the previous
debug pair could not use Wear Data Layer.

Both variants now compile one direct source set. Debug phone retains its package
and signer; debug watch uses that same identity. Release identity/signing stay
unchanged. Debug apps are labelled HeySig Dev to distinguish them from release
HeySig. No existing app data is uninstalled or wiped. Legacy result storage is
retained only for explicit privacy deletion, not used to execute commands.

Removed active relay UI, listener, exported pairing receiver, debug watch TLS
client, desktop source/build script and relay tools. Historical acceptance data
is preserved. Updated current setup/build guides and phone-owned speech guidance.

Validation and hardware installation are pending. On entry the phone connected
on port 45343. The watch's old address was unreachable; current full address was
requested. Resume device acceptance with a read-only status request, followed
by the unresolved synthesis/recognition diagnostics. Do not send aid tones just
to verify pairing.

## Build and installed-device verification

Gradle shared tests, both debug/release phone/watch APKs, and full release lint
completed successfully in 28m03s. All 27 shared checks passed with no failures,
errors or skips. Phone lint: zero errors/17 warnings; watch: zero errors/four
warnings. No AAB validation or store readiness is claimed by these APK checks.

Both debug APKs have package com.heysig.batteryprototype and signer SHA-256
16a30331844bf27b7b809fc894040d9e9a08bb52fcd307fda1af21973d9f8fdb.
Debug phone 0.12/code12 updated the existing phone installation successfully;
watch 0.7/code7 installed as its matching companion. Release APKs retain the
previous shared signer and package; they were built but not installed in this
checkpoint. Both debug DEX inventories contain Data Layer classes and exclude
the retired WatchBridge, WatchBridgeSetup and ProtoActivity classes.

The phone UI now shows real direct-pairing controls instead of the unavailable
stub. Find paired watches discovered Pixel Watch 3; selection and Enable watch
completed. The watch discovered Pixel 9a. Watch UI automation later returned
empty/stale hierarchies and the display returned to another app, so final phone
selection/status round-trip is not claimed. Owner was asked to complete Connect
then Read status manually with HeySig Dev visible on both. Speech remains
unaccepted; the development phone currently has both speech switches off.

Disabled the retired com.heysig.watchdev package without erasing its app data.
Removed the old desktop build directory, relay code/cache, and private relay
certificate/key, pairing configuration and request database at owner request.
Signing keys, phone app data and historical acceptance evidence were preserved.
A temporary bounded display-wake helper was stopped; no global lock/display
setting was changed. No aid tones were sent for this verification.

## Resume-readiness defect found after owner retest

Owner reported the watch showed Speak, then Read status said Connect phone first.
Confirmed in code: onPause invalidates ready, while onResume only reattaches the
message listener and labels the button from a saved phone ID. It sends no new
hello, so a saved selection is presented as a usable connection.

Watch 0.8 now sends a fresh correlated read-only hello after its listener attaches
when a saved phone exists and no voice service owns the turn. Connect/Speak uses
actual readiness (or the active voice service), not just a stored node ID. A new
read-only status tap may wait for hello; pause clears that pending request.
Aid-changing commands are never queued/retried during reconnect. The existing
selected node, phone gate, generation invalidation, lease and one-use command
validation remain required. Hello refreshes the phone-owned speech label too.
Manual acceptance needed: select once, read status; sleep/resume and read status
again without selecting the phone; disable phone connection and verify failure
without any aid-changing command being replayed. Build/install pending.

Watch 0.8 validation completed: shared checks, watch debug/release APKs and full
release lint all passed in 12m03s. All 27 shared tests passed; watch lint has
zero errors and four warnings. Debug APK verified as com.heysig.batteryprototype
version 0.8/code8 with the unchanged matching phone development signer. It was
installed successfully with -r, preserving the saved phone choice. The watch
was reopened; the remote screenshot returned a blank sleeping display and is
not evidence of a successful handshake. Owner was asked to reopen the watch
and use Read status without reselecting the phone while HeySig Dev remains
open/enabled on the phone. End-to-end status/resume acceptance remains pending.
Stop and malformed-reply paths also clear the transient reconnect/read-status
flags. No aid-changing command is queued during the new hello handshake.

## Phone listener found Off during missing-reply retest

Owner reported No confirmed result after watch 0.8. Read-only checks confirmed
HeySig Dev was the resumed phone Activity, no crash in the inspected buffer,
and its Watch connection card explicitly showed Pixel Watch 3 / Off. Thus the
watch had no live foreground listener to answer it despite the phone app being
open. The phone implementation deliberately closed the listener on pause and
never restored it on resume. Re-enabled the selected watch for diagnosis.

Phone 0.13 separates explicit persisted Enable from temporary live authority.
Only that choice restores the selected watch on an unlocked foreground resume.
Pause/lock still closes live authority; Disconnect/Forget revoke restoration.
An old selection alone never opts in. App destruction no longer counts as an
explicit Disconnect. Restores are silent and do not replace voice/import result
messages. Hands-free service ownership is preserved. Build/install and pause/
resume/read-status acceptance are pending; no aid-changing requests are used.

## Least-touch session follow-up

Owner explicitly requested the fewest phone/watch touches. After re-enabling
the old phone listener, owner reported Phone connected / SPEAK, then answered
yes to the Read status instruction. This establishes the repaired link responds
with the phone enabled; it is not complete speech or locked-phone acceptance.

Phone 0.13 debug installed successfully. Physical UI checks passed: Enable;
Home/reopen restores Enabled; Disconnect; Home/reopen stays Off; final Enable.
No aid-changing command was sent. Phone mic/notification permissions are already
granted; both permissions on the new matching watch package are not yet granted
and require owner approval through its normal feature setup.

Watch 0.9 reuses the existing bounded selected-phone client for resume hello,
so it automatically discovers the foreground or already-started hands-free
route without asking to choose the same phone again. Pause cancels the probe.
The first permission result also survives returning from Android permission UI
to avoid requiring a second Speak tap. Service dumps now include only non-content
TTS initialization/language/playback state and recognition readiness/error codes;
no recognized words/audio are added to storage or logs. Build/install pending.

Steady operation uses the existing explicitly started bounded phone hands-free
session and watch shake-voice session. Phone listening may be Off while its
watch link remains active. Starting services after expiry/reboot still requires
foreground owner action; neither lock is bypassed and no boot restart is added.

## Two vibration voice flow

Phone 0.13 and watch 0.9 debug builds installed. Shared tests and both APK
build variants/release lint passed. Owner selected both phone speech switches On.
The existing phone background session is active with phone listening Off. Watch
permission setup completed; live service showed microphone idle, recognition
results received, and TTS completion callbacks. Owner subsequently reported it
works but setup is unclear; this is not complete sleep/lock acceptance.

Watch 0.10 moves the short vibration from gesture detection to the recognizer's
ready callback, after the optional prompt. One longer vibration marks a received
reply or terminal error, independent of speech switches. It does not assert that
an aid setting changed. Optional prompt and spoken results still follow phone
settings. Stop cancels pending feedback. Build/device verification pending.

Watch 0.10 debug/release APKs and release lint passed (11m11s); shared checks
passed: 27 tests, zero failures/errors/skips. Watch lint: zero errors/four
existing warnings. The final debug APK was rebuilt after the spoken-error
follow-up (2m26s) and installed successfully over 0.9, preserving data and
permissions. Started the session through More → Shake to speak with capture
false: active, IDLE, motion samples arriving, recognizer idle. A read-only
Read status request then reached REPLYING with TTS playing and both phone
speech choices true. The earlier 0.9 read-only status completed TTS, and its
sensor samples continued while the watch display slept. Audio inspection
showed watch speaker, TTS/media unmuted, volume 5/15; no audio settings changed.

The short ready vibration is requested only in onReadyForSpeech. The longer
completion vibration is consumed once on a reply or terminal error; it remains
independent of speech choices. Connection and timeout errors honor spoken
responses. New physical ready/completion vibrations and full locked-session
acceptance are not claimed from host automation. No aid-changing command was
sent for these checks. The owner’s existing displayed playback is owner activity.

Known wording issue: background status can show “Listening Off” for the phone
and repeat the last playback wording. This does not mean the watch session is
off. Phone status wording was not changed in this watch-feedback update.

## Ready vibration follow-up

Owner sometimes misses the first vibration. Watch 0.11 replaces its 60 ms
default-amplitude ready cue with two 120 ms full-amplitude pulses, separated
by 80 ms. It still runs only after recognizer readiness. Completion stays
a single 140 ms pulse. No shake thresholds, microphone/session rules, phone
speech choices or aid output paths changed. Build/install pending.

Read-only watch vibrator diagnostics show the old 60 ms ready and 140 ms
completion effects finishing, with no suppression in the inspected HeySig
entries. Default amplitude is already 255 and intensity High. Thus the 0.11
change improves duration and pulse pattern, not hardware peak strength. The
live 0.10 session recorded five shake triggers and recognition results; this
does not establish that the owner felt every vibration.

## Tap watch screen follow-up

Owner requested tapping the screen rather than aiming for Speak. Watch 0.12
enables home background/status single taps through the same primary action.
The ScrollView gesture detector receives taps not consumed by child buttons;
Stop/More remain independent. GestureDetector rejects scrolling as a single
tap. The full viewport accepts background taps. Dialogs, watch face, sleeping
display wake delivery and PIN locking remain under Android control. Existing
consent, connection and busy guards apply. Includes the uninstalled 0.11 double
ready cue. Build/install pending.

The stronger-cue build passed shared checks (27 tests, zero failures/errors)
and debug APK assembly. After the tap request, the combined 0.12 debug APK
was rebuilt successfully in 4m19s, verified as package
com.heysig.batteryprototype/versionCode12 with the existing shared debug
signer, and installed over 0.10 successfully. No user data was cleared.
0.11 was not installed. Release APK/lint/AAB checks were not rerun for these
last gesture/cue edits; prior 0.10 release validation does not certify 0.12.
Physical tap/scroll behavior and perception of the longer ready pattern remain
owner acceptance items. No aid command was sent for these build/install checks.

Restored 0.12 voice session through More → Shake to speak without capturing
speech. Service active/IDLE with motion samples arriving and recognizer idle.
More remained usable after the background-tap change. A read-only Read status
reached REPLYING/TTS playing with both speech choices true; phone HandsFreeService
remained active. The initial menu attempt lost its control search after display
sleep; a bounded display-wake helper and fresh XML completed it. The helper
changed no lock/display preferences. Background single-tap recognition has not
been physically accepted by the owner yet.

## GitHub checkpoint and owner choice

After discussing alternatives, owner chose to keep shaking. No side-button
activation or automatic microphone-on-resume change was implemented. A remote
button-route test was declined and did not run. Watch 0.12 remains installed
with its longer double ready pulse, single reply/error pulse, optional phone-
controlled speech and visible-home tap shortcut. Final read-only status TTS
completed and the service returned to IDLE with both speech choices true.
Owner-reported working flow does not establish full locked-session/endurance
acceptance or perception of every cue. Local VS Code settings are excluded
from this source checkpoint; no APKs, signing material or device captures are
committed.
