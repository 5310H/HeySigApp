# Phone voice verification — October 11, 2026

## Working phone entry

Run `SIG3_VoiceCommand` from Tasker, then speak a supported phrase. The new public task uses Tasker's existing Get Voice action envelope and passes recognized text directly to `SIG3_VoiceRouter`. Empty or cancelled recognition sends no command. It needs no AutoVoice event profile or ongoing ADB connection.

The user confirmed that the prompt appeared and saying **current program** opened Signia and showed the selected program. Screen inspection showed the active Universal header. Tasker results collected afterward:

```text
SIG3_CommandOK = 1
SIG3_Result = Program: Universal; program=Universal (app state; aid receipt unverified)
SIG3_Error = empty
SIG3_Program = Universal
```

Exported the active phone project to `/tmp/HeySig3-voice-active-20261011.prj.xml`. Its VoiceCommand action-code sequence and Java bodies match the repository. Tasker assigned phone task ID 364; repository ID 380 is unique within the generated project. The existing InternalAppLaunch Java also matches, confirming it was preserved. Tasker normalizes imported plugin action codes, so raw code comparisons for that helper differ.

## AutoVoice event path

AutoVoice 4.0.14 is installed and microphone permission is granted. The original HeySig recognition profile was disabled and pointed to `SIG_VoiceRouter`. Backed up that original project to `/tmp/HeySig-before-voice-20261011.prj.xml`, changed the profile entry task to `SIG3_AutoVoiceCommand`, and enabled the profile. The existing Monitor Signia App profile was preserved.

AutoVoice's built-in typed **current program** test successfully delivered an event and opened Signia. This establishes event delivery, separately from speech recognition. Direct launches of ActivityGetVoice, including its advertised recognition-shortcut action, did not display a microphone prompt; the user confirmed both failures. The cause remains unresolved. No default-recognizer or natural-language settings were changed. Tasker's working built-in prompt is the verified voice entry for now.

## Repository verification

- Prompt routing tests passed for a current-program query, a numeric volume command, and cancellation without movement.
- Existing prompted mode/battery, aliases, automation, and adapter checks passed.
- Generated-action independence check passed.
- XML validation passed: 82 files, 5,107 actions, zero failures.

An initial new-task ID collided with InternalAppLaunch. Validation caught it, the import helper was stopped, and the new task was regenerated with an unused ID before the corrected import and successful test.

## Spoken program-change follow-up

Captured the starting program as Universal. The first spoken "program two" attempt was transcribed as "program too", as reported by the user, and did not establish a successful change. Added "too" as an alternative for slot 2 in the program/mode routing pattern, without changing configured program names. Simulated prompted routing to a differently named slot 2 passed; XML validation remained at zero failures. Imported the corrected router for a live retry.

The user's requested future Save settings / Restore settings feature, including charging/restart behavior, is recorded in the [remaining-work list](https://github.com/5310H/HeySig/blob/HeySid3/docs/HeySig3-remaining-work.md#future-save-and-restore-settings-after-charging).

## Try again / Cancel acceptance

Added an explicit retry dialog to `SIG3_VoiceCommand`. Missed or unrecognized speech clears stale command success and offers Try again / Cancel. Try again captures a new phrase; it does not replay a previous hearing-aid command. A matched command ends the voice session even if its worker operation fails. Dialog dismissal and a 60-second timeout stop the session; attempts are bounded at 20 per session.

Imported the updated VoiceCommand and VoiceRouter into the phone. The user said **banana**, selected **Try again**, then said **current program**, and confirmed that retry reopened the prompt and Signia showed the program. Screen inspection showed **Off just Tinnitus** as the actual active program. The query did not request a program or slider change. Live Cancel-button acceptance is recorded below; Back and timeout remain separate checks.

Retry flow simulations passed for empty/unrecognized speech followed by a valid phrase, Cancel/Back/timeout without movement, clearing stale success, and no repeat after a routed success or failure. Numeric routing still passed all 768 transitions; generated-action independence and parity passed. Static validation: 82 XML files, 5,287 actions, zero failures. Dialog implementation follows Tasker's [temporary Activity API](https://tasker.joaoapps.com/userguide/en/help/ah_java_code.html), with a bounded wait and UI-thread cleanup.

Next requested enhancement: spoken confirmations using actual program names and measured values, plus spoken failure instructions. Keep on-screen messages available and sequence speech after Signia's acoustic command has completed; no spoken success before verified readback.

## Spoken result acceptance

Implemented spoken replies in the general `SIG3_VoiceCommand` path. Its result formatter requires a fresh worker operation, uses verified program/value readback for success, and speaks concrete error instructions for failed commands. Incomplete or stale results receive no success claim. Unrecognized phrases speak a retry instruction. `%SIG3_VoiceResponses=0` disables speech; unset or 1 enables it.

The Say action reuses the original phone-exported envelope with the default TTS engine, media stream, audio-focus behavior, and speech completion before continuation. A two-second gap follows the completed command; speech is skipped when SIG3_Worker is active. This gap has not been established as proof of acoustic hearing-aid completion. Phone volume and DND are never changed, and text remains available if speech is inaudible.

Imported the updated general voice task and ran a spoken **current program** query. The user confirmed that the name spoken aloud matched the selected program on the screen. This is the first live acceptance of a spoken result. Other command families, blocked commands, the spoken retry instruction, and TTS failure need their own live acceptance.

Collected CommandOK=1, Error empty, and Program=Off just Tinnitus. The filtered Tasker variable list did not display the helper-only VoiceReply variable, so that field was not accepted as a captured speech log. Exported the active project to `/tmp/HeySig3-spoken-active-20261011.prj.xml`: the general voice task has 56 actions, matching the repository count, and all embedded Java bodies match. Both Say actions preserve the exported media/default-engine settings, audio-focus flag, network-off flag, and continuation-off flag. User confirmation establishes the heard response separately from those export checks.

Fourteen cases executed the actual formatter in local BeanShell: arbitrary program names, numeric/negative levels, DND/low-volume/unavailable-tinnitus errors, missing errors, stale operation IDs, incomplete results, battery wording, retry instructions, speech disabled, and worker busy. Numeric routing (768 transitions), retry/parity simulations, generated-action independence, and static validation passed. Static validation: 82 XML files, 5,341 actions, zero failures. Tasker's [Say documentation](https://tasker.joaoapps.com/userguide/en/help/ah_say.html) describes the speech engine, stream, and completion behavior.

## Spoken program change and restoration

Captured the actual starting active header, Off just Tinnitus, before opening the prompt. The user said **program one**; screen inspection showed Universal, and the user confirmed both the selection and spoken "Program is Universal" result. Opened a second prompt for **program two**; the user confirmed selection of Off just Tinnitus and its matching spoken name. Final screen verification required the active header to equal the captured starting name. No slider command was requested. Program labels in this evidence describe actual configured selections, not hardcoded controller names.

## Current-value voice queries

Before a proposed one-step volume test, public VolumeCurrent passed with CommandOK=1, Error empty, Level=8, and Program=Off just Tinnitus. Saved those starting observations in `/tmp/HeySig3-spoken-slider-start-20261011.json`.

The user initially reported a changed screen without speech, then corrected that report: the attempt showed Try again. Retrieved VoiceHeard=**current volume** and Error=**Voice command not recognized**. This establishes an unsupported query, not a successful slider change or a TTS failure. Added read-only voice routing for current volume, current balance, and current tinnitus, with question-form alternatives, and imported the corrected router for retesting.

Simulation verified routing/readback without gestures for all three queries. The balance/tinnitus test fixture needed its family knob IDs set to the simulated control ID; the first fixture run failed its readback checks and the corrected fixture passed. Numeric routing (768 transitions), actual spoken-result formatting (14 BeanShell cases), and XML validation passed. Static validation: 82 files, 5,371 actions, zero failures.

The user confirmed that **current volume** spoke **8** without Try again, matching the captured starting volume. The user then confirmed that **current balance** spoke the balance displayed on the phone. Subsequent screen inspection showed value **0**, Selected Sound Sharpness Tab, and active program **Universal**. This differs from the earlier volume-test program; the cause of that intervening program change has not been established. These are read-only query acceptances; they do not establish spoken slider-change acceptance.

The user confirmed that **current tinnitus** spoke the level shown on the phone. Subsequent screen inspection showed value **0**, Selected Tinnitus Signal Tab, and active program **Universal**. Spoken current-volume, current-balance, and current-tinnitus queries have now each passed live user acceptance. No slider Set command was issued during these query tests.

## Spoken battery-check acceptance

Opened the general phone voice prompt for **check battery**. The user confirmed that Signia ran the battery prompts and spoke **Battery checks requested. Listen for the hearing aid indications.** This verifies the app sequence and spoken request confirmation; it does not establish battery percentages or hearing-aid indications. Battery checks use the top-right question-mark battery icon and have no slider.

## Remaining live speech checks

Spoken failure/retry messages, and Back/timeout still need individual phone acceptance. AutoVoice microphone capture, a convenient home-screen voice shortcut, watch voice events, and hands-free activation remain separate work. No always-listening service or wake word was enabled. Program selection and speech are app-accepted; acoustic hearing-aid receipt remains unverified.

## Spoken volume-change test

Fresh public VolumeCurrent screen read showed volume **8**, Selected Volume Tab, and active program **Universal**. Saved the starting observation to `/tmp/HeySig3-volume-voice-change-20261011.json`. Opened the general voice prompt for **volume seven**; the user confirmed that the screen showed **7** and speech said **Volume is 7**. Independent screen read confirmed **7** in Universal on the Volume tab. Opened a second voice prompt for **volume eight**; the user confirmed restored volume and **Volume is 8** speech. Final screen assertions matched the captured starting volume **8** and program **Universal**, with Volume selected. The spoken volume-change and restoration test **8 → 7 → 8** passed app and user speech acceptance; acoustic hearing-aid receipt remains unverified.

## Spoken balance-change and restoration

Fresh BalanceCurrent read showed **0** in Universal on Selected Sound Sharpness Tab; saved to `/tmp/HeySig3-balance-voice-change-20261011.json`. The proposed prompt was balance one, but the user clarified they actually used **-1**. User confirmed the screen and speech worked, and independent screen read showed **-1** in Universal. This accepts the actual negative-level test, not the proposed positive target. The user then confirmed **balance zero** restored the display and spoke **Balance is 0**. Final screen assertions matched the saved starting balance **0**, program Universal, and selected balance tab. App and spoken-result acceptance passed for **0 → -1 → 0**; hearing-aid receipt remains unverified.

## Spoken tinnitus-change and restoration

Fresh TinnitusCurrent screen read showed **0** in Universal on Selected Tinnitus Signal Tab. Saved the starting state to `/tmp/HeySig3-tinnitus-voice-change-20261011.json`. The user confirmed **tinnitus one** displayed **1** and spoke **Tinnitus is 1**; independent screen read confirmed level 1 and the selected tinnitus tab in Universal. The user then confirmed **tinnitus zero** restored level **0** and spoke **Tinnitus is 0**. Final screen assertions matched the captured starting level **0**, program Universal, and selected tinnitus tab. The **0 → 1 → 0** test passed app and speech acceptance; acoustic hearing-aid receipt remains unverified.

## TV unavailable-tinnitus spoken response

Before testing, the actual phone header already showed **TV Active**; no program switch was requested. Saved the starting header to `/tmp/HeySig3-tv-voice-start-20261011.json`. Opened the general voice prompt for **current tinnitus**. The user reported speech began **Command stopped**, then confirmed it explained tinnitus was unavailable without Try again. Tasker result capture showed `%SIG3_Error = Tinnitus unavailable for this program` and `%SIG3_CommandOK = 0`. This is an accepted blocked query with a spoken reason, not a successful tinnitus read or change. TV balance speech and tinnitus Set rejection need separate tests; TV volume acceptance is recorded below.

## TV spoken volume-change and restoration

Fresh VolumeCurrent screen read showed **8** in the actual selected **TV** program on Selected Volume Tab. Saved to `/tmp/HeySig3-tv-volume-voice-20261011.json`. The user confirmed **volume seven** displayed **7** and spoke **Volume is 7**; independent screen read confirmed level 7 in TV. The user then confirmed **volume eight** restored **8** and spoke **Volume is 8**. Final screen assertions matched captured volume 8, TV program, and selected Volume tab. TV stayed selected throughout these screen observations. The **8 → 7 → 8** test passed app and speech acceptance; hearing-aid receipt remains unverified.

## Live Cancel after unrecognized speech

Opened the general voice prompt for **banana**, followed by explicit **Cancel**. The user confirmed the dialog closed without another voice prompt or a Signia control change. Captured `%SIG3_CommandOK = 0`, `%SIG3_Error = Voice command not recognized`, and `%SIG3_Result = Voice command not recognized; no command sent`. This verifies the Cancel-button path and clearing stale success. Back dismissal, timeout, and cancellation inside the recognition prompt remain separate live checks.

## Acceptance clarification from the user

The user clarified that the aids provide confirmation only for battery checks, and those indications already work. Remove audible aid-confirmation requirements from program/slider completion tests; use fresh Signia readback and command results. This does not add independent acoustic-receipt evidence for those commands. Battery indications are user-confirmed, without a measured battery percentage.

## Initial Back report and explicit handler

The user reported that phone Back did not close the retry dialog after **banana**. Subsequent screen inspection still showed HeySig3 voice with Cancel/Try again. Added an explicit KEYCODE_BACK listener that consumes Back and cancels on key release, with cancelability explicit. Retry-flow simulations, independent generation, and XML validation passed, but these do not establish Android Back/gesture behavior. The corrected task was imported; later live acceptance is recorded below.

## Live Back dismissal accepted

Earlier Back reports were ambiguous: the user clarified they had spoken back while recognition was inactive, and a later disappearance could have been timeout. These do not establish a physical Back failure or timeout acceptance. After importing the explicit Back-key handler, a fresh **banana** test was repeated with instructions to use the phone Back button/gesture immediately. The user confirmed that Back closed the retry dialog immediately, without reopening recognition or changing controls. Physical Back dismissal is now accepted; timeout and cancellation within recognition remain pending. No claim is made that the earlier implementation had a reproducible physical Back defect.

## Live retry-dialog timeout accepted

Opened the general voice prompt for **banana** and instructed the user to leave Try again / Cancel untouched for approximately 60 seconds. The user confirmed the dialog closed by itself without reopening recognition or changing controls. This is live user acceptance of timeout behavior; elapsed time was not instrumented. Cancellation within the microphone prompt remains pending.

## Quiet microphone-cancellation change accepted

Diagnostic Back-before-speaking run captured empty RecognitionError and RecognitionMessage. Native Get Voice did not expose a distinct cancellation error. Removed temporary diagnostic globals from the task and added an empty-result guard: when both VOICE and gv_heard1 are unset, clear CommandOK, VoiceReply, and Error, record Voice capture ended without speech; no command sent, and stop before speech/retry/routing. Empty silence/timeout results also end quietly; unsupported recognized text retains explicit retry. Retry/parity/generation checks and static validation passed (82 XML files, 5,389 actions). Imported the updated task and opened a fresh microphone prompt. The user used Back before speaking and confirmed quiet termination without not-recognized speech, a retry dialog, or a control change. Live quiet microphone cancellation passed.

## Spoken DND instruction accepted

Read phone zen_mode=1 before the test. The first launcher attempt failed with Missing Tasker tab: TASKS while the phone remained in the voice-task editor; it did not start recognition and is not a DND-command result. Pressed Play directly in the confirmed SIG3_VoiceCommand editor for a fresh **check battery** attempt. The user confirmed the command stopped and the DND instruction was audible. Spoken DND blocking passed user acceptance. No phone DND or volume setting was changed by the test helper. User restoration of DND is pending.

## Spoken low-phone-volume instruction accepted

After the user turned DND off, read zen_mode=0 and active media speaker volume **6/25**, unmuted. Saved that read-only audio dump to `/tmp/HeySig3-low-phone-volume-start-20261011.json`. The volume was already below the configured minimum **13/25**; the helper did not lower it. Opened the general voice prompt for **check battery**. The user confirmed it stopped and the instruction to raise phone volume was audible. Spoken low-volume blocking passed user acceptance. Asked the user to raise media volume to at least 13/25 before subsequent control commands; restoration is not yet verified.

## Restored audio settings and shortened warnings

After user restoration, read zen_mode=0 and active speaker media **17/25**, unmuted. Shortened spoken DND wording to Turn off Do Not Disturb and the 50%-minimum volume instruction to Raise phone media volume to halfway. Other configured minimums do not receive a misleading halfway instruction; the selected stream is retained, and full errors remain unchanged on screen. Added actual formatter cases for 50% media and non-50% ring settings. Shortened wording needs separate live hearing acceptance.

## Manual Stop control located; follow-up voice command accepted

Imported temporary phone-only **SIG3_TestStopOnly**, containing only a 60-second Wait, to locate Stop safely. Screenshot while running showed a square at the bottom-left where Play normally appears; accessibility still described this as Play. The user was instructed to tap the square and subsequently confirmed a triangle was shown. That confirms the task was no longer running, but elapsed timing was not captured and natural completion cannot be excluded. This does not establish interruption of SIG3_Worker or stale-result handling. Opened a subsequent current-program voice query for recovery acceptance. The temporary task is not part of repository exports.

The user subsequently reported the voice-task editor was visible. The launcher had reported Ran, but that alone did not establish an active recognition prompt. Pressed Play directly in the confirmed voice editor; the user said current program and confirmed both display and speech worked. A normal voice command worked after the wait-only task ended. Manual interruption of SIG3_Worker and stale-result behavior remain pending; this limited recovery test does not certify them.

Subsequent controlled testing of the actual worker's pre-dispatch pause passed Stop, cleared authoritative success, and fresh read-only recovery; see [worker Stop verification](https://github.com/5310H/HeySig/blob/HeySid3/docs/HeySig3-worker-stop-verification-2026-10-11.md). This later test does not certify interruption during plugin/internal-helper execution or a spoken recovery reply.

## Numeric event retest after tab fixes

AutoVoice's built-in typed `volume 8` event reached HeySig3 and changed Universal Volume 9→8. Fresh results were CommandOK=1, Observed=8, Error empty and OperationId=1791762906071. Independent Signia readback showed Universal, Selected Volume Tab and displayed 8. This verifies numeric AutoVoice event routing; it does not establish microphone capture.

Launching the installed `com.joaomgcd.autovoice.ACTION_FROM_SHORTCUT` recognition activity again returned to AutoVoice Testing without a microphone prompt. Live AutoVoice microphone recognition remains unchecked. The working SIG3_VoiceCommand prompt separately recognized spoken `volume 9`, restored Universal Volume 8→9, and spoke 9 as confirmed by the user. Independent screen readback showed Universal, Selected Volume Tab and 9. Fresh results: CommandOK=1, Observed=9, Error empty, OperationId=1791763065073. The original AutoVoice typed-test setting `current program` was restored without sending another event. The user reports no watch available for live AutoWear testing.

## Readiness cue acceptance

Added “Sig here” before the general Get Voice action, including its Try again loop. Speech waits for completion, then capture starts after a settling delay. Nine executable preflight cases cover speech preference, media volume, audio mode, DND, lock, screen state, microphone permission, and a busy worker. The existing 18 spoken-result cases and voice retry simulations pass.

The user initially heard only the cue. Diagnostic recognition results nevertheless contained “set volume to 10”; the full task reported the existing Do Not Disturb guard blocking that command. A temporary microphone-only task then showed its prompt. The same diagnostic with the readiness cue added showed both the cue and microphone prompt, with no recognition error. Finally, with Do Not Disturb off, the user confirmed the full general voice task spoke the program name after “Sig here” and the microphone prompt. These checks establish the cue and native capture handoff. Wake-phrase activation is still being implemented; Continuous has not been started during this setup.
