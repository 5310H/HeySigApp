# Sleeping-watch vibration and tap — October 18, 2026

Owner selected vibration followed by Tap to speak. Implementation is in both
watch variants under More → Sleeping-watch shake. See the
[reviewed extension](../architecture/hands-free-session.md#october-18-sleeping-watch-extension).

Source: working tree based on 43658e1. Tester: Codex.
Device: Pixel Watch 3 at the owner-provided endpoint `192.168.69.190:37001`;
Android 17 observed this session. A standard type-1 wake-up accelerometer was
reported by the device without starting a motion session.

Initial menu candidate APK SHA-256:
`ba0ea6adb26f842f683fd9722f1394e24ff05fc0c06d64f692995ab0037cfa28`.

Final development watch APK SHA-256 (notification-revocation guard and corrected
Recents flags):
`8db9d787fddcb4b9b3e8aca697e7fafd4784e3a213efa1b813266e711cf8e5ae`.

Completed automated/build checks:

- 13 shared JUnit tests passed, including two new session/token tests for expiry,
  one-use taps, stale offers, Stop, restart and missing process-local authority.
- Manual controls/setup/phrase standalone checks passed.
- Phone and watch development APKs and the phone instrumentation APK built.
- Watch in-place development installation returned Success; all existing
  shared-preference file hashes matched before/after. Private hashes stay in
  `/tmp`, not this record.
- Owner confirmed the More → Sleeping-watch shake entry is visible on the
  initial installed menu candidate. The final correction retains that menu.
- Final correction APK and instrumentation APK rebuilt successfully; the final
  watch update returned Success and fresh before/after preference hashes matched.
- Phone required-class DEX packaging check passed. Phone release lint passed
  with zero errors and 16 warnings; both release Kotlin compilations passed.
- Watch merged release manifest has a non-exported specialUse motion service,
  no microphone permission and the expected notification/service permissions.

Final watch release lint recheck passed with zero errors and two warnings:
the intentionally pinned Core version and existing redundant v26 icon folder.
The two Recents flag warnings were corrected before the final APK rebuild.

The session remains off after installation. No motion session, recognition,
microphone capture, aid command or hearing-aid setting change was used to verify
installation. No release submission or account declaration was performed.

## Required physical acceptance

1. Open More → Sleeping-watch shake; review the explanation. Test notification
   denial without starting, then permission/channel recovery and explicit Start.
2. Verify the persistent notification, Stop and watch-face return indicator.
3. With the screen off, deliberately shake three times; verify vibration and
   Tap to speak. Motion alone must not launch recognition or send commands.
4. Tap within 30 seconds and verify the ordinary speech/provider/connection path;
   cancel recognition without changing aids. Verify stale/repeated taps do not
   start recognition. Verify lock/unlock behavior.
5. Verify Stop from the notification, More and main Stop; also Recents removal,
   process death and reboot. No session may restart itself.
6. Verify eight-hour expiry and late notification/event rejection, accidental
   movement, TalkBack/large-text/round layouts and measured battery impact.

These physical items remain open. specialUse foreground-service eligibility,
final disclosure/declaration and Wear presentation acceptance remain release
gates. Real Automatic noise switching remains source-locked.
