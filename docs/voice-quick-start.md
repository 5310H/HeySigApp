# HeySig voice quick start

## Volume up and down

Say **volume up** or **volume down** to move one step. **Please turn the volume
up** and **lower the volume** also work. Volume must already be set for the
current program; otherwise say an exact value, such as **volume eight**. Program
changes use that program’s retained known targets after completed playback; unknown values need an exact command first. At 0 or 15, HeySig reports the limit and sends
nothing. These commands preserve your saved restoration snapshot.

For the native Chromebook Android app, version 0.9. Open HeySig and press **Speak command** for each request. This is push-to-talk; an always-listening Hey Sig wake phrase is not implemented. The same APK/source is being developed for later phone acceptance. The experimental [watch companion](../wearApp/README.md) has passed Wi-Fi status and spoken-reply acceptance; watch-triggered aid controls remain pending. See [six-level noise settings and Observe commands](noise-quick-start.md).

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

Ranges: volume 0–15, Sound Sharpness −8 to +7, tinnitus 0–15. Select a configured program before changing levels. Say “Balance up/down” or “tinnitus up/down” (also “tin up/down”) for one step from the current program’s known requested value. An unknown baseline requires an exact value first; range limits and unsupported tinnitus programs reject without playback. Program names come from Setup; the examples use this user's fitted mapping.

## Setup and confirmation

Review addresses and programs in Setup, then press **Save reviewed setup**. A changed dropdown is not active until saved. Tinnitus requires Supported for the selected program. Setup changes currently clear remembered program/level/snapshot assumptions; select the intended program again afterward. Setup editing is currently touch-based; complete guided voice setup is pending.

For restore, review the program and known fields in the preview. Press **Speak answer**, then say **confirm restore**, or use the Restore button. Unknown fields are skipped. Cancel preserves the record. Confirmation expires after 60 seconds.

If speech is unclear, read **Speech heard** on screen. “Program to” asks whether slot two was intended; press Speak answer and say Yes or Cancel. “Program number two” and the exact configured name avoid this ambiguity. Spoken clarification changes nothing until confirmed.

## What is remembered

Current-value replies mean **last sent**, not a query of the aids. Changes made externally are not tracked. Mark aid settings unknown on Home when appropriate. A successful program selection clears remembered levels because the new program's actual levels cannot be read. Only one last-settings record exists; completed manual setting changes replace/update it automatically. Restore does not replace its source record.

Keep the app open/unlocked, DND off, device media volume at least halfway, and use the built-in speakers. On Chromebook, also check ChromeOS host volume/output. The app explains blocked playback and does not change those system settings itself. Speak during playback stops the current operation before opening recognition; already emitted tones cannot be undone.

Recognition uses the installed Android speech service, which may use its network connection. Speech text is shown for the current session, not saved as a transcript. Physical aid response remains separate from playback completion.

See [actual acceptance results](acceptance/chromebook-manual-controls-2026-10-16.md) and [development scope](chromebook-development.md).

## Optional hands-free and locked-phone commands

See the [hands-free quick start](hands-free-quick-start.md) for the explicit
phone session, Hey Sig, Off/Pause/End, Quick Settings tile and watch controls.
Normal prompted speech remains available without enabling hands-free.

## Natural spoken commands

Phone and watch share a bounded local command grammar. You can say “please
switch to TV”, “could you set my volume to eight”, “change the sound sharpness
level to negative three”, “what's my volume?”, “which program am I using?”,
“check my batteries”, “save my settings” or “restore my settings”. Use the
configured program names and absolute levels. Polite requests can start with
please, can you, could you or would you, and end with please.

Unknown words, negated requests, multiple targets and relative requests such as
“turn it up” are not interpreted. Existing range, fitting, confirmation and
execution checks still apply. This adds no model, audio recording, transcript
history or network service; the installed speech recognition provider retains
its existing data handling. Physical phone/watch recognition acceptance remains
pending; grammar checks and debug builds do not establish hearing-aid receipt.

## Personal phrase learning

Open **Settings → Voice & speech → Voice tools & personal phrases**, or use phone/watch voice:

1. Say **learn phrase TV time means program TV**. The meaning must be an existing
   program, absolute level, battery or current-value command.
2. Review the phrase and meaning. Say **remember that correction** within one
   minute on the same device. A generic “yes” does not save it.
3. Nothing is sent while learning. Say **TV time** separately when you want the
   ordinary validated command to run. Earlier playback cannot be undone.

Say **list my phrases**, **forget phrase TV time**, **learned phrases off/on**,
**reset learned phrases**, then **confirm reset learned phrases** to manage them.
Lists show ten mappings per page; say **list my phrases page 2** for more.
Cancel preserves saved mappings. To edit, forget an exact phrase and teach it
again. Off retains mappings but disables their use. UI controls provide the
same review, add, forget, disable and confirmed reset actions.

Mappings are exact, phone-owned and available to phone/watch interpretation.
Reserved commands, numbers, negated/compound phrases and existing valid
commands cannot be replaced by learned phrases. The 200-mapping limit never
silently evicts a record. Setup changes deactivate old mappings, including if
you later change back; review and re-teach them deliberately.

Only separately confirmed phrase text, meaning, setup generation, source and
confirmation time are stored locally. No raw audio or conversation history is
added. General data export excludes this separate phrase store; Delete local
app data removes it. Phrase-specific export/import and conversational “No, I
said…” correction are not implemented in this initial teaching flow. This does
not retrain the installed speech recognizer. Physical voice and watch acceptance
remain required.

## Protecting saved settings

**Save settings** explicitly captures the known last-sent program and values.
Later commands update last-sent state without replacing this saved snapshot.
Say **what is saved?** or **read saved settings** on phone/watch to review it;
**restore settings** still requires its separate confirmation. Unknown fields
are skipped and partial/interrupted restoration leaves the original snapshot
intact. Existing stored snapshot bytes are retained on upgrade without an
implicit Save. A damaged snapshot is preserved and restore blocked while the
valid fitting and manual controls remain available; an explicit valid Save can
replace it. This changes earlier development builds that automatically replaced
the saved record after manual changes.

## Guided setup, program aliases and reviewed import

Say **configure hearing aids**, then **setup help** for exact draft commands.
Examples: **set control address to 12**, **name program one as Universal**, and
**set tinnitus support for program one to supported**. Use only verified fitting
values. Say **review aid setup**, then **save aid setup**, or **cancel aid setup**.
Drafts expire after five minutes; saving a changed setup archives previous settings
and clears active assumptions. No aid command is sent. Phone and forwarded watch
voice use the same workflow. The phone Aid setup screen can import a HeySig export
into a reviewed draft; exported commands and snapshots are never replayed.

For a program alias say **when I say movie time I mean program TV**, then
**remember that correction** after reviewing it. Use the phrase separately to
execute the command. Voice and speech offers separate active-phrase export and
reviewed import. Imported mappings require explicit confirmation and never replay
an earlier request. A chosen document provider may upload exported files; delete
exported copies separately. Recognition measurements remain pending under the
[controlled evaluation plan](voice-evaluation.md).

File actions can also be requested with **import aid setup**, **import personal phrases** and **export personal phrases**. Android document selection/review happens on the phone; a hands-free-only watch session directs you to open the phone. No aid playback occurs.
