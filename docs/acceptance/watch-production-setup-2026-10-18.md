# Direct phone/watch setup — October 18, 2026

Owner reported that Watch connection only displayed a development notice and
requested progress toward the production app.

## Finding

The Pixel had only `com.heysig.batteryprototype`; the watch had only
`com.heysig.watchdev`. These preserved development identities use the Chromebook
relay and cannot discover each other through production Data Layer. System
companion state on the Pixel reported the Pixel Watch 3 paired and connected by
Bluetooth. The missing HeySig candidate pair explains the development-only setup
screen; changing system watch pairing is unnecessary for this finding.

The direct-connection candidates both use `com.heysig.app` with the configured
upload signer and separate form-factor version codes. They install separately
from development apps and do not automatically migrate the existing fitting,
requested values, restoration record or preferences. No development data is to
be deleted to work around setup.

## Small development-path fixes

- Explain the direct-connection installation path in the development phone notice.
- Offer typed commands when watch recognition is unavailable, and show the shared
  recognition-provider disclosure before first speech use.
- Preserve the interrupted request identifier when Stop/cancel is sent, and block
  submission if the ordinary request identifier cannot be saved.

## Build and device validation

Shared checks and both debug APK builds passed. The phone APK required-class
packaging check passed. Watch debug lint passed with zero errors and nine warnings; release lint passed
with zero errors and two warnings. Both signed release APKs built successfully, including release vital lint;
phone/Wear package identities and upload certificate digests matched. Form-factor
version codes are 1,000,009 and 2,000,003. The final development-watch disclosure
wording was rebuilt and debug lint passed again.

Both candidates installed successfully alongside the original development apps.
On the Pixel 9a (Android 17), Settings → Watch connection showed the normal
controls. Find paired watches discovered Pixel Watch 3, selection displayed the
Data Layer disclosure, and Enable watch produced the enabled selected-watch
status. No microphone permissions were granted. The watch (Android 17) is
was PIN locked during inspection, and its debugging connection subsequently
dropped before final Connect phone/status/recovery checks. The owner was guided
to finish Connect → select Pixel → More → Read status directly on the watch. ADB installed the candidates; ordinary Play installation and the
full no-developer-tools acceptance matrix remain unverified.

No hearing-aid command, microphone capture or fitting change has been used for
setup diagnosis. This record does not establish Play approval or complete
watch voice, reception, accessibility or battery acceptance.

## Candidate fingerprints

- Phone release APK: `7a0ee42f31c7a688e3ca21f07eba0913fb2f73e97941996aa98cb09bdd3254ce`.
- Watch release APK: `5d4deef8854b75c9e7c546b19a7b8a787dd036794ada547775573ed79bcf8e0f`.
- Final watch development APK: `4128799d0c1e6df5d29e95348a118fa14d8aa2ea1d0ddfcf45e5c29cdb7f6715`.

Build source is the working tree based on 43658e1. Builds use the pinned Gradle
wrapper, existing local dependency cache and documented ARM aapt2 override.
A first build failed with the default x86 resource tool; the ARM override build
passed. Development apps were not replaced, cleared or uninstalled. Candidate
fitting is intentionally unset until reviewed setup/import; no controls are ready
for hearing-aid acceptance merely because the phone discovered the watch.

## Owner follow-up: connected, then status timeout

The owner subsequently reported the watch connected, then reported “no confirmed
result” after being asked to use More → Read status. This is not a passed
status/round-trip acceptance result. A fresh phone UI inspection still showed
Pixel Watch 3 enabled with the candidate open. Scoped candidate-process logs and
recent AndroidRuntime/Wear logs showed no app crash in the inspected entries.
The watch debugging endpoint was unavailable; direct watch diagnosis awaits its
current port. Do not infer successful status delivery, journal lookup or aid
readiness from the earlier connection report. No automatic command replay was
requested or performed.


## Direct status round trip passed

On the owner-provided replacement debugging port, fresh explicit Connect →
Pixel 9a returned “Phone connected. Commands use the phone speaker; keep HeySig
open and unlocked.” More → Read status returned “Program: unknown / Volume: not
set / Balance: not set / Tinnitus: not set.” The UI hierarchy confirmed the
complete reply despite the home status being ellipsized. Speech remained off.
These values are expected for the separate candidate with no fitting imported.

The watch repeatedly slept during earlier inspection. Its original system
screen_off_timeout was read as 10000 ms, temporarily extended to 120000 ms for
this controlled test, then restored and read back as 10000 ms. The earlier
reported timeout was not reproduced while awake; this does not prove its cause
or pass ordinary ten-second-timeout/lifecycle acceptance. No microphone,
recognition or aid playback was used. ADB was used for inspection/input, with
application requests using the matching release Data Layer implementation.

Next gates: reviewed fitting migration before aid controls; result lookup,
disconnect/reconnect and normal screen-timeout recovery; physical speech/aid,
round/large-font/accessibility, endurance and full production acceptance.
