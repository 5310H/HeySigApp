# Native UI refresh — October 16, 2026

The [UI refresh](../ui-refresh.md) uses four phone tabs, grouped native cards,
clear program tiles, draft-only level sliders with explicit Send, persistent
Speak/Stop actions and a dark rounded watch style. Existing fitting/storage,
validated command transport and privacy behavior are preserved. Automatic noise
switching remains disabled.

## Device and layout evidence

The compatible development update was installed with `adb install -r` on the
explicitly selected Chromebook Android 13/API 33 runtime. No uninstall, fitting
reset, noise capture or aid command was performed.

| Check | Observed result |
|---|---|
| Normal text, 412dp app width | PASS: Home/Noise/Settings/Voice/Privacy/Watch navigation, system Back through Settings to Home, current-runtime insets, at least 48dp button targets |
| 2× Android text, same window | PASS: same checks; adaptive tabs remain usable |
| Round-style contexts, 192dp | PASS at font scales 1 and 2: native shared watch styles, at least 48dp action targets and no text ellipses |
| History cap/expiry/corruption/deletion | PASS only in isolated test preferences; unrelated fitting sentinel retained |
| Real fitting and noise preferences | Before/after SHA-256 comparisons match exactly; raw preference files were not printed or committed |
| Temporary font setting | Original Android font scale 1.0 restored in a finally block and verified |

Raw phone reports:

- [Final normal-text run](evidence/ui-refresh-chromebook-final-2026-10-16.txt)
- [Final 2× text run](evidence/ui-refresh-chromebook-large-font-final-2026-10-16.txt)
- [Final short watch-label run](evidence/ui-refresh-chromebook-watch-label-final-2026-10-16.txt)

The initial navigation attempt failed with `windowFocusBeforeBack=false` and
`Back did not return to Settings`. The test now brings the existing app window
forward, targets input to its reported display and taps only a noninteractive
heading to focus it. Focused runs reported display 0 and window focus true.
No application Back behavior was bypassed to obtain a pass. The initial
[failure report](evidence/ui-refresh-chromebook-2026-10-16.txt) is retained.

Native view renders were captured only with the explicit instrumentation
`screenshots` option. These are app view renders rather than system-compositor
screenshots; the unfocused ARC compositor capture was black. Previews with real
program names remain under ignored `private/ui-preview/`, outside Git. Review
caught an awkward large-font watch brand wrap and overly wordy selected tiles;
brand scaling and concise labels were corrected. The watch primary label is
Speak, with the full Speak command accessibility description.

## Build and artifact validation

The shared checks, phone/Wear debug and release APK builds, instrumentation APK,
full release lint and signed bundles were built with pinned Gradle tooling.
The final full build reported BUILD SUCCESSFUL (231 actionable tasks: 51
executed, 180 up-to-date). The four shared suites retain zero failures/errors.
Release lint has zero errors, with 21 phone and 9 Wear warnings;
resource/localization/icon warnings remain and are not suppressed.

The [bundle validation report](evidence/ui-refresh-bundles-2026-10-16.txt)
records the final artifact hashes, package/target SDK/signature checks and
absence of development transport, private configuration and native libraries.
Existing development IDs and signing configuration are preserved. The final
watch-label debug/instrumentation rebuild passed (88 tasks: 8 executed, 80
up-to-date), followed by another passing device run including both round-style
font scales. Final native previews were visually reviewed. Development APK
aliases were refreshed after verifying unchanged signing-certificate digests.
The manifest preflight and whitespace checks also passed.

## Limits

Round checks use isolated Android native contexts and the actual shared style
code; they are not physical Wear OS device or full companion-flow acceptance.
TalkBack, keyboard/device variations, exact Android 16 and real watch quality
acceptance remain part of the production matrix. UI checks do not establish
aid reception. Policy publication, medical/health review and Play Console gates
remain as documented in the [readiness audit](../play-store-readiness.md).
