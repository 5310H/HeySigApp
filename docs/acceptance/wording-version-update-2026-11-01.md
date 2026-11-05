# Command wording and build version — November 1, 2026

Successful program/level commands now report `Set to …` instead of `Played: …`.
The existing result callbacks supply both visible text and optional spoken
responses, including watch replies and hands-free results. Battery-only
requests report `Battery request sent: …`. Transport, execution, error handling
and remembered settings are unchanged; this wording does not confirm aid receipt.

Both apps use build version `2026.11.001` (`yyyy.mm.###`). Android's separate
integer version codes are phone 16 and watch 13. The phone displays the build
on Home and Settings; watch More → App version opens the build details.
Build type is also visible so development artifacts can be identified.

Offline Gradle `:shared:test`, `:androidApp:assembleDebug` and
`:wearApp:assembleDebug` passed using the documented ARM resource compiler and
`JAVA_TOOL_OPTIONS=-XX:-UsePerfData`. Both APKs passed native inventory and
16 KB alignment checks; the phone passed required compiled-class validation.

In-place installation, installed-byte matching, launch and preference-preservation
results are recorded in [the device report](data/wording-version-update-2026-11-01.json).
Both devices' installed package metadata also reported `versionName=2026.11.001`,
with phone code 16 and watch code 13. Final local APK hashes matched the installed
bytes in that report. The additional UI hierarchy check was interrupted and
does not establish visual/accessibility acceptance of the new build displays.
Verification uses no microphone capture or hearing-aid commands. Live speech,
physical aid reception and Play approval are not established. Automatic
qualification gates remain disabled.
