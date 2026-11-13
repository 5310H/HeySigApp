# Automatic status consistency — November 13, 2026

Owner reported that the app switched to TV but showed Off on both the phone
Noise and Choose programs screens. Read-only inspection found the phone
microphone foreground service running, TV as last requested program, and 27,
then 33 completed checks. The Noise screen's button and summary showed On during
inspection. The reported incorrect main-screen label was not reproduced.

Two inconsistent status paths were found in code: detailed/voice noise status
read the older level controller without checking the active speech-aware service;
Choose programs could show a retained Off message while a start request was
pending. Those paths now prioritize the speech-aware service or its Starting
state. Noise buttons also initialize from the current service state instead of
being constructed with Off before their first refresh. This is a status-only
change; microphone scheduling, TV routing, saved choices, cooldown, program
commands and fitting are unchanged.

The previous apparent lack of microphone activity had a separate diagnosed
cause: saved input was watch, and that session had stopped for watch silence or
disconnection. At the owner's request, the app's Save action selected phone input,
preserving all saved TV/normal program roles. Later read-only inspection confirmed
phone capture and TV as last requested program. No automatic restart or aid
command was initiated by these diagnostic checks.

Validation uses the existing phone/watch workflow and UI smoke checks. Runtime
reproduction of the owner's intermittent Off label is not claimed; verify the
next occurrence against service status rather than inferring capture from a
label. A status fix does not validate detector accuracy or hearing-aid reception.

## Validation and installation

[Build aef19f7 checks](https://github.com/5310H/HeySigApp/actions/runs/43524599004)
passed shared tests, both APK/test-package builds, lint, native/APK verification
and API 36 emulator smoke. No controller/model/threshold change was included.
The source commit was pushed before APK generation and installation.

Automatic was stopped using its normal On/Off control after checking that no
playback was pending. Both main/test packages were upgraded in place using the
existing development signer, verified against each previously installed signer.
Both installed main APK hashes match the verified signed candidates. All 37
phone preference files (13 user files) and the watch's one preference file
matched before/after installation. Phone preferences also matched after launch.

The phone visibly showed **Build aef19f7 · Nov 13, 2026** and TV as its last
requested program. Phone microphone, TV slot 5 and Watching TV choices were
preserved. No aid commands were sent by these update checks. Automatic remained
stopped after reopening; an explicit On is required. No new watch UI pass or
physical TV/quiet-return acceptance is claimed. The original intermittent label
on the main Noise screen still needs observation during actual use; code-path
corrections are not proof that every reported instance has been reproduced.

[Scalar installation records](automatic-status-2026-11-13) retain the exact source
and installed-byte verification without recording wearer audio or transcripts.
