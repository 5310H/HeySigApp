# Google Play readiness — audited October 16, 2026

**Status: development only; not ready for store submission.** This is a
source/build audit, not a Play review or medical-device classification.
Recheck the official links before every submission. The requirements below
guide phone, Chromebook and Wear development from this point forward.

## Current blockers and completion evidence

| Area | Current finding | Required completion evidence |
|---|---|---|
| Target SDK | Phone manifest targets 35. Watch targets 35. | New phone submissions/updates currently require API 36; Wear requires 35. Upgrade phone compile/target SDK with Android 16 behavior and device tests. Recheck the dated requirement before upload. |
| Release build | Shell scripts produce locally signed development APKs. No Gradle release/AAB pipeline exists. Watch is explicitly debuggable. | Pinned Gradle/Android/Kotlin toolchain and wrapper, separated development/release variants, release AABs, non-debuggable production manifests, validated upload signing and Play App Signing. |
| Watch setup | Provisioning requires ADB, a Linux TLS relay, local certificates and a foreground Chromebook bridge. | A supported user installation/pairing/transport flow without developer tools. Truthful standalone/companion metadata and store description. Test connection failure and recovery without replay. |
| Privacy | Local preferences, remote speech recognition and development relay flows exist. No published privacy policy or in-app policy entry exists. | An accurate public non-PDF privacy policy, an in-app entry, retention/deletion details, publisher identity/contact and the release-specific Data safety form. |
| Health functionality | Controls hearing-aid programs/levels; no health-app declaration or regulatory assessment is recorded. | Complete Health apps declaration. Establish intended use, supported devices/countries and whether regulation applies; provide required evidence if regulated. Do not use a non-medical disclaimer as a substitute for assessment. |
| Microphone | Noise uses RECORD_AUDIO and bounded foreground capture. Speech is delegated to an installed recognition service. | Feature-specific explanation, affirmative user action, runtime permission, denial/revocation and privacy-switch tests. Verify actual recognition providers and network handling. |
| Quality/accessibility | Development UI; round-watch clipping and state-preservation risks need production work. | Core Android and Wear quality checklist, usable round layouts, large fonts, at least 48dp Wear touch targets, black watch background, swipe-back/state retention and appropriate launch assets. |
| Device safety/claims | Acoustic playback completion does not prove hearing-aid reception. Noise thresholds are illustrative; Automatic is disabled. | Bounded/validated controls, compatible-device and fitting acceptance, honest last-sent status, cancellation/recovery and endurance evidence. No unsupported medical/compatibility claims. |
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
| Noise microphone | PCM is processed in memory into scalar statistics, with no recording file or upload in PhoneNoiseSampler/NoiseAccumulator. Capture stops on leaving the Activity. | Verify final merged permissions, background behavior and all dependencies. Test denial/revocation and Android microphone privacy controls. |
| Voice recognition | ACTION_RECOGNIZE_SPEECH invokes an installed service that may use a network connection. | Identify supported provider behavior and accurately explain it; do not advertise offline-only speech without evidence. |
| Phone state | Private preferences contain fitting, last-sent state, snapshots, noise settings and voice preferences. | Final retention/delete/export controls, backup behavior and any additional SDK data flows. |
| Watch commands | Text and alternatives cross a pinned TLS connection to the development relay, then an authenticated ADB-forwarded loopback connection to the app. | Replace development provisioning/transport for release and audit production authentication, encryption and retention. |
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
   `python3 tools/check-play-readiness.py` to expose known source-level blockers;
   a clean result from that script would not establish full compliance.
2. Introduce normal Gradle phone/Wear release variants. Remove the development
   receiver, ADB provisioning, debug-only screens and relay dependencies from
   production. Do not replace them with an insecure shortcut.
3. Upgrade the phone to the required SDK and test Android behavior changes.
   Build/validate signed AABs and configure upload-key/Play App Signing handling.
4. Implement accurate in-app privacy, disclosures, optional speech and data
   management. Finish production watch navigation/pairing/accessibility.
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
