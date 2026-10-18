# Refresh controls — October 18, 2026

Home replaces Batteries with Refresh. Settings exposes Battery check; Saved
settings no longer has a Restore button. Existing saved records are preserved.

Refresh freezes the current requested program and known levels for review, then
resends exactly that plan through the existing validated executor. Unknown values
stay unknown; per-program memories and saved snapshots do not fill the gaps.
Confirmation expires after 60 seconds and is bound to the initiating device.
Nothing sends on startup. Playback completion is not proof of aid reception.

Voice supports refresh, refresh settings, refresh current settings, refresh aids
and refresh hearing aids, including ordinary polite wrappers. Say confirm refresh
or cancel after the prompt. Foreground phone/watch routing and hands-free commands
use existing independently optional voice prompts and spoken responses. Speech-off
status/results stay visible. Existing restore-settings voice commands remain.

Regression coverage checks anchored parsing/negation, exact current-state plans,
unknown-level skipping, independent snapshot protection and Home/Settings action
placement using separate test preferences and fake playback. No physical aid
commands are needed to validate this change.

Validation: shared tests, phone/Wear debug APKs and phone test APK passed in one
Gradle invocation. Required application DEX classes passed the packaging check.
In-place phone and Chromebook updates succeeded; both launched with Status: ok.
ProgramSettingsSmoke passed on both devices, including current refresh planning,
unknown-level skipping, snapshot protection and Home/Settings action placement.
No microphone access or real aid playback was used. Audible TTS, physical aid
reception, large-font and round-watch acceptance remain unverified for this change.
