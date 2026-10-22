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

Voice prompts and spoken responses remain separate choices on each device.
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
application ID/signing identity; development builds retain their separate relay.
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

## Optional shake activation

Phone Voice settings and watch More → Shake to speak provide separate, default-off choices. Three acceleration peaks above 2.7 g within 1.2 seconds, separated by at least 180 ms and a return below 1.8 g, trigger one command; a ten-second cooldown limits retriggers. Sensor samples stay in memory. A 60 ms vibration acknowledges activation. This is a gesture heuristic, not a trained model; walking/exercise false triggers and accessibility require physical testing.

Phone shake requires an existing session with listening On; it never overrides Off or Pause, starts a session or changes an aid setting by itself. It uses the same bounded command recognition, generation checks and shared executor. The phone foreground listening wake lock supports sensor delivery while locked. Watch shake currently runs only while the HeySig activity is resumed and uses the existing Speak consent and connection checks. It is not a sleeping-watch wake gesture. No persistent watch sensor service or new background watch permission is added.
