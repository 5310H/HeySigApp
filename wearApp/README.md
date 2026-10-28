# HeySig watch app

The watch connects directly to its paired Android phone using Wear OS Data Layer.
Both build types use the same connection code; there is no computer relay.
The watch recognizes commands and displays/speaks replies. Only the phone sends
hearing-aid tones through the existing validated executor.

Install matching phone and watch builds. Release uses `com.heysig.app` on both.
Development uses `com.heysig.batteryprototype` on both with the phone development
signer and the launcher name **HeySig Dev**. The retired `com.heysig.watchdev`
installation is not the paired development app. Existing phone fitting remains
in its original package; the app does not silently copy fitting between packages.

On the phone select Settings → Watch connection, find/select/enable your watch.
On the watch select More → Connect phone once. Keep the phone open/unlocked for
the foreground path, or explicitly start its reviewed hands-free session.
Voice prompts and spoken responses follow the independent phone switches.

Start a shake voice session from the unlocked watch. It lasts at most eight hours,
uses bounded recognition after three shakes and leaves the PIN intact. Stop ends
it. See [connection details](../docs/production-watch-connection.md).
Shake triggering passed owner testing on October 26; recognition and audible
prompts/results still require acceptance. Do not assume a vibration proves speech.
