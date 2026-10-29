# Noise calibration voice milestone — October 28, 2026

## Scope

Complete the voice entry points for the existing native Kotlin calibration and
preview workflow. No real Automatic release-gate change. No new Sleep target
or program assignments. Saved configuration, fitting and restoration remain
unchanged until explicit Save. No microphone capture or aid command is part of
ordinary build/installation checks.

## Implementation

- Shared NoiseVoice commands: start, explicit confirm-start, capture, progress,
  review, apply, cancel, and no-playback Automatic preview.
- Touch and voice use the same calibration helpers. Start and apply require
  explicit review; start confirmation and apply review expire after one minute.
- Shared NoiseCalibrationReview binds apply to the samples, microphone, draft,
  fitting and monotonic review age. Apply does not save or enable switching.
- Calibration capture instructions specify the waiting interval and a short
  progress query. Phone speech follows saved choices and precedes the existing
  settling delay. Watch receives its normal local reply.
- Existing background HandsFree watch route delegates noise requests only to
  the visible/unlocked phone Activity. Pause removes that delegate. Missing
  foreground authority reports a block without starting capture.
- Noise alternatives are included in watch ambiguity checks. Calibration
  guidance honors the prompt switch; progress/results honor spoken responses.

## Verification

Baseline standalone noise measurement/Observe/calibration/automation checks
passed before changes. Added shared parser and expired/changed-review regression
cases, plus isolated Android confirmation/Stop/Cancel checks that assert no
capture consent, playback or saved-setting mutation.

An intermediate build failed after the progress command was added after shared
compilation; no APK from that inconsistent snapshot was installed. A fresh
consistent-source build is pending. Physical calibration, microphone behavior,
watch voice acceptance and aid reception are not established by these tests.

## Device access and remaining acceptance

Confirmed phone address 192.168.69.130:37207 has a reachable TCP port. ADB
logs report TLS SSLV3_ALERT_CERTIFICATE_UNKNOWN: wireless-debugging pairing
needs renewal. Read-only inspection and in-place update remain pending. Owner has not approved starting physical
samples or the proposed daytime program mappings. No sample values or mappings
are fabricated. Real Automatic remains locked at both start and dispatch.

## Phone 0.14 validation and installation

The final consistent-source build passed in 16m09s: 27 shared tests with no
failures/errors/skips, phone/Wear debug APKs, phone test APK and phone debug
lint (zero errors/17 warnings). Package/signer verified against the existing
phone development identity. Wireless pairing eventually succeeded. Phone 0.14
installed in place; actual fitting and noise preference file digests matched
exactly before/after installation and after isolated device checks.

Api36Smoke passed on the Pixel (reported Android SDK37, app target36). This
includes isolated no-playback/no-capture calibration confirm/apply rejection,
Stop invalidation, Cancel and storage preservation, plus existing UI/shared-
executor/snapshot/priority checks. It does not establish physical calibration
or new watch voice acceptance.

Actual saved noise assignments are [1,3,6,null,null,null], with default
boundaries [-60,-50,-40,-30,-20], margin2, confirmations3, cooldown60, capture5,
wait10, freshness30, and protected program2. No calibration proof is saved.
These choices were inspected without changing them.

## Owner-authorized uncalibrated trial

Owner requested “turn on automatic” and explicitly deferred calibration until
after seeing operation. Phone 0.15 adds an explicit development-only acceptance
trial rather than changing the production qualification constant or inventing
calibration proof. Normal Automatic still enforces the production gate.

The trial uses saved assignments/thresholds, is labelled UNCALIBRATED, expires
after 15 minutes, and is memory-only. Foreground, unlock, audio priority, awake
baseline, confirmation, cooldown and duplicate suppression remain required.
Mode expiry/Stop removes the authorization and cancels sampling. The final
executor boundary checks active debug-trial authorization too.

Existing manual override was On, with mode AWAKE and other schedules Off.
Explicit Start resumes noise control through the existing durable state
transition; startup failure restores the old state. New manual requests or
watch Stop terminate the noise session. Other schedules are never enabled.
Restoration snapshot digest was captured for before/after comparison.

Added shared checks for development-versus-production admission, expiry, clock
rollback, Stop/inactive authorization and all remaining readiness guards.
0.15 build/device trial results are pending. No calibration samples have been
labelled or collected. Production physical qualification remains incomplete.

## Final 0.15 checkpoint

Final consistent APK and AndroidTest builds succeeded; phone 0.15 was installed
with the existing development signing identity. The earlier full 0.15 shared
checks passed (27 tests), and lint reported zero errors and 17 warnings.
The final isolated on-device rerun failed because the calibration screen was
not shown; this rerun is not a pass. Subsequent direct UI inspection confirmed
the phone was PIN-locked (keyguard and AOD showing).

Owner explicitly authorized real changes: allow change, lets test it. The UI
trial attempt could not obtain a fresh visible control tree while locked,
so no successful trial start or automatic program change is claimed. Awaiting
owner unlock to perform Stop verification and the live foreground trial.
No calibration evidence was fabricated and saved mappings were not edited.

## Unlocked live trial

Owner unlocked the Pixel. Dismissed an unrelated Refresh confirmation without
refreshing. Started the real uncalibrated trial through its review dialog.
Observed built-in microphone sampling and initial cooldown at -43.6 dBFS.
Tapped Stop noise, then started a fresh trial successfully. Observed ongoing
sampling, cooldown, and Unused level decisions with zero confirmations for
level 4. Subsequent persisted last-requested program changed from 5 (TV)
to 6 (Very Noisy), providing evidence of an automatic program request during
the authorized trial. Physical hearing-aid receipt is not established by this
state alone. Restoration snapshot digest remained
47ae8540c36d118f500a9d34004ec87614189011e566447fbf10a4ead07037be.
Saved noise mappings and thresholds were not edited; levels 4–6 remain unused.
Trial requires the phone foreground and unlocked; it is not background-ready.
