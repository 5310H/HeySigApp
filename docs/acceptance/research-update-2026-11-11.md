# Research follow-up and device update — November 11, 2026

The owner requested review of two videos, current manufacturer offerings and
updates to the phone/watch. See the [research review and access limits](../research/hearing-aid-concepts-2026-11-11.md).
No application source, live decision rules, fitting or saved assignments changed.
Research notes are separate from the installed app revision.

## Installed candidate

- Pushed source: `e6b3f5b9c578940fe99590fa09b380b2d20e1713`.
- Successful [cloud checks run 43092434617](https://github.com/5310H/HeySigApp/actions/runs/43092434617).
- Existing downloaded artifacts were checked against all four locally re-signed
  APK SHA-256 hashes. Both app signatures matched the established development
  certificate `16a30331844bf27b7b809fc894040d9e9a08bb52fcd307fda1af21973d9f8fdb`.
- Both application APKs contain revision `e6b3f5b` and date `Nov 11, 2026`.
- Pixel 9a and Pixel Watch 3: app and test packages installed successfully using
  `adb install -r`; neither existing app was uninstalled or cleared.
- All 13 phone and one watch non-acceptance preference files matched byte-for-byte
  immediately before/after installation. See the [hash-only record](research-update-2026-11-11/upgrade-settings-check.json).

## Checks and limits

The phone Api36Smoke runner passed on Android SDK 37: navigation, native loading,
large-font/round-context layouts, optional controls, isolated storage/privacy,
routine/manual priorities and executor behavior. It uses fake transports and
isolated settings; it does not capture microphone input or play aid commands.

Watch fixture-only instrumentation passed all six native decisions (CLEAR,
MODERATE, HEAVY, CLEAR, UNKNOWN, HEAVY) and native measurements within the pinned
fixture tolerances. The actual 192dp round Automatic layout passed at font scales
1 and 2 with buttons at least 48dp. Reported native CPU was 36.073 seconds excluding
YAMNet; this is not capture latency or an endurance measurement. The runner
returned `INSTRUMENTATION_CODE: -1` and `result=PASS: watch Automatic native`.
No live microphone or paired aid command test was run.

The correct WatchAutomaticAcceptance runner is present in the installed test
manifest. This update is not physical aid reception, detector field acceptance,
battery endurance, regulatory clearance or Play approval.
