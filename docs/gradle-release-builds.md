# Gradle builds and signed validation candidates

The phone and watch now have separate debug and release variants. These steps
produce test candidates, not Play submission approval. Production watch pairing,
privacy/health declarations and the other [release gates](play-store-readiness.md)
remain required.

## Pinned toolchain

- JDK 17; Gradle 8.13 with distribution SHA-256 in the wrapper properties.
- Android Gradle plugin 8.13.2; Kotlin 2.2.10; Android build tools 35.0.0.
- Compile SDK 36 for both apps. Phone target 36, minimum 29; Wear target 35,
  minimum 30. Target SDK does not raise the minimum supported Android version.
- JUnit 4.13.2 wraps the existing shared manual/voice and noise regression checks.

AGP 8.13 supports API 36 and JDK 17/Gradle 8.13.
[Official compatibility table](https://developer.android.com/build/releases/agp-8-13-0-release-notes?hl=en).
Android 16 removes edge-to-edge opt-out and changes Back dispatch for apps
targeting 36. HomeActivity now handles system-bar/cutout/IME insets and uses
OnBackInvokedDispatcher on API 33+, with a fallback on older Android.
The manifest explicitly opts into the callback on Android 13; the retained
pre-33 framework override has a narrowly documented GestureBackNavigation lint
exception. Microphone capture rechecks permission at the capture entry point,
including after permission revocation.
[Android 16 target behavior](https://developer.android.com/about/versions/16/behavior-changes-16).

## Local setup

Install JDK 17 and Android SDK platforms 35/36 with build tools 35.0.0, then
set `sdk.dir` in ignored `local.properties`, or use `ANDROID_HOME`. Run:

```sh
python3 tools/setup-signing.py
./gradlew --no-daemon :shared:test :androidApp:assembleDebug :wearApp:assembleDebug
```

The signing helper preserves existing development keys from each module's
`build/debug.keystore` in ignored `private/signing/`. For a fresh checkout it
creates new development keys. Preserve the old keys before any clean task if
you need compatible updates to existing installations. Gradle clean no longer
removes the active signer.

| Variant | Phone application ID | Watch application ID |
|---|---|---|
| Debug | `com.heysig.batteryprototype` | `com.heysig.batteryprototype` |
| Release | `com.heysig.app` | `com.heysig.app` |

The phone development ID, signer and preferences remain unchanged. The watch development build now uses that same package and phone signer so Data Layer can connect. The old `com.heysig.watchdev` relay package is retired; no installed data is silently copied or erased.
Release IDs are separate; installing a release candidate does not migrate
development fitting/settings automatically. Final publisher/package identity
must be confirmed before registering the apps in Play Console.

Phone and non-standalone Wear release candidates share the package and signer,
as required for companion publishing. Their version-code ranges are distinct:
phone `1,000,013`, watch `2,000,009`; debug uses 13 and 9 respectively. The
separate `com.heysig.watch` namespace identifies the watch's Kotlin classes,
not its release application ID.
[Companion package/signing requirement](https://support.google.com/googleplay/android-developer/answer/18058043?hl=en-CA-3).

The earlier build scripts now call Gradle and copy debug APKs to the familiar
`androidApp/build/heysig-proto.apk` and `wearApp/build/heysig-watch.apk` paths.
The old HEYSIG Android-jar/D8 environment variables are no longer needed.

## Local ARM build tooling

The initial migration uses an ignored SDK at `/tmp/heysig-android-sdk` and cache
`GRADLE_USER_HOME=/tmp/heysig-gradle-cache`. Google's SDK platform/build-tool
archives were checked against their official repository index checksums.
The old Debian aapt2 cannot read current platform resources on this machine.

This ARM host uses the AOSP-based static aapt2 from
[android-sdk-tools release 35.0.2](https://github.com/lzhiyong/android-sdk-tools/releases/tag/35.0.2)
through a machine-local `android.aapt2FromMavenOverride` in the Gradle user-home
properties. This override is not committed or needed on a standard supported
host with Google's Maven aapt2. The downloaded ARM archive SHA-256 was recorded
as `db1cea2c4454d5f9c5a802646b2d1cf560b4ee7badbe23e51ab8e1881bb50fc2`;
the upstream release did not provide an independent digest/signature. A release
should also be reproduced with Google's tools on a standard build host before
submission. No ARM tooling binary is included in the app or repository.
The same source release supplies local ARM platform-tools 35.0.2; explicit
device commands retain the existing ADB connection.

## Upload signing and bundles

```sh
python3 tools/setup-signing.py --create-upload-key
./gradlew --no-daemon releaseCandidates
```

Upload signing is separate from debug signing. The helper creates a local
4096-bit RSA upload key and owner-readable password file under ignored
`private/signing/`. It does not enroll an app in Play App Signing. Release
builds fail when the upload-signing configuration is absent; there is no debug
key fallback. Securely back up this directory outside the development machine.
Never commit, print or send the key/password file.

Candidate outputs:

- `androidApp/build/outputs/bundle/release/androidApp-release.aab`
- `wearApp/build/outputs/bundle/release/wearApp-release.aab`

Use the validator with Google's bundletool 1.18.3 (pinned release SHA-256 is
checked by the script):

```sh
python3 tools/validate-release-bundles.py --bundletool /path/to/bundletool-all-1.18.3.jar
```

It checks bundle structure, signer identity against the configured upload key,
target/package/debug flags, least-privilege permissions and absence of the
development relay, research Activity, instrumentation, private configuration
and native libraries. It writes each decoded bundle manifest beside the AAB
for further inspection. It is not a privacy, medical or Play approval check.

Both variants use the [direct Wear Data Layer connection](production-watch-connection.md). The desktop runtime, relay client/server, pairing receiver and loopback listener have been removed. Physical pairing/recovery acceptance is required before submission.

Public policy fields live in `gradle/release-public.properties`. Set only real,
owner-verified publisher/contact information and the published HTTPS policy URL.
Empty fields keep candidate builds possible and show an unavailable-policy
message; they do not satisfy the privacy release gate. See the
[policy draft](privacy-policy-draft.md) and [data inventory](data-safety-review.md).

## Runtime migration checks

```sh
./gradlew --no-daemon :androidApp:assembleDebugAndroidTest
python3 tools/run-device-smoke.py --device DEVICE
```

Choose each device explicitly. The runner checks installed target 36, opens
Home/Noise/Voice/Privacy/Watch, tests journal cap/expiry/deletion only in isolated
preferences, sends system Back and verifies current-runtime insets. It
does not request microphone access or play hearing-aid commands. Do not run
`connectedDebugAndroidTest` indiscriminately when the watch is also connected.
The helper fails on a runner failure even when ADB's instrumentation command
itself exits zero, and never uninstalls the existing app to bypass an update failure.
Real microphone, recognition, audio, fitting and aid-response checks remain
separate acceptance tasks.
