# Google Play readiness — audited October 16, 2026

**Status: development only; not ready for store submission.** This is a
source/build audit, not a Play review or medical-device classification.
Recheck the official links before every submission. The requirements below
guide phone, Chromebook and Wear development from this point forward.

Build migration update: [pinned Gradle variants and upload signing](gradle-release-builds.md)
are implemented. Signed phone/Wear bundles passed artifact checks and release
lint; target-36 navigation/insets/Back checks passed on Android 13 and 17.
Development transport/research screens are separated from release sources.
See [migration evidence](acceptance/gradle-api36-2026-10-16.md). The [production Data Layer connection](production-watch-connection.md) and in-app
privacy/data controls are now implemented for validation. Hardware pairing
acceptance, verified policy publication and policy/medical work remain release blockers.

## Current blockers and completion evidence

| Area | Current finding | Required completion evidence |
|---|---|---|
| Target SDK | Gradle compiles both apps with 36; phone targets 36 and Wear targets 35. Insets and Back handling migrated. | Record runtime/device evidence; new phone submissions/updates currently require 36, Wear 35. Recheck the dated requirement before upload. |
| Release build | Pinned wrapper/Gradle/AGP/Kotlin, debug/release separation, preserved debug keys and a separate local upload key are implemented. | Validate signed AABs, release lint and artifact contents, reproduce on a standard build host, back up upload signing and complete Play App Signing enrollment. |
| Watch setup | Debug retains the separate ADB/Linux relay. Release now implements selected-node Wear OS Data Layer setup with foreground-only command grants; hardware acceptance is pending. | Accept the documented no-developer-tools setup on real paired devices. Test connection failure/recovery and confirmations without replay before submission. |
| Privacy | In-app privacy notices, speech disclosure, per-device deletion/export and bounded production result history are implemented. Public policy identity/contact/hosting are not yet supplied; see the policy draft and data inventory. | An accurate public non-PDF privacy policy, an in-app entry, retention/deletion details, publisher identity/contact and the release-specific Data safety form. |
| Health functionality | Controls hearing-aid programs/levels; no health-app declaration or regulatory assessment is recorded. | Complete Health apps declaration. Establish intended use, supported devices/countries and whether regulation applies; provide required evidence if regulated. Do not use a non-medical disclaimer as a substitute for assessment. |
| Microphone | Noise uses RECORD_AUDIO and bounded foreground capture. Speech is delegated to an installed recognition service. | Feature-specific explanation, affirmative user action, runtime permission, denial/revocation and privacy-switch tests. Verify actual recognition providers and network handling. |
| Quality/accessibility | Development UI; round-watch clipping and state-preservation risks need production work. | Core Android and Wear quality checklist, usable round layouts, large fonts, at least 48dp Wear touch targets, black watch background, swipe-back/state retention and appropriate launch assets. |
| Device safety/claims | Acoustic playback completion does not prove hearing-aid reception. Default noise thresholds are illustrative; optional measured calibration and command-free automatic preview are implemented. Automatic remains source-locked pending physical acceptance. | Bounded/validated controls, compatible-device and fitting acceptance, honest last-sent status, cancellation/recovery and endurance evidence. No unsupported medical/compatibility claims. |
| Testing/account | Local logic/APK checks and limited hardware acceptance exist. Play Console account/test status is unknown. | Internal/pre-launch/device tests; complete account verification, app content/rating/access declarations and applicable closed-test/production-access requirements. |
| Rights and dependencies | App includes protocol research and platform speech services. | Dependency/license inventory, provenance and distribution-rights review for shipped code/assets, and accurate manufacturer-affiliation wording. No proprietary research artifacts or secrets in releases. |

The current official target-SDK table sets API 36 for new mobile apps/updates
and API 35 for Wear as of August 31, 2026. The Wear quality page still contains
an older target-SDK paragraph; use the dedicated target-SDK policy for the
current submission floor. [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-TJ).

Google's health policy covers health-related functionality, external-hardware
disclosures, privacy and medical-device evidence. Our conclusion that this
controller needs health-policy review is an assessment based on its hearing-aid
functions; its legal classification has not been determined.
[Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).

## Verified current data flows — inputs to the privacy review

| Flow | Current implementation | Release review needed |
|---|---|---|
| Noise microphone | PCM is processed in memory into scalar statistics, with no recording file or upload in PhoneNoiseSampler/NoiseAccumulator. Optional calibration saves 18 scalar values and microphone/capture configuration locally. Capture stops on leaving the Activity. | Verify final merged permissions, background behavior and all dependencies. Test denial/revocation and Android microphone privacy controls. |
| Voice recognition | ACTION_RECOGNIZE_SPEECH invokes an installed service that may use a network connection. | Identify supported provider behavior and accurately explain it; do not advertise offline-only speech without evidence. |
| Phone state | Private preferences contain fitting, last-sent state, snapshots, noise settings and voice preferences. | Final retention/delete/export controls, backup behavior and any additional SDK data flows. |
| Watch commands | Text and alternatives cross a pinned TLS connection to the development relay, then an authenticated ADB-forwarded loopback connection to the app. | Release uses package/signer-restricted Google Data Layer messages, which may use encrypted Google-server routing. Verify SDK/provider behavior and physical setup; see production-watch-connection.md. |
| Request history | Relay SQLite stores command-body digests and final reply text; final replies can include settings or echoed speech. Phone/watch keep request IDs/results/preferences. | Bounded retention/deletion and truthful disclosure of any text/settings retained. Do not claim that speech-derived content can never be persisted. |

Google requires truthful disclosures of personal/sensitive data handling and
relevant SDK behavior. The Data safety form must describe the actual release;
local processing, off-device processing and exemptions must be assessed from
the form's definitions, not guessed from permission names.
[User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en),
[Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en-GB),
[disclosure and consent guidance](https://support.google.com/googleplay/android-developer/answer/11150561?hl=en).

## Engineering sequence and release gates

1. Keep functional prototype testing separate from submission preparation.
   Preserve current device signing/settings during development. Run
   bundle validation followed by `python3 tools/check-play-readiness.py` to inspect decoded release manifests;
   a clean result from that script would not establish full compliance.
2. Initial Gradle phone/Wear variant separation is complete. Keep the
   development receiver, ADB provisioning, research screen and relay out of
   production. Accept the implemented Data Layer companion setup on physical devices before submission.
3. Initial SDK migration, signed AAB validation and local upload signing are
   complete, with [device/build evidence](acceptance/gradle-api36-2026-10-16.md).
   Add exact Android 16 and full release-device acceptance, reproduce on a
   standard build host, back up the key and complete Play App Signing enrollment.
4. Validate the implemented in-app privacy, disclosures, optional speech and data
   management. Publish the verified policy and finish watch physical acceptance/accessibility.
5. Complete intended-use/device/country review, health declaration, medical
   assessment and verified privacy/Data safety/store-listing content.
6. Run release-variant tests: clean install/upgrade, no-ADB operation, denial and
   revoked permissions, speech-off operation, phone/watch disconnects,
   round/large-font screens, process death, Stop, battery/audio interruptions,
   physical aid compatibility and noise acceptance before enabling Automatic.
7. Run Play Console test tracks and pre-launch checks, review findings, then
   prepare the actual submission for owner review. No upload, publication or
   declaration has been performed by this audit.

Android's release guidance requires production build configuration, signing
and removal of development configuration. Play uses app bundles for new apps
and Play App Signing. [Release preparation](https://developer.android.com/studio/publish/preparing),
[App Bundles](https://developer.android.com/guide/app-bundle),
[Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en).

Wear submissions must meet applicable Wear quality requirements. A layout
appearing on one watch does not verify the full checklist.
[Wear quality](https://developer.android.com/docs/quality-guidelines/wear-app-quality),
[core quality](https://developer.android.com/docs/quality-guidelines/core-app-quality).

For personal developer accounts created after November 13, 2023, the current
production-access requirement includes at least 12 testers continuously opted
in for 14 days and an application for production access. Account applicability
is unknown here; do not assume this applies to every publisher.
[Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

## Evidence still requiring owner or external input

Publisher/account type and creation date; intended distribution countries;
publisher contact and privacy-policy hosting; supported aid models/fittings;
medical-device assessment and any applicable approvals; upload-key custody and
Play Console declaration/test results. These are release prerequisites, not
reasons to stop implementing or testing the authorized development work.

## October 17 hands-free background design change

The owner requested locked-phone commands and watch listening controls.
See [the separately reviewed design](architecture/hands-free-session.md).
New phone permissions are FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE,
POST_NOTIFICATIONS, WAKE_LOCK and VIBRATE; microphone access remains explicitly opt-in.
Watch adds VIBRATE for gesture acknowledgement.
No background boot restart/listener component is added. A live, non-sticky,
bounded microphone foreground session owns capture and selected-watch control.
The single validated executor is shared with foreground controls. Off releases
the microphone; End also ends foreground/watch authority. The wake engine now
introduces native dependencies: validate pinned bytes, 16 KB ELF and APK ZIP
alignment and actual supported devices, rather than retaining the previous
no-native-libraries assumption. New microphone foreground-service declarations
and updated data-flow disclosures are required before Play submission.

Current Android constraints consulted October 17:
https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
and https://developer.android.com/develop/background-work/services/fgs/service-types
The Play foreground-service declaration requirements are at
https://support.google.com/googleplay/android-developer/answer/13392821
These sources do not establish approval for this implementation.
