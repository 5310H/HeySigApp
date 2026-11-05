# Noise-level Automatic — November 4, 2026

The owner explicitly requested removal of the acceptance lock and trial mode,
and an update to AGENTS.md. Automatic On now starts ordinary foreground noise
switching from reviewed saved assignments/thresholds. Calibration is optional.
No physical acceptance, model accuracy or aid reception is inferred from this
change. The archived semantic classifier retains its separate qualification
checks and no longer owns Automatic On/Off.

Shared Kotlin owns parsing and noise decisions. The main phone/watch flow
provides On/Off rather than standalone Sample/environment-monitoring entry
points. Trial command/state/expiry are removed. Fitting, assignments, measured
calibration, snapshots and speech preferences are preserved without migration.

On requires valid saved settings, at least one target, an awake known baseline,
foreground/unlocked/audio availability and other schedules Off. An explicit On
resumes the manual override only when microphone consent/permission are ready;
a failed capture start restores the previous override. Therapy protection,
confirmation, hysteresis, stale/rejected sample handling, initial/success cooldown,
shared validated executor and post-playback settling remain. Off bypasses busy
checks. Late successful playback still updates cooldown after Off. There is no
trial timeout; leaving/locking/interruption ends capture and requires restart.

Tests cover ordinary uncalibrated saved-threshold decisions and remaining
runtime priorities. Android instrumentation assertions are updated for the
available On control and foreground guard, without capture or playback.
The later phone update below installs in place; no microphone test or hearing-aid command was performed.

Validation results follow. The initial cache-access blocker was resolved after
the owner requested the device update. No release artifact or Play submission
was prepared.

Terminology: user-facing Sleep labels/messages/defaults now say Therapy. New
therapy routine/time/program commands map to the existing shared routine
commands; legacy sleep aliases remain. Actual configured program names win
over routine aliases. Both Therapy and legacy Sleep program names are protected
from automatic noise switching. Saved preference/JSON keys, enum values, alarm
identities, explicit program names and route IDs are unchanged.

Validation completed: noise checks (measurement/parser/calibration/automatic
decisions/environment regressions/worker access), routine checks (including
Therapy aliases and exact configured-name precedence), and manual checks passed.
The final noise run passed, including multiple protected program slots.
`git diff --check` passed. Android instrumentation was updated but not run.

## Initial phone update

Pixel 9a updated in place and launched successfully. All 26 existing preference
files were byte-identical before and after replacement, before launch. Installed
APK bytes match the new local debug APK. See the
[device report](data/automatic-noise-phone-update-2026-11-04.json). Build ID is
`fe9c795`, the base Git revision with local changes. The owner asked to skip
the watch because it needs charging; it was not updated.

Gradle shared tests, phone assembleDebug and lintDebug passed (26 lint warnings,
no lint errors). Required phone compiled classes and pinned native inventory /
16 KB alignment checks passed. The ARM host used
`-Pandroid.aapt2FromMavenOverride=/tmp/heysig-arm-tools/build-tools/aapt2`;
the SDK and Maven aapt2 binaries were x86-64 and could not run on this host.
No UI interaction enabled Automatic, microphone capture or aid playback.

The later threshold-learning update also completed watch installation; see
[the final phone/watch evidence](noise-threshold-learning-2026-11-04.md).
