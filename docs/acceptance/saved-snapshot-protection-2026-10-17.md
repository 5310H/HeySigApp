# Explicit saved-snapshot protection — October 17, 2026

First implementation milestone in the owner's requested recommendation order.
This deliberately changes earlier development/manual auto-save behavior: normal
commands only update last-sent state. Save settings is the explicit action that
replaces the independent restoration snapshot. Existing `saved` bytes are read
without an automatic rewrite or format migration. Fitting changes retain their
existing explicit invalidation behavior; rename/identity refinements remain a
later configuration milestone.

Shared `SavedSettings` validates/copies a snapshot and creates an ordered,
validated restore plan. Phone UI, foreground watch input and hands-free Save
call `ManualRuntime.saveSnapshot`; restore uses the same shared planner. Save
rejects Busy/unknown/invalid contexts and reports storage failures without
changing the prior in-memory snapshot. Saved-record decode/validation errors
preserve raw bytes and do not discard an otherwise valid fitting. Explicit valid
Save can replace an unusable snapshot. Read saved settings / What is saved are
available through the existing shared parser and phone/watch routes.

The isolated Android acceptance uses real runtime/preferences with a fake
transport. It checks unchanged snapshot bytes after program, level, battery,
automatic and successful/partial restore commands; restart/interruption; Busy,
invalid and failed explicit Save; successful explicit replacement; and damaged
snapshot isolation/recovery. The existing executor cancellation acceptance now
asserts retention of the original program-1 / volume-6 snapshot throughout.
No user preferences, microphone or actual acoustic transport are used.

Validation: shared checks passed (433 voice cases); shared Gradle tests,
phone/Wear debug APKs, Android test APK and both release Kotlin compilations
passed. The isolated Android smoke runner passed on Android 13 (target 36),
including savedSettings, handsFree, personalPhrases and sleepRoutine checks.
The calibration visibility assertion required a bounded layout-settling wait;
its visibility requirement remains unchanged. Android 16 and physical aid
acceptance remain pending. No Play approval is claimed.

Pixel debug update installed with `adb install -r`; read-only before/after
comparison confirms both fitting and saved snapshot bytes unchanged. No real
aid commands or microphone capture were used for installation verification.
