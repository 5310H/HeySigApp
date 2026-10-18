# HeySig Wear app

Release candidates now use the [normal Android/Wear pairing flow](../docs/production-watch-connection.md), with selected-node Data Layer messages, typed/voice commands, result lookup and privacy/data controls. Google Play services may use encrypted Google-server routing. The ordinary connection requires the phone open and unlocked. An explicitly started [hands-free phone session](../docs/hands-free-quick-start.md) additionally supports locked-phone commands and watch On/Pause/Off/End controls. The watch never emits aid tones. Hardware acceptance is pending.

The remainder of this guide describes the separate, preserved development relay.

## HeySig watch development companion

Native Wear OS prototype for the Pixel Watch 3. It offers Speak command,
Read status, Check last result, and Stop, with spoken replies. Commands use the
existing Chromebook app's parser and acoustic playback path.

The development companion has independent **Voice prompts** and **Spoken responses** switches
in More → Speech options. They persist on the watch. Say “voice prompts off/on” or “voice
responses off/on” to change them locally, even without the relay. The phone has
its own switches. Text replies remain available when speech is off.

## Experimental Chromebook connection

The watch connects over Wi-Fi to a pinned TLS relay on Chromebook Linux port
8765. The relay forwards authenticated requests through an ADB loopback forward
on port 18765 to the foreground HeySig Android app. The phone is not a bridge.
Keep HeySig open and explicitly enable its Watch Wi-Fi checkbox. Leaving the
app disables that foreground bridge; re-enable it on return. Alternatively, an
explicit hands-free session owns the same authenticated development bridge
while the phone is locked, until End or expiry.

Version 0.3 uses the [pinned Gradle build and separated variants](../docs/gradle-release-builds.md).
The debug variant retains this relay companion; the release validation candidate
omits the relay and implements a [foreground Data Layer connection](../docs/production-watch-connection.md). Physical pairing acceptance and verified public privacy policy publication are still required before submission.
Build with `bash tools/build-watch-prototype.sh`. Output is
`wearApp/build/heysig-watch.apk`. Install only after pairing ADB with the watch;
the pairing port and the main Wireless debugging connection port are separate.

Initialize `tools/watch-relay/relay.py --host CHROMEBOOK_LAN_IP --initialize-only`.
Its default private directory is ignored by Git. Install both apps, then run
`tools/watch-relay/provision.py --watch WATCH_IP:CONNECTION_PORT` through Python.
Forward TCP port 8765 in ChromeOS Linux settings. Run the relay with the same
`--host` value. It starts in status-only mode, which also permits the experimental
[noise editor, sampling and Observe commands](../docs/noise-quick-start.md).
Automatic noise and aid controls remain blocked. Verify Read status from the watch
before restarting the relay with `--allow-controls` for listening tests.

Configuration contains a secret token and certificate fingerprint. Do not commit
or share it. The development certificate expires after 30 days; regeneration
requires provisioning both apps again. The watch uses a Wi-Fi network request
and pins the exact certificate; it does not depend on the phone's Data Layer.

## Request behavior and validation

Each command has a unique ID, a conversation ID, and a short-lived one-use
nonce. The relay records results in SQLite and does not automatically retry
uncertain commands. Check last result reads the recorded outcome. Stop cannot
undo acoustic tones already emitted. Status reports remembered app state;
hearing aids do not provide acoustic readback.

`python3 tools/watch-relay/check_relay.py` checks duplicate suppression,
conflicting IDs, expiry, one-use nonces, concurrent submission, status-only
restrictions, and restart behavior after an uncertain result.

The APK builds and relay unit checks have passed. Installed-device TLS status
requests and spoken status with an audible reply passed
[device acceptance on 2026-10-16](../docs/acceptance/watch-status-2026-10-16.md).
Hearing-aid operation through this watch route still requires device acceptance.
This is a development relay, not a
background production service. Wear tiles and fully voice-driven setup are not implemented.

More → Shake to speak adds a separate default-off gesture choice. Three deliberate
shakes use the existing Speak action with a short vibration, only while HeySig
is resumed. Physical gesture/haptic acceptance remains pending.

**More → Sleeping-watch shake** provides a separate default-off session.
Choose Start after the motion/provider/battery explanation and notification
permission. Three deliberate shakes with HeySig in the background vibrate and
offer **Tap to speak** for 30 seconds. Tap to use the existing speech path;
unlock first if required. No microphone starts from the gesture itself.
The watch must provide a wake-up accelerometer. Unsupported devices retain
foreground Shake to speak. The session has a visible notification Stop control
and watch-face ongoing-activity return affordance. It ends after eight hours,
Stop, closing from Recents or process death, and never restarts after reboot.
Real screen-off delivery, false triggers and battery acceptance remain pending.

## Native launcher and connection check

The native watch launcher entry is now labelled **HeySig**, using the ear icon.
The development application ID/signing identity stays unchanged. Replacing an
older Tasker-created HeySig entry requires identifying that separate installed
launcher/package on the actual watch; updating this app does not remove Tasker
or another app's shortcut automatically.

Open **More → Check Wi-Fi**, or say **check watch Wi-Fi**, for a passive local
connection snapshot lasting at most five seconds. It does not scan, switch Wi-Fi
on or save network names. **Check phone link** asks the configured companion for
status over the existing transport; Wi-Fi validation alone does not prove that
link works. **Wi-Fi settings** opens the watch's system settings. Physical watch
acceptance for this new check remains pending.

Sleep routine setup commands are described in [the sleep guide](../docs/sleep-quick-start.md).
The phone owns saved routine choices. This build saves and previews them with
scheduling Off; it does not provide automatic overnight aid changes.
