# Foreground companion and privacy controls — October 16, 2026

Status: implementation/build validation passed; physical companion acceptance
and verified public policy publication remain pending. This is not Play approval.

## Implemented

- Release phone/Wear connection through pinned Google Wear Data Layer 20.0.1,
  with matching existing release package/signer, explicit selected-watch
  disclosure/enable/forget flow and watch phone selection.
- Activity-scoped listeners and capability discovery; no background receiver
  service, developer relay, raw socket or durable command queue in production.
- Fresh five-second one-use command grants, sender binding, bounded payloads,
  request/result correlation and no automatic replay. Read status and result
  lookup do not transmit hearing-aid tones. Stop preserves the original
  request's lookup identifier.
- All aid controls still enter the existing shared parser, fitting validation,
  manual executor and guarded phone speaker transport. The watch emits no aid
  tones. Phone foreground/unlocked guards remain in place.
- In-app privacy notices on both devices, recognition-provider disclosure,
  separate optional speech switches (off by default in new release installs),
  TTS/network-voice disclosure and visible connection/speech state.
- At most 64 production reply records, expiring after 24 hours with removal on
  next open/access. No raw command-body or recognition-alternative journal.
- Phone history/display clear retains fitting and restoration records. Export
  uses the document picker after disclosure. Confirmed full deletion on either
  device invokes Android's own clear-app-data operation; no aid command is sent.
- Backup disabled and all app-data domains excluded from Android extraction.
- Public policy URL hook and owner-public configuration file. Fields remain
  empty because publisher/contact/hosting facts have not been provided.
- [Policy draft](../privacy-policy-draft.md), [data inventory](../data-safety-review.md)
  and [connection/setup/acceptance matrix](../production-watch-connection.md).

## Validation

Final command:

```sh
GRADLE_USER_HOME=/tmp/heysig-gradle-cache ./gradlew --no-daemon \
  :shared:test :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest \
  :wearApp:assembleDebug :androidApp:assembleRelease :wearApp:assembleRelease \
  releaseCandidates
```

Result: BUILD SUCCESSFUL; 231 actionable tasks (71 executed, 160 up-to-date).
Four shared JUnit suites passed with zero failures/errors, including 410 voice
phrase cases, manual/voice settings, noise measurement, six-level Observe and
new companion sender/token/expiry/duplicate/reconnect/retention checks.

Both release lint tasks passed: phone 0 errors/16 warnings; Wear 0 errors/7
warnings. Production quality/localization/icon warnings remain; no baseline or
project-wide suppression was added.

Compared the previous development APK aliases against the new debug APKs with
apksigner: both signatures verified and certificate SHA-256 identities matched.
The preserved development IDs and signing configuration were not replaced.

[Signed bundle validation and hashes](evidence/companion-privacy-bundles-2026-10-16.txt)
record Google bundletool, upload-certificate verification, decoded manifests,
backup flags and artifact contents. Validation checks require the production
capability in both bundles and reject development relay/research/test code,
background Data Layer listener filters, private configuration and native code.
The [manifest-bearing dependency list](evidence/companion-manifest-dependencies-2026-10-16.txt)
is an inventory input, not a completed license/SDK behavior review.

An initial Gradle script name collision and a watch TextView layout-property
collision were corrected before the successful final run. No source check was
disabled to get the build through.

## Runtime acceptance not performed

ADB listed only an offline emulator; no usable paired phone/watch was available.
The extended instrumentation runner compiled, including Privacy/Watch navigation
and history cap/expiry/corruption/deletion checks in isolated preferences. It
was not executed in this session. Earlier API 36 migration device evidence is
preserved and is not claimed as acceptance of these new features.

No install, microphone capture, actual full-data-clear operation or hearing-aid
command was performed. Existing fitting, saved restoration settings and physical
acceptance records were not changed.

Remaining required evidence: normal no-developer-tools pairing/status on real
Android/Wear devices; disconnect, stale-result and confirmation recovery without
replay; provider-specific recognition/TTS/network tests; export/deletion on a
disposable release installation; round-watch/large-font/Back acceptance; and
explicitly approved physical aid-command reception tests. Automatic noise
switching remains disabled.

Public policy publication still needs verified publisher identity, privacy
contact and a public HTTPS host, plus final third-party/provider review. No
Data safety/health declaration, Play upload or policy publication was performed.
