# Chromebook manual-control acceptance — October 16, 2026

App: HeySig 0.4 for the initial checks, then 0.5 for the final numbered-program check, in the Chromebook Android environment. Voice uses the installed Android speech activity and the shared deterministic parser; acoustic requests use the corrected speaker transport. This is separate from earlier Pixel acceptance and the silence-only transport diagnostic.

Starting aid program reported by user: Sleep, slot 2. Restore the starting program after listening tests. There is no acoustic state query; user listening/beeps and remembered requests have separate provenance. No full recognition transcript was collected.

| Test | User-confirmed result |
|---|---|
| Voice “program one” from Sleep | One beep and Universal; passed |
| Voice “program two” return to Sleep | Failed resolution: user reported “choose a program or slot”; transcription not collected |
| Voice “set program to Sleep” | Two beeps and Sleep restored; passed |
| Voice volume eight → twelve in Universal | User confirmed volume increased; passed. Baseline restoration later confirmed for volume 8, sharpness 0 and tinnitus 0 |
| Voice balance zero → four in Universal | User confirmed sharper sound; passed. Baseline restoration later confirmed for volume 8, sharpness 0 and tinnitus 0 |
| Tinnitus capability setup | User confirmed Universal is configured Supported; no assumption based on program name |
| Voice tin zero → four in Universal | User confirmed the signal changed after Supported was explicitly saved and Universal reselected; passed. User later confirmed return to volume 8, sharpness 0 and tinnitus 0 |
| Voice save/restore | User confirmed Save → Restore preview → Speak answer → confirm restore completed with Universal / volume 8 / Sound Sharpness 0 / tinnitus 0 |
| Voice “program number two” on 0.5 | User confirmed two beeps and Sleep; passed. Starting program restored after the manual tests |
| Native watch commands | Not implemented/accepted |

Voice battery reception passed separately in [the Chromebook playback acceptance record](chromebook-playback-2026-10-16.md). Offline parser tests are not a substitute for the live voice/acoustic checks listed here. Only explicitly confirmed physical results are marked passed.

## Numbered-program recognition follow-up

The failed “program two” request produced a program-resolution instruction; the actual recognized text was not displayed by 0.4 and was not collected. “set program to Sleep” then succeeded. Do not attribute the failure to a specific transcript without evidence.

Version 0.5 adds visible current-session speech text, explicit “program number two” / “program slot two” variants, and a confirmation for the ambiguous “program to.” It does not learn an alias automatically or send a guessed slot. Confirmation expires after 60 seconds and supports Cancel. Sound Sharpness phrasing and additional current-level query forms are included; contradictory numeric signs are rejected. Speak/Stop controls move above the manual controls to reduce scrolling.

The shared regression passed 410 phrase cases plus fitting, context, ranges, tinnitus capability and restore-plan checks including the final Cancel-priority regression (which also passed). This is offline interpretation coverage; the new Android recognition flow and aid responses require separate acceptance. No settings or aid programs are changed by these local parser checks.

The initial tinnitus request was blocked with “setup tinnitus support first.” After the user was directed to Save reviewed setup, reselect Universal, and send tin zero/four, they confirmed the signal changed. This accepts the successful saved-configuration path; no recognized transcript was collected for the blocked request.

Save/restore acceptance covers the stored record and user-confirmed completion with the displayed/requested settings. No external aid reset, charging cycle or independently changed settings were introduced for this check; do not infer reset recovery or independent electronic aid readback.

## Final state and scope

Version 0.5 built, signature-verified, installed on the Chromebook and launched successfully. The user confirmed “program number two” produced two beeps and Sleep. The actual aids are therefore user-confirmed back in their starting program. Universal test levels had previously been returned to volume 8 / sharpness 0 / tinnitus 0. Selecting Sleep does not establish its numeric levels; they remain unknown to HeySig.

This accepts representative live Chromebook voice paths for programs, volume, Sound Sharpness, tinnitus and saved-request restoration, alongside the earlier battery check. It does not certify all levels, all fitted programs, every speech alternative, charging recovery, phone acceptance of this new core, or native watch transport. The original numbered phrase failure and initially unsaved tinnitus setup remain part of this record.
