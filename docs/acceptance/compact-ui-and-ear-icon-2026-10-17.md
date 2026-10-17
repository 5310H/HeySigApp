# Compact phone/watch UI and ear launcher — October 17, 2026

The phone has three tabs, a native program chooser and four Home shortcuts.
Level controls and battery requests have focused pages. Noise starts with
Observe/Sample/Stop; setup, calibration and details are separate pages. Status
has an Info action. Saved controls are under Settings. The watch uses the actual
shared compact status/Speak (or Connect)/Stop/More layout, with secondary actions
and independent speech switches under More.

The owner supplied `ear_logo.svg`. The two teal ear paths are preserved; the T,
bees and small subtitle are removed from the launcher adaptation. Both modules
use adaptive icons with a monochrome layer and a dark background. The supplied
file is unchanged. See the [clean SVG](../../assets/branding/ear-logo.svg) and
[round preview](../../assets/branding/ear-icon-preview.png).

## Verification scope

Tests use the existing Chromebook ARC Android 13/API 33 development installation,
412dp window, at normal and 2× text. Installation uses `adb install -r`.
Preference digests for the real fitting and noise configuration are compared
with a fresh October 17 baseline; no values are printed. UI checks do not
request microphone access, capture audio or send aid commands. Slider movement
changes only a draft. The original Android font scale is restored in `finally`.
Round-watch checks render the actual shared home in isolated 192dp round
contexts at font scales 1 and 2; these are not physical Wear OS acceptance.
Previews with real program names remain in ignored `private/` storage.

The initial compact checks passed; visual review then shortened the program
caption and capped shortcut text sizing to prevent a large-font Sharpness wrap.
The first evidence is preserved alongside final runs.

Final normal and 2× text runs passed. The real fitting/noise preference digests
match the fresh baseline, and font scale 1.0 was restored. The actual shared
watch home fits the isolated 192dp round viewport at both font scales, with
48dp minimum targets and no ellipses. Visual review confirmed the corrected
Home shortcut labels.

- [Final normal-text report](evidence/compact-ui-chromebook-final-2026-10-17.txt)
- [Final large-text report](evidence/compact-ui-chromebook-large-font-final-2026-10-17.txt)
- [Initial normal-text report](evidence/compact-ui-chromebook-2026-10-17.txt)
- [Initial large-text report](evidence/compact-ui-chromebook-large-font-2026-10-17.txt)

## Build results

The final pinned Gradle build passed: 231 actionable tasks, 98 executed and 133
up-to-date, in 12m 15s. Shared tests, phone/watch debug and signed release APKs,
phone instrumentation, full release lint and both signed bundles completed.
The shared JUnit wrapper reports five tests, zero failures/errors/skips. No
Java/Kotlin compiler deprecation warnings were reported. Lint has zero errors:
three phone warnings and one watch warning, explained below. Previously the
October 16 UI build reported 21 phone and 9 watch warnings.

Development APK aliases were refreshed only after matching existing signer
digests. The compatible final phone update is installed on the Chromebook.

- [Build and lint result](evidence/compact-ui-build-2026-10-17.txt)
- [Signed bundle validation](evidence/compact-ui-bundles-2026-10-17.txt)

## Build cleanup

Deprecated system-window APIs are replaced with AndroidX WindowCompat/ViewCompat
insets. Numeric fields retain exact canonical decimal strings. Durable journal
writes still use synchronous commit before acknowledging or playing commands.
Watch launcher affinity and native launcher resources are explicit. Resources
used only by round-layout fixtures are confined to instrumentation.

AndroidX Core is pinned at 1.17.0 for compile SDK 36; newer Core releases require
a separate compile-tooling review. Predictive Back opt-in is intentionally
present for Android 13+ and ignored on older supported devices. The ARM build
host still requires its local experimental AAPT2 override. These compatibility
notices are not hidden by a lint baseline or broad warning suppression.
Adaptive launcher XML retains its API 26 resource qualifier because the ARM resource linker lost
the icon when its qualifier was removed; lint may report that qualifier as
redundant at the supported minimum SDK.

Targets, development IDs/signers, production identity and the Automatic release
lock are unchanged. AndroidX adds only its app-private signature-level receiver
permission; the bundle validator verifies its exact name and protection level.
The validator accepts the signature enum in symbolic or bundletool numeric
form (exactly 2); its first run exposed that representation mismatch, which
was corrected before rerunning validation. No runtime microphone or background
feature is added.

Physical watch/companion, TalkBack, exact Android 16 and aid reception remain
pending. Signed bundles are validation candidates, not Play approval or an
upload. See the [remaining checklist](../remaining-checklist.md).
