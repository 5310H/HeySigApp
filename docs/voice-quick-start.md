# HeySig voice quick start

For the native Chromebook Android app, version 0.8. Open HeySig and press **Speak command** for each request. This is push-to-talk; an always-listening Hey Sig wake phrase is not implemented. The same APK/source is being developed for later phone acceptance. The experimental [watch companion](../wearApp/README.md) has passed Wi-Fi status and spoken-reply acceptance; watch-triggered aid controls remain pending. See [six-level noise settings and Observe commands](noise-quick-start.md).

## Voice sound switches

On Chromebook/phone, open **Voice**. On the watch, scroll below Stop. **Voice prompts** controls confirmation guidance; **Spoken responses** controls status, results and errors. Each device saves its own choices independently. Both may be off; text replies remain visible. Saying “voice responses off” turns off its own spoken acknowledgement. Turn responses back on by voice or touch.

Say “voice prompts off,” “voice prompts on,” “voice responses off,” or “voice responses on.” These settings do not change media volume or hearing-aid levels. The installed recognition service may still provide its own sounds. The previous phone “Speak playback completion” preference is used as the initial response setting when upgrading.

## Everyday commands

| Action | Say |
|---|---|
| List configured programs | “list programs” |
| Select Universal | “set program to Universal” |
| Select Sleep | “set program to Sleep” or “program number two” |
| Select another slot | “program number five” (slot must be configured) |
| Ask remembered program | “current program” |
| Set volume | “volume eight” |
| Set Sound Sharpness | “balance zero” or “sound sharpness four” |
| Set tinnitus | “tin zero” or “tinnitus four” |
| Ask remembered levels | “current volume,” “current balance,” “current tin” |
| Request battery indications | “check left battery,” “check right battery,” “battery check” |
| Save known requested settings | “save settings” |
| Restore known saved settings | “restore settings” |
| Explicitly resend a repeated target | “send again” after the already-requested message |
| Cancel | “cancel” or “stop” |

Ranges: volume 0–15, Sound Sharpness −8 to +7, tinnitus 0–15. Select a configured program before changing levels. Relative up/down voice commands are not implemented in this slice. Program names come from Setup; the examples use this user's fitted mapping.

## Setup and confirmation

Review addresses and programs in Setup, then press **Save reviewed setup**. A changed dropdown is not active until saved. Tinnitus requires Supported for the selected program. Setup changes currently clear remembered program/level/snapshot assumptions; select the intended program again afterward. Setup editing is currently touch-based; complete guided voice setup is pending.

For restore, review the program and known fields in the preview. Press **Speak answer**, then say **confirm restore**, or use the Restore button. Unknown fields are skipped. Cancel preserves the record. Confirmation expires after 60 seconds.

If speech is unclear, read **Speech heard** on screen. “Program to” asks whether slot two was intended; press Speak answer and say Yes or Cancel. “Program number two” and the exact configured name avoid this ambiguity. Spoken clarification changes nothing until confirmed.

## What is remembered

Current-value replies mean **last sent**, not a query of the aids. Changes made externally are not tracked. Mark aid settings unknown on Home when appropriate. A successful program selection clears remembered levels because the new program's actual levels cannot be read. Only one last-settings record exists; completed manual setting changes replace/update it automatically. Restore does not replace its source record.

Keep the app open/unlocked, DND off, device media volume at least halfway, and use the built-in speakers. On Chromebook, also check ChromeOS host volume/output. The app explains blocked playback and does not change those system settings itself. Speak during playback stops the current operation before opening recognition; already emitted tones cannot be undone.

Recognition uses the installed Android speech service, which may use its network connection. Speech text is shown for the current session, not saved as a transcript. Physical aid response remains separate from playback completion.

See [actual acceptance results](acceptance/chromebook-manual-controls-2026-10-16.md) and [development scope](chromebook-development.md).
