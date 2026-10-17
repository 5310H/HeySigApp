# HeySig Wear app

Release candidates now use the [normal Android/Wear pairing flow](../docs/production-watch-connection.md), with selected-node Data Layer messages, typed/voice commands, result lookup and privacy/data controls. Google Play services may use encrypted Google-server routing. The phone must stay open and unlocked; the watch never emits aid tones. Hardware acceptance is pending.

The remainder of this guide describes the separate, preserved development relay.

## HeySig watch development companion

Native Wear OS prototype for the Pixel Watch 3. It offers Speak command,
Read status, Check last result, and Stop, with spoken replies. Commands use the
existing Chromebook app's parser and acoustic playback path.

The development companion has independent **Voice prompts** and **Spoken responses** switches
below Stop. They persist on the watch. Say “voice prompts off/on” or “voice
responses off/on” to change them locally, even without the relay. The phone has
its own switches. Text replies remain available when speech is off.

## Experimental Chromebook connection

The watch connects over Wi-Fi to a pinned TLS relay on Chromebook Linux port
8765. The relay forwards authenticated requests through an ADB loopback forward
on port 18765 to the foreground HeySig Android app. The phone is not a bridge.
Keep HeySig open and explicitly enable its Watch Wi-Fi checkbox. Leaving the
app disables the bridge; re-enable it on return.

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
background production service. Tiles and voice-driven setup are not implemented.
