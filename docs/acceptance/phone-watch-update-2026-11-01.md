# Phone and watch development update — November 1, 2026

The owner requested completion of the phone/watch update. Both existing
development apps were updated with `adb install -r`; neither was uninstalled.
Installed base APK bytes were read back and matched the local candidates.
Both launcher activities returned `Status: ok`.

All 25 phone preference files and the one watch preference file were identical
immediately before and after installation. Only in-memory fingerprints were
compared; settings contents were not retained in the report. No permission
grant, microphone capture, monitoring start or hearing-aid command was used.

The candidates include the primary-preserving Small Group monitor and the
watch sample-age/cancellation fixes. Both automatic qualification gates remain
disabled. Installation and launch do not establish microphone, classifier,
physical-aid, endurance or Play acceptance.

Exact installed hashes and launch evidence are in
[the scalar report](data/phone-watch-update-2026-11-01.json).

`tools/check-noise.sh` passed all checks, including grant/sample timing,
cancellation and PCM erasure. Gradle `:shared:test` passed (up to date).
Both development APKs passed pinned native-library and 16 KB alignment
validation; the phone passed required compiled-class validation.

The host initially exhausted disk space and Java performance counters crashed.
Generated native build caches were removed and Java performance counters
disabled for the retry. A subsequent rebuild reached the resource compiler,
which required the ARM override documented in `docs/gradle-release-builds.md`.
The final offline Gradle build with that override passed both
`:androidApp:assembleDebug` and `:wearApp:assembleDebug`; packaging was up to
date. No production artifact was installed or published.
