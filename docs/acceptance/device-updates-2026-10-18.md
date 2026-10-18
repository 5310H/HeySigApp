# In-place development updates — October 18, 2026

Source: working tree based on commit 43658e1. Tester: Codex. Development IDs and
existing debug signers preserved. No store submission or release declaration was
performed. Owner requested release prerequisites remain pending.

Implemented: relative Balance/tinnitus commands, reviewed guided setup and Help,
reviewed fitting import, conversational personal-phrase proposals and separate
reviewed phrase import/export. See [feature evidence](guided-setup-and-phrases-2026-10-18.md).

Validation: shared Gradle tests passed (11 JUnit methods wrapping shared safety
checks), phone/Wear development APKs and instrumentation APK built, both release
Kotlin compilations passed, phone required-DEX-class packaging check passed.
The read-only settings fingerprint runner subsequently built successfully.

| Device | OS/API observed | Update and checks |
|---|---|---|
| Pixel 9a (192.168.69.130:38471) | Android 17, API 37 | In-place update, Home/Settings and isolated ProgramSettingsSmoke passed; protected preference hashes unchanged. |
| staryu (emulator-5554) | Android 13, API 33 | In-place update, Home/Settings and isolated ProgramSettingsSmoke passed; protected preference hashes unchanged. |
| Pixel Watch 3 (192.168.69.190:37001) | Android 17, API 37 | In-place update, WatchActivity launch/process passed; all watch shared-preference hashes unchanged. |

Pixel/Chromebook protected preferences are core fitting/requested/restoration/
per-program settings, personal phrases, sleep and noise. Only digests are emitted;
private before/after digest records stay in /tmp and are not committed. The
Chromebook kernel disables run-as, so a read-only Android instrumentation runner
reads the same private preference files without edits, UI or audio. New workflow,
archive, phrase import/export and relative snapshot checks use separate disposable
preferences and fake transport. No microphone access, user fitting edit, aid
setting change or real acoustic command was used to verify installation.

Physical acceptance remains open: actual aid reception/calibration, recognition
providers and speech settings, overnight/Doze/reboot reminders, production pairing,
large-font/accessibility/round-watch review and endurance/battery. Sleeping-watch
shake activation remains unimplemented; foreground shake is unchanged. Noise
Automatic remains source-locked. These updates do not establish Play approval.

Development APK SHA-256:

- `androidApp/build/outputs/apk/debug/androidApp-debug.apk`: `1d3c7dff02306363c0756ce4a2b58c71556f716c011eec68447393be0aef8cd9`
- `wearApp/build/outputs/apk/debug/wearApp-debug.apk`: `4753920a3d53bd479a8f9339ea884fe5f79615e1be1fab152e29accc4ece33b2`
