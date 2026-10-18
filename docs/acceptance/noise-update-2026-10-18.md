# Noise development update — October 18, 2026

Tester: Codex. Phone: owner-provided ADB endpoint `192.168.69.130:38471`.
Build: development APK SHA-256
`1d3c7dff02306363c0756ce4a2b58c71556f716c011eec68447393be0aef8cd9`.

`tools/check-noise.sh` passed measurement, Observe, calibration and guarded
automation checks. The required application DEX class packaging check passed.
The existing development APK was installed in place with `adb install -r`;
installation returned Success. No source change or new build was required.

Read-only hashes of existing core and personal-phrase preferences matched
before and after installation. Sleep and Noise preference files were absent
both times. Private hashes remain in `/tmp`; no preference contents were read
into this record. No fitting edit, microphone capture or aid playback was
performed. Device OS was not rechecked in this session.

Physical calibration, UI/device acceptance and aid reception remain pending.
Real Automatic remains source-locked. This update does not establish Play approval.

## Chromebook and watch follow-up

Both development updates returned Success using in-place `adb install -r`:

- Chromebook `emulator-5554` (staryu): same phone APK as above. Read-only
  SettingsFingerprintSmoke passed before/after; core, personal phrases, Sleep
  and Noise fingerprints matched exactly.
- Watch `192.168.69.190:37001`: watch development APK SHA-256
  `4753920a3d53bd479a8f9339ea884fe5f79615e1be1fab152e29accc4ece33b2`.
  All existing shared-preference file hashes matched before/after.

Device OS versions were not rechecked. No microphone capture, aid playback or
physical calibration was performed. Install success is not UI/physical acceptance.
