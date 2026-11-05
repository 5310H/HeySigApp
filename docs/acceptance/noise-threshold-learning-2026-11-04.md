# Automatic threshold learning — November 4, 2026

The owner requested suggestions learned during Automatic operation, followed by
approval before any saved threshold changes. Shared Kotlin now owns the bounded
scalar learner, proposal calculation and settings/source/fitting/revision/time
binding. The Android adapter reuses existing valid fresh captures only during
real Automatic with runtime priorities clear and no pending playback. Preview,
guided calibration, rejected/stale captures and post-playback settling do not
feed learning. No new permission, service, capture duration or network is added.

Learning retains at most 240 levels and monotonic capture times in memory,
trimming data older than one hour on ingestion. It needs five minutes of overall
measurement span and ten observations in both adjacent levels before considering
a boundary. Middle-range separation must exceed twice the hysteresis margin.
Proposals use the midpoint of the lower level's upper quartile and the upper
level's lower quartile, cap each adjustment at 2 dBFS, omit changes below 0.5 dBFS,
and validate the complete threshold spacing. Uncovered boundaries remain saved.
This describes the algorithm; it is not measured performance qualification.

The phone Noise page shows collection/readiness and Review/Discard controls.
Opening a ready review stops Automatic and displays all old/new thresholds plus
counts. Keep current changes nothing. Approve & save checks exact current
settings/fitting/source, learner identity/revision, foreground/audio availability
and a one-minute review lifetime. It uses the existing atomic noise-preference
save path. Only thresholds change, and altered thresholds invalidate prior
measured calibration proof. Automatic stays Off until explicitly restarted.
A failed preference commit retains the draft and existing saved settings.

Temporary learning survives Stop within the current process so it can be
reviewed; fitting changes and successful saves clear it. A changed source or
configuration starts new learning. Discard clears learning without stopping
normal Automatic. No learning history is exported or persisted. Approved noise
thresholds are stored in the existing settings preference, not as calibration
proof. Updated Privacy and data, microphone disclosure, and the user explanation
describe this data flow.

Pure checks cover limited/unchanged/overlapping evidence, partial coverage,
outliers, bounded storage, invalid/duplicate timestamps, expiry, source/fitting/
settings/revision changes, forged adjustment/evidence rejection, and preservation
of assignments/timing. Android smoke assertions cover visible Review/Discard
buttons without microphone use or aid commands. Build/device results follow.

## Validation status

Final standalone `bash tools/check-noise.sh`: PASS, including bounded fractional
rounding, evidence/approval guards and existing noise/worker regressions.
Gradle `:shared:test`: 33 tests, zero failures/errors/skips. Both debug APK builds
and lint passed; the watch was rebuilt once more for the final Build ID menu
label, with assembleDebug and lintDebug passing. Lint reports contain 26 phone
warnings and 18 watch warnings, with no errors.

Both final APKs passed pinned native-library inventory and 16 KB ELF/ZIP alignment
checks. The phone APK passed required compiled-class checks. APK inspection
confirmed the phone's learned-threshold review and calibration-proof disclosure,
and the watch's Build ID menu label. Android instrumentation assertions were
updated but not run; physical behavior, large-font/round layouts and real-user
threshold suitability still require device acceptance. No microphone test or
hearing-aid command was performed.

Pixel 9a installation, installed-byte verification and launch passed. All 26
preference files were byte-identical across replacement, before launch. See the
[phone device report](data/noise-learning-phone-update-2026-11-04.json).

The Pixel Watch 3 initially left the network during the build. After the owner
provided the renewed connection port, installation, installed-byte verification
and launch passed. Its existing preference file was byte-identical across
replacement, before launch. See the
[watch device report](data/noise-learning-watch-update-2026-11-04.json).
Both devices are now updated. Neither installation used microphone tests or
hearing-aid commands.

Both app labels use only Build plus the Git revision. This update uses base
revision `fe9c795` with local changes. At the owner's instruction, future updates
must validate and push intended changes to GitHub before final build/installation,
so Build identifies the pushed source. These installed APKs precede the
subsequently requested GitHub source update; a source push does not change an
installed APK's displayed base revision.

Toolchain: pinned Gradle wrapper with the local ARM override
`-Pandroid.aapt2FromMavenOverride=/tmp/heysig-arm-tools/build-tools/aapt2`.
This remains a development update, not physical acceptance or Play approval.
