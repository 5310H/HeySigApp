# Voice controls cleanup — October 21, 2026

The phone voice screen puts session status and Start/Resume first, followed by
Pause, Off, End, Shake to speak and independently optional speech choices.
Phrase management, typed commands, recognition testing and microphone settings
are on Voice tools & personal phrases. Back returns from tools to voice.
The Home phrase shortcut opens the phrase tools directly.

The listening indicator is also a 48dp shortcut to Voice & speech and remains
available when there is no session. Within a session, status updates change
the status text without rebuilding the voice page and losing its scroll
position. Starting or ending a session refreshes the session controls.

Both watch variants label remote session controls Phone hands-free. Watch status
text wraps so result and error text remains readable in the scrollable layout.
This does not add a watch wake word or change watch microphone behavior.

The previously accepted phone wake threshold adjustment is retained. Existing
fitting, saved snapshots, phrases, speech preferences and gesture choices keep
their storage formats. Opening a screen never starts microphone access.

## Validation

Pinned Gradle shared checks, both debug APK builds and the phone instrumentation
APK build passed with the documented ARM aapt2 override. The development APK
class-definition check and patch whitespace check passed.

Installed on the Pixel 9a as a compatible development update. All shared
preferences had the same aggregate fingerprint immediately before and after
installation. No uninstall, data clearing or fitting change occurred.

The existing no-microphone/no-playback Api36Smoke runner passed on the phone:
navigation, 48dp touch targets at font scale 1.5, isolated storage/executor checks,
and real watch-layout rendering in isolated 192dp round contexts at font scales
1 and 2. Native wake-model initialization and silence decoding passed. These
watch contexts do not establish physical watch acceptance.

Additional UI checks passed for the Off-status shortcut, the tools screen,
Back to voice, and the Home phrase shortcut. Local screenshots were inspected.
The existing authorized session was restarted through its visible Start button;
the screen showed Listening for Hey Sig, with prompts and replies On. UI testing
did not issue aid commands. The earlier user-reported wake-word/command success
is retained as separate evidence; speech was not injected during these checks.

Evidence: ignored `private/voice-cleanup-2026-10-21/device-smoke.txt`, preference
fingerprint, and Off/listening screenshots. The watch debug APK was built but
not installed because no physical watch debugging connection was available.
