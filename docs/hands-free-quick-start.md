# Hands-free on phone and watch

Hands-free is optional and Off after installation, reboot or process death.
Start a new session on the phone while it is unlocked. It can then recognize
commands and play the same validated tones while the phone is locked.

1. Open **Settings → Voice & speech → Start hands-free session**.
2. Read the microphone, installed-provider, locked-command and watch explanation.
   Enable the session, then allow the requested microphone and notification
   permissions. A new permission is never requested merely by opening Home.
3. Wait for the listening indicator. Say **Hey Sig**, wait for the vibration (or
   **Ready** if voice prompts are on), then say **program TV**, **volume eight**,
   **tinnitus four**, or another existing supported command.
4. Every new command or confirmation needs another Hey Sig. Recognition lasts
   up to ten seconds per wake. Conflicting recognized targets send nothing.

Commands can be triggered by other people or background sound; Hey Sig is not
speaker authentication. Leave media volume, speaker route and Do Not Disturb
configured as for the working manual app. Tones still do not prove aid receipt.
Actual pocket/locked reception and wake accuracy require physical acceptance.

## On, Off, Pause and End

- **Hey Sig, stop listening** releases the microphone. It stays Off and cannot
  hear a request to turn itself back on.
- **Hey Sig, pause for 30 minutes** releases the microphone and schedules a
  return to this same live session. Android may delay the timer.
- Use **Resume listening** on the phone or **On** on your authorized watch to resume sooner.
- **End session** stops listening and watch control, removes the notification
  and cancels the timer. Start another session on the unlocked phone afterward.
- The phone's global Stop also turns listening Off and cancels command playback.
- Sessions are limited to eight hours; process death, reboot or removal from
  Recents does not restart them or replay a command.

Off and Pause stop recognition but let an already-started tone command finish.
Use global Stop or End session to cancel playback; emitted tones cannot be undone.

Off retains the visible foreground session solely so its watch/notification/tile
controls remain available. This is distinct from End. The notification exposes
Off, Pause and End. Add **HeySig listening** through Android's Quick Settings
Edit screen for a convenient toggle. Without a live session, the tile opens the
phone app for explicit setup; it does not silently start the microphone.

Voice prompts and spoken responses are independent phone choices followed by both devices.
They are not required for recognition. A listening indicator remains visible
when both are off. The installed recognizer may have its own sounds.

Tap the listening status above the phone's navigation tabs to open Voice &
speech. This shortcut remains visible when hands-free is Off. Start/Resume,
Pause, Off and End are at the top of that screen, followed by Shake to speak
and the two speech switches. Voice tools & personal phrases contains the
recognition test, microphone settings, phrase management and typed commands.
Listening updates preserve your place on the voice screen.

## Watch

Select and authorize the paired watch in the phone's **Watch connection** before
starting the hands-free session. Normal pairing needs the shared production
application ID/signing identity within each variant. Development builds now use the same direct connection; there is no computer relay.
If the selected watch changes, restart or enable the session from the phone to
refresh the hands-free connection. Forget immediately rejects the old sender.

On the watch, choose **Connect phone** and select the enabled phone session.
Then open **More → Hands-free** for **On / Pause 30 minutes / Off / Status /
End session**. The watch's existing Speak button still recognizes a command on
the watch and sends it to the phone, including when that phone is locked during
the session. This does not enable an always-listening watch microphone.

After End, expiry or disconnection, no request is automatically retried. Use
Check last result when uncertain and verify aid state before repeating. A watch
cannot start a completely ended phone microphone session remotely; Android's
permission/start rules require the unlocked phone to establish that session.
Real paired-watch and locked-phone acceptance is still required.

## Microphone priority and privacy

Noise capture turns hands-free listening Off. Enabling hands-free stops a
foreground noise session. Wake capture releases its recorder before command
recognition, playback or spoken replies. Calls, mute, permission loss and capture
errors stop or block listening. Automatic noise switching stays source-locked.

Wake detection runs locally with bundled model assets; HeySig does not save
or upload that wake audio. Command recognition prefers Android's on-device
recognizer; otherwise the installed provider may transmit command audio.
Optional TTS can also use a network provider. No new audio/transcript history
is added. See Privacy and data for notices, export and deletion controls.

## Shake voice on the watch

Once per session, start the phone hands-free session with the selected watch
authorized. You can then turn phone listening Off while keeping that session
active for the watch. On the unlocked watch, tap Speak to start its voice session;
accept the disclosure and microphone/notification permissions once if requested.
Both sessions last up to eight hours and end on Stop/End, process death or reboot.
After that setup, shake for each command, or tap the status/background anywhere
on the visible HeySig watch home screen to start speech. Speak remains a large
explicit target; Stop and More keep their normal actions. Swipes/scrolls do not
start speech. When the display is asleep, the first tap may only wake it. This
shortcut cannot receive taps on the watch face or bypass the PIN lock.

Shake your wrist three distinct times within about two seconds. Wait for the
double vibration before speaking. A single vibration marks the result or an
error, not proof that the hearing aids received a setting. With phone Voice
prompts On, “Say a command” comes before the ready double vibration. With Spoken
responses On, the result/error is also spoken. Vibrations remain when both
speech switches are Off. Android recognition may have its own sounds.

The watch screen may sleep or remain PIN-locked during an existing session.
The microphone is idle between turns and captures for at most ten seconds per
command. The watch uses three 1.8 g peaks, reset below 1.3 g, separated by at
least 180 ms within 1.8 seconds, with a ten-second cooldown. No raw motion
samples are retained. Physical gesture and sleep/lock acceptance remains partial.

Phone shake is separate: it requires its existing session with listening On
and does not override Off or Pause. Its thresholds remain 2.7 g/1.8 g within
1.2 seconds. All commands use the shared validated executor.
