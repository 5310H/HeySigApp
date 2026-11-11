# GitHub build and test workflow

The **Phone and watch checks** Action runs on pushes to main, pull requests and
manual dispatch. It compiles both development apps and their instrumentation
packages, runs shared policy checks and JVM tests, checks Android lint and pinned
APK/native contents, and runs the silent API 36 emulator smoke runner. The pinned
Gradle wrapper and Kotlin compiler are used on GitHub's Ubuntu runner. Heavy
compilation and emulator work no longer need to run on the Chromebook.

The navigation smoke test uses an explicitly requested synthetic in-memory
fitting on the fresh CI emulator. The test refuses this mode unless hardware is
`ranchu` and the app's core settings are empty. It never saves a wearer fitting,
opens the microphone or emits hearing-aid commands. Physical tests use their
existing settings and do not enable this fixture mode.

The API 36 baseline uses the Pixel 7 hardware profile. An initial generic
320×640dp emulator required scrolling to reach the Home “Teach a phrase” action,
failing the baseline test's all-actions-visible assertion. That result is
retained as a small-viewport limitation; this workflow does not establish full
phone screen-size coverage. Round-watch controls have separate device checks.

Download `development-checks-<full commit>` from the run's artifacts. It includes
APKs, source revision, APK hashes, test and lint reports. These are development
builds, never production or Play submission artifacts. A failed job can still
upload reports or partial APKs; use only a successful run for a device candidate.

GitHub generates one disposable signing key for both apps and tests in each run.
No existing device key, upload key, relay secret or signing password is uploaded.
The key itself is not an artifact. Downloaded APKs must be re-signed locally with
the existing development key before updating installed phone/watch apps. Re-sign
both app and test packages together, verify their certificates and retain the
original hash manifest separately; signing changes APK hashes. This small local
operation does not compile code. Never uninstall an existing app to bypass a
signature mismatch, because that can erase fitting and saved settings.

Use the signing helper after extracting a successful run's artifact:

```sh
python3 tools/sign-cloud-development.py \
  --artifact /tmp/heysig-cloud \
  --commit FULL_40_CHARACTER_COMMIT \
  --output /tmp/heysig-device-candidate \
  --apksigner /path/to/android-sdk/build-tools/35.0.0/apksigner
```

It checks the expected source and original APK hashes, signs all four app/test
APKs with the existing local development key, verifies their certificates and
writes new hashes. It refuses to overwrite an output directory and does not
install anything. Confirm the workflow run succeeded before invoking it.

Paired phone/watch Data Layer checks, actual microphone behavior, screen-off
lifecycle, battery measurements and hearing-aid reception/listening acceptance
still require the physical devices. The emulator does not establish those facts.
Release signing, release validation and Play review remain separate requirements.

Checklist for updates:

- [ ] Commit and push the intended changes, preserving unrelated local work.
- [ ] Wait for a successful **Phone and watch checks** run for that exact commit.
- [ ] Review reports and download the matching development artifacts.
- [ ] Re-sign locally with the existing development key and verify certificates.
- [ ] Preserve settings evidence and install with an in-place update.
- [ ] Run the relevant physical checks and record actual results separately.
