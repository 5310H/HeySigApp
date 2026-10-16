# What we know so far

October 14, 2026. These results describe the existing HeySig/Tasker setup on the Pixel 9a. Experimental Kotlin battery-audio prototypes now build for a Chromebook Linux desktop and Android; the full app remains unimplemented and must earn its own phone acceptance.

| Area | Existing evidence | Work for the app |
|---|---|---|
| Programs | Six configured names; controlled and measured automatic transitions passed fresh Signia readback | Own accessibility service, selectors and serialized executor |
| Sliders | 28 focused checks passed for occasional use; endpoints, repeats and restoration | Port gestures, geometry checks and no-op/readback behavior |
| Battery | Existing Signia battery checks work; user confirms audible indications | Own guarded battery workflow; direct request chirp remains unproven |
| Voice | Prompted commands, spoken results, retry/cancel and independent speech switches accepted | Replace AutoVoice with app speech services; prove background wake separately |
| Saved settings | Durable snapshots and restore acceptance; exact saved-record hash survives reboot | Versioned durable storage and migration preview |
| Sleep | Selectable times/programs and live foreground sleep/wake transitions accepted | Native scheduler and configured overnight/reboot acceptance |
| Noise | Quiet/normal/louder calibration, real switching, manual priority and bounded background checks accepted | Calibrate the app sampler again; preserve hysteresis, confirmation and cooldown |
| Reboot | Noise choices, speech preferences, off states and saved-record hash survived a full reboot | Verify the same behavior in the new app |
| Endurance/battery | Eight-hour observation trial started; final results not yet collected in this record | Collect existing evidence, then test app endurance and comparable idle battery use |
| Watch | Requested; actual watch/platform integration not accepted | Identify watch and validate commands before watch noise sampling |
| Direct chirps | Command definitions and checksum/control bits verified, 131,072 original-code comparisons | Resolve address/profile, full waveform and actual aid acceptance |
| iPhone | Shared logic is possible; a usable control path has not been demonstrated | Establish direct/official integration before promising feature parity |

The tested noise choices use an **uncalibrated phone index**, with boundaries −54.5 / −43, margin 1.5, three confirmations and 60-second cooldown. These values belong to the AutoVoice sampler and must not be assumed valid for a new audio implementation.

On October 15 the user clarified that program changes produce an audible beep count equal to the program slot (Sleep slot 2 means two beeps). This supersedes the earlier battery-only-feedback assumption. Program/slider UI readback alone remains evidence of Signia app state; user-confirmed program beep counts can provide separate reception evidence. Slider work should remain proportionate to occasional use.

Start with [the implementation plan](plan.md), [architecture boundaries](architecture/boundaries.md), [acceptance records](acceptance/), and [chirp research](../research/acoustic/README.md).

## Evidence provenance

Source: [5310H/HeySig, HeySid3](https://github.com/5310H/HeySig/tree/HeySid3). Milestones: `86842f7` static acoustic validation, `4b96088` measured noise acceptance, `311de13` full reboot persistence and timed-trial setup. Copied acceptance documents retain their historical scope; their claims apply to the old setup, not this app. Links to uncopied historical records point back to HeySig. No proprietary APKs or extracted assemblies were copied.

## Direct battery prototype progress

**October 16 direct slider acceptance:** user confirmed volume works. HeySig Sound Sharpness 0 → +4 → 0 and tinnitus 0 → 4 → 0 then passed live user change/restoration acceptance in Universal, using address 12. Volume 8 was sent as baseline. Six slider waveforms passed original-CIL checks and invalid ranges were rejected. See [balance/tinnitus acceptance](acceptance/direct-balance-tinnitus-2026-10-16.md) and [volume scope](acceptance/direct-volume-2026-10-16.md). This supersedes earlier pending direct-slider descriptions for the tested scope.

**Latest battery acceptance, October 15:** HeySig direct left and right battery requests both produced the user's usual aid indications using qualified address **12**, address-XOR enabled and gain divisor 1.2. Right's first attempt missed; Signia reference and the subsequent HeySig retry succeeded. Captured command/address sequences match; reliability beyond these tests is not established. See [battery acceptance](acceptance/direct-proto-2026-10-15.md). Earlier pending/failure descriptions below are historical.

**Latest October 15 result:** playback capture decoded qualified address **12** from Signia Sleep and TV, replacing the address-zero hypothesis for the tested program path. HeySig direct Sleep and TV then passed user acceptance (two/five beeps), with TV restored. Neither app queries aid state in this acoustic setup. Battery/slider direct reception remains separate. See [working direct-program capture](../research/acoustic/notes/decoded-program-capture-2026-10-15.md); earlier failed-test descriptions below are historical.

October 15 program extension: direct slots 1–6 are implemented and installed. Six address-zero/startup-profile program waveforms matched original-CIL PCM exactly. A static-AudioTrack initialization bug was fixed; completed Sleep slot-2 playback still gave no user-observed response. Matching Signia's streaming/legacy-media playback attributes also completed without aid response. Signia's own Sleep request produced two beeps and Sleep, and TV restoration produced five beeps, both user-confirmed. See [direct-program acceptance](acceptance/direct-program-prototype-2026-10-15.md). Program reception and active address remain unresolved.

The [Chromebook window](../desktopApp/README.md) and [Android debug prototype](../androidApp/README.md) share a standalone Kotlin PCM generator. Twenty-four waveform comparisons against restricted interpretation of original Signia encoder instructions matched PCM bytes exactly. [Evidence](../research/acoustic/fixtures/proto-waveform-validation.json) identifies the original methods and supplied profiles; this is not live capture or acoustic reception evidence.

The user confirms there is no pairing in their setup. Static basic-setup defaults support a manual address-zero test; an explicit preset is available. Real Chromebook/phone speaker playback and the user’s audible aid response remain pending. Neither prototype starts playback automatically or modifies the independent eight-hour Tasker observation trial.

The initial Chromebook test and gain-corrected retests produced no audible aid response, while the user confirmed Signia’s own left battery request works. The prototypes remain unaccepted on aids. Startup gain-divisor correction (4 to 1.2) is implemented, but it did not by itself resolve Chromebook transmission.

The prototype was subsequently installed and opened on the Pixel over the user’s wireless-debugging connection. Visible Signia settings show Media sound. Same-phone-speaker request testing is pending user feedback; installation alone is not aid acceptance.
