# Hands-free background session design — October 17, 2026

Scope approved in this session: optional Hey Sig wake phrase, Off, Pause 30
minutes, notification/Quick Settings controls, watch controls and actual
locked-phone commands. The owner explicitly requested locked-phone operation.
This design is reviewed separately from the earlier foreground-only noise and
speech design. Implementation review and acceptance evidence are recorded
separately; code/build success is not physical acceptance or Play approval.

## Session and authority

Only the visible phone activity starts a new microphone foreground service,
after a feature-specific explanation and affirmative permission requests.
Notifications are required by the UI so the owner retains visible Off/End
controls. The service is non-exported and START_NOT_STICKY, has no boot receiver
and does not restart listening after process death. A session is bounded to
eight hours and ends when removed from Recents. No background PendingIntent
can create a new session: only the activity's one-use process-local start
handoff can do so.

Off invalidates the session generation, destroys command recognition and
releases the wake microphone and wake lock. It retains the existing foreground
session/notification so an already-authorized selected watch or system tile can
turn listening back on. End session removes this authority and closes the watch
connection. It must then be started on the phone again. An already-authorized transmission keeps its separate bounded command wake lock
until completion; Off/Pause do not cancel it, while End and global Stop do.
Off is not a wake phrase
that can turn itself back on. Paused mode captures nothing; an inexact elapsed
realtime alarm may resume this same live session after about 30 minutes. Off,
End or a new pause invalidates an older alarm. Android may delay that timer.

## Microphone and commands

The bundled sherpa-onnx keyword model processes 16 kHz PCM in worker memory.
It detects the project-authored phonetic Hey Sig keyword; it is not voice or
speaker authentication. No audio file, transcript history or analytics is
added. The model is generic, not trained on the owner's voice. Capture ends and
the AudioRecord is released before command recognition starts. Recognition is
bounded to ten seconds, prefers Android's explicit on-device recognizer and
otherwise uses the installed provider, which may transmit command audio. The
disclosure states this fallback. There is no repeated continuous SpeechRecognizer
loop. Each subsequent command needs a fresh wake phrase, including confirmation.

Prompts and spoken responses remain independently optional and honor existing
settings/migration defaults. The notification and visible active-session status
remain usable when both are off. TTS, command transmission, prompted recognition
and noise sampling have priority over the wake microphone. Noise capture turns
hands-free listening Off; starting listening stops a foreground noise session.
Calls, mute, permission loss and capture errors fail closed with visible status.

## One executor

ManualRuntime owns the existing fitting, last-requested state, saved restoration
record, busy/cancelled flags, durable journal and validated PCM transport. Phone
buttons, speech, noise's locked Automatic path and background requests share
that same instance. No new player or queue is added. The activity retains
foreground guards. Locked playback is allowed only while the explicitly
started hands-free service is still alive; Off still permits deliberate watch
commands in that authorized session. End cancels in-flight playback. Route,
DND, call, media-volume and external-output checks remain in SpeakerPlayback.
A partially sent command invalidates remembered certainty; no request is
replayed after interruption. Playback completion never establishes aid receipt.
Automatic noise switching remains source-locked and foreground-only.

Background parser uses the same shared command/validation vocabulary, alternatives
must agree, and confirmations expire after 60 seconds with origin isolation.
Restore and ambiguous selections require explicit confirmation. Noise edits and
capture remain in the existing foreground UI; unsupported background requests
do not start capture or execute an aid command.

## Watch

Release Data Layer listening exists only within the live foreground service,
on a separate hands-free capability/path. Only the phone's explicitly selected
watch is accepted, with separate hands-free disclosure consent. Re-selection or
Forget invalidates the old sender immediately. Existing one-use five-second
leases and bounded 24-hour/64-result replay protection apply. Raw commands and
alternatives are not persisted by this path. No manifest WearableListenerService
or autonomous wake-up is introduced. The watch chooses an explicitly enabled
hands-free phone session before the ordinary foreground connection, never
queues/retries, and exposes On/Pause/Off/Status/End in More. Watch Speak continues
to use its installed recognition provider. It does not become an always-listening
watch microphone. Development uses only the existing authenticated ADB/Linux
relay and existing development IDs/signers; no relay enters release artifacts.

## Packaging and acceptance

The engine release AAR is checksum-pinned; model/native provenance and license
notices are in third_party/sherpa-onnx. All downloaded ABI libraries have 16 KB
ELF alignment. Artifact ZIP alignment and exact 16 KB-runtime behavior require
validation. Phone targets 36/Wear 35 remain unchanged. Permissions newly needed
by the phone are FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE,
POST_NOTIFICATIONS, WAKE_LOCK and VIBRATE; RECORD_AUDIO already existed.
Watch adds only VIBRATE for gesture acknowledgement. No runtime
permission is requested on normal app launch. No model download occurs at runtime.

Required evidence: pure stale-result/timer tests, silent native-model loading,
isolated fake-transport journal/concurrency/failure tests, normal/large/round UI,
permission denial/revocation, locked/offscreen capture and actual command
reception, false wakes/other speakers, notification/tile/Off/Pause/End, call and
noise arbitration, process death/reboot, paired real watch and battery/endurance.
No new physical aid setting is changed just to test installation or screens.
Google Play microphone-FGS declarations, privacy/Data safety and medical review
must be revised before a store submission.

## Optional shake activation

Phone Voice settings and watch More → Shake to speak provide separate, default-off choices. Three acceleration peaks above 2.7 g within 1.2 seconds, separated by at least 180 ms and a return below 1.8 g, trigger one command; a ten-second cooldown limits retriggers. Sensor samples stay in memory. A 60 ms vibration acknowledges activation. This is a gesture heuristic, not a trained model; walking/exercise false triggers and accessibility require physical testing.

Phone shake requires an existing session with listening On; it never overrides Off or Pause, starts a session or changes an aid setting by itself. It uses the same bounded command recognition, generation checks and shared executor. The phone foreground listening wake lock supports sensor delivery while locked. Watch shake currently runs only while the HeySig activity is resumed and uses the existing Speak consent and connection checks. It is not a sleeping-watch wake gesture. No persistent watch sensor service or new background watch permission is added.
