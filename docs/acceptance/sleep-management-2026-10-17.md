# Sleep management — October 17, 2026

Owner chose tap-to-switch notifications. Implemented Sleep now/Wake now using the
existing executor, durable mode/override/interruption recovery, manual priority
and sleep/noise exclusion. Added explicit inexact local reminder enable/disable,
late/duplicate guards and future-only rescheduling on boot/update/time changes.
No receiver playback, microphone session or exact-alarm permission is introduced.
The Teach a phrase Home shortcut is restored per the owner's clarification.

Shared checks cover command parsing, transition failure/recovery, sleep priority,
reminder eligibility/late limits, timezone and DST. Isolated Android acceptance
uses separate preferences and fake transport to cover saved targets, sleep/wake
completion, manual override, noise exclusion, failure/restart and invalidation.
It does not schedule alarms, edit the user's fitting or emit aid commands.

Validation: `tools/check-sleep.sh` and Gradle shared tests passed. Phone/Wear
debug APKs, the Android test APK and both release Kotlin compilations passed
in the pinned offline Gradle environment (144 tasks; build successful).
The updated Android smoke runner has not been executed: the device pre-update
read and test-environment smoke command were declined at tool approval. No
device installation or data-preservation comparison is claimed for this update.
Physical reminder delivery, Doze,
notification denial/revocation, timezone/reboot and hearing-aid acceptance remain
required. No Play approval is claimed.
