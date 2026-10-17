# Remove redundant program/status Info — October 17, 2026

The owner requested removal of program/status information clutter. Home keeps
the existing program chooser but drops its redundant “Last requested program”
caption. The global status row drops its Info button and shows full command
results directly, without single-line ellipsis. Initial idle status stays
hidden. Command execution, fitting, saved settings, microphone handling and
the Automatic release gate are unchanged.

This follow-up uses development APK and device checks. Signed release bundle
hashes in the earlier compact-UI record describe commit `09ac673`, not this
follow-up. Production release validation remains a separate gate.

The compatible development update was installed with `adb install -r` on the
Chromebook ARC Android 13/API 33 runtime (412dp app window). Normal and 2× text
checks passed, including navigation and isolated round-watch layouts. Visual
review confirms the caption and Info button are absent. Real fitting/noise
preference digests match the fresh baseline; font scale 1.0 was restored. No
audio capture or aid command was performed. The development APK alias was
refreshed after matching the existing signing identity.

- [Normal-text report](evidence/simple-status-chromebook-final-2026-10-17.txt)
- [Large-text report](evidence/simple-status-chromebook-large-font-final-2026-10-17.txt)

Physical watch, TalkBack and exact Android 16 acceptance remain pending.

Debug lint additionally exposed a pre-existing numeric view-ID error in the
research-only ProtoActivity. Its input fields and XOR checkbox now use named
Android ID resources, which remain stable across recreation. This does not
change command encoding or aid settings. The first combined verification
process stopped during lint; a standalone rerun exposed that error.

Final phone APK/instrumentation and debug lint passed (77 tasks: 21 executed,
56 up-to-date). Debug lint has zero errors and 35 warnings, including
existing research/instrumentation warnings. Shared checks and Wear development
APK checks also completed. See the [build record](evidence/simple-status-build-2026-10-17.txt).
