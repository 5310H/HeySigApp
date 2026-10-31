# Noise development device update — October 30, 2026

User requested phone and watch updates. The validated debug APKs from the
[noise implementation checks](automatic-program-switching-2026-10-30.md) are the
installation candidates; no release or Play submission is involved.

## Phone

Pixel 9a at the user-provided wireless-debugging address
`192.168.69.130:39139` accepted `adb install -r` successfully. Package remains
`com.heysig.batteryprototype`, version 0.15/code 15, target 36/minimum 29.
All 25 existing shared-preference files had identical SHA-256 fingerprints
immediately before and after installation; no new preference file appeared.
Only fingerprints were retained privately in `/tmp`; preference contents were
not printed or committed. No uninstall, data clear or permission change occurred.

HomeActivity subsequently opened with `am start -W` reporting `Status: ok`, and
the app process was present. No microphone, monitoring, program selection or aid
playback was used for this installation check. This does not establish audio,
classifier, physical-aid or Play acceptance.

## Watch

The earlier watch addresses returned `No route to host`. The user subsequently
provided `192.168.69.190:42717`, which connected to Pixel Watch 3. Its in-place
`adb install -r` completed successfully. Package remains
`com.heysig.batteryprototype`, version 0.12/code 12, target 35/minimum 30.
The existing `heysig-watch-v1.xml` preference fingerprint was identical before
and immediately after installation; no new preference file appeared. No
uninstall, data clear or permission change occurred.

The subsequent watch launch command did not return a confirmation during the
check. Installation/version and preference preservation are confirmed; watch
launch/runtime acceptance remains unconfirmed. No microphone or aid controls
were used to verify the watch installation.

## Installed APK fingerprints

- Phone SHA-256: `a3ecb38f03b6dcfa3a365fd4faa80da3d8d9c7220c350c58d3a7439936d854a7`.
- Watch SHA-256: `a889fd3a5b9998028db16c98aa2c5ce2e5ab523e285979fca440e50d0f2e717f`.
