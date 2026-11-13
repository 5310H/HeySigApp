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
