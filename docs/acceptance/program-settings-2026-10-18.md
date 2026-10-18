# Per-program settings — October 18, 2026

Volume, Balance (stored under the existing SHARPNESS key for compatibility),
and tinnitus targets are retained per program after completed transport playback.
Selecting a program replays its known targets through the existing executor.
Explicit levels in a multi-command plan take precedence; explicit snapshot restore
continues to send only its own known fields. No commands replay at startup.
The previous active program's known levels seed its record on upgrade. Changes
to fitting clear these records alongside requested state and saved snapshots.
Interrupted commands do not replace remembered targets with an uncompleted value.
Playback completion still does not establish reception by the aids.

Phone controls now say Balance and use a teal accent.
The isolated ProgramSettingsSmoke runner exercises storage/restart/program changes
and snapshot protection with a fake transport and separate preferences. It sends
no audio and never writes the user's fitting or settings.

Validation: shared Gradle tests and phone/Wear debug APK builds passed using the
existing ARM build cache. Android test APK compiled. ProgramSettingsSmoke passed
on the phone at 192.168.69.130:38471 with the final development APK installed
using an in-place update. No physical-aid playback was performed. Watch connection
at 192.168.69.190:34757 returned No route to host; no watch installation performed.
Full accessibility/large-font/round-watch and physical aid reception acceptance
remain unverified for this change. These development checks are not Play approval.

## Corrective packaging validation

The earlier overlapping builds produced an APK missing HomeActivity, causing
ClassNotFoundException on both Chromebook and phone. The previous startup
verification was insufficient. A clean single Gradle invocation rebuilt the phone
APK, test APK and shared tests successfully. check-development-apk.py now checks
required DEX class definitions and is included in build-android-prototype.sh.
The corrected APK passed this check, installed in place on both devices and
launched HomeActivity successfully (am start -W Status: ok). Both app processes
remained alive afterward. No hearing-aid commands or settings changes were used.
