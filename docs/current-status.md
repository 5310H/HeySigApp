# Current phone/watch checkpoint — November 11, 2026

Pixel 9a and Pixel Watch 3 have **HeySig — Build 4a25d01 · Nov 11, 2026**
installed. The owner requested dropping the Dev label and removing older copies.
Old phone/watch HeySig and watch-only legacy packages were removed with their
stored data retained. The current paired package/signing identity is unchanged;
this remains a development artifact. Exact installed APK hashes and HeySig labels
were verified, and all 13 phone and one watch user-preference files survived the
in-place update. See [the rename record](acceptance/app-name-cleanup-2026-11-11.md).

The preceding Build c53746e passed rapid restart/controlled transitions, real
watch capture with both screens off, both notification Off actions, manual
priority and recording-access-loss checks. Phone microphone activity remained
absent in watch mode. Phone smoke passed on SDK 37; reopening both apps without
On did not restart capture. See [behavioral evidence](acceptance/automatic-completion-2026-11-11.md).

The owner authorized a short real Noisy → Universal check using existing saved
per-program targets, heard both changes and confirmed Universal restored. This
is physical reception evidence for that sequence, not speech-benefit evidence.

Normal Automatic uses local speech evidence and RNNoise corroboration with saved
speech roles. Watch-source mode analyzes on the watch and leaves command execution
on the phone. Explicit On/Off, confirmation/cooldown, manual/Therapy priority and
the shared validated executor remain; calibration/enrollment/learning and an
acceptance lock are not required. Saved six-level assignments and the older
experimental six-scene gates remain separate and unchanged.

Remaining checks include independent scene/placement accuracy, audiologist
program configuration, wearer benefit and sustained battery/thermal behavior. The
[research review](research/hearing-aid-concepts-2026-11-11.md) adds clearer
source/uncertainty status and separate understanding, effort, comfort and awareness
evaluation to the [current checklist](remaining-checklist.md). No live decision
rules changed for the research. Full review of the two videos remains incomplete
because playback/captions were inaccessible. Listening benefit and release/Play
acceptance remain open.

# Historical phone/watch checkpoint — November 4, 2026

Phone and watch are updated in place, with installed APK bytes verified and all
26 phone preference files plus the watch preference file preserved before launch.
Noise-level Automatic uses saved assignments/thresholds through Automatic on/off;
calibration is optional. It learns bounded threshold suggestions from existing
valid foreground samples, with explicit approval required before saving changes.
Therapy wording retains existing settings and legacy sleep voice commands.
Build labels show only the Git revision; future updates push GitHub before final
build/installation so that revision identifies the pushed code.

All 33 shared Gradle tests, both debug APK builds/lint, native inventory/16 KB
alignment, and phone compiled-class checks passed. No microphone tests or aid
commands were used for this update. Android instrumentation was updated but not
run; physical aid/threshold suitability, accessibility/endurance and Play release
acceptance remain outstanding. The archived semantic classifier stays unqualified.

See [the user explanation](how-automatic-noise-works.md) and
[implementation, validation and device reports](acceptance/noise-threshold-learning-2026-11-04.md).
Earlier checkpoints below retain their historical scope.

# Historical phone/watch checkpoint — November 1, 2026

November 4, 2026 owner-directed update: ordinary level-based Automatic is now
available through Automatic on/off using saved thresholds and assignments;
calibration is optional and the acceptance lock/development trial are removed.
See [current behavior](noise-quick-start.md) and
[change/validation record](acceptance/automatic-noise-voice-2026-11-04.md).
Earlier lock/trial statements below are historical. The archived semantic
classifier remains unqualified; actual physical acceptance and Play release
requirements remain outstanding. Enabling Automatic is not acceptance evidence.

Both the Pixel 9a and Pixel Watch 3 have development build **2026.11.001**
installed (`yyyy.mm.###`), with Android version codes 16 and 13 respectively.
Both were updated in place, their installed APK bytes matched the final builds,
and their launcher activities returned `Status: ok`. All 25 phone preference
files and the one watch preference file were preserved unchanged.

Successful program/level results now use **Set to …** for visible text and
optional spoken replies. Battery-only results use **Battery request sent: …**.
These are command-completion reports, not confirmation of hearing-aid receipt.
Find the build on phone Home/Settings and watch More → App version.

The experimental semantic monitor includes the primary-preserving Small Group
fallback and watch sample-age/cancellation fixes. It preserves every non-null
primary estimate and only fills a complete primary abstention. Real automatic
dispatch remains blocked; neither semantic nor six-level automatic qualification
has been established.

Shared tests, both debug APK builds, native inventory/16 KB alignment checks
and the phone required-class check passed. The latest update did not exercise
microphone capture, live spoken responses or hearing-aid commands. Physical
monitoring/aid acceptance, independent classifier evaluation, endurance,
accessibility and release/Play validation remain outstanding.

See [wording/build and installation evidence](acceptance/wording-version-update-2026-11-01.md),
[monitor implementation and limits](acceptance/environment-primary-preserving-fallback-2026-11-01.md)
and [remaining work](remaining-checklist.md). No production release or Play
approval is claimed. The dated checkpoints below retain their historical scope.

# Noise automation checkpoint — October 28, 2026

Phone development version 0.15 is installed; watch remains 0.12. Added voice
calibration commands and a debug-only, owner-authorized uncalibrated automatic
trial, limited to 15 minutes with the phone open and unlocked. Production
automatic qualification remains locked. No calibration evidence was invented.

Live sampling, cooldown, unused-level decisions and a Stop/restart were observed.
The persisted last-requested program changed from TV (5) to Very Noisy (6)
during the trial; this does not establish physical hearing-aid reception. The
restoration snapshot digest was unchanged. Saved noise choices remain
[1,3,6,null,null,null], so levels 4–6 send nothing.

All 27 shared checks passed; debug lint had zero errors and 17 warnings. Final
phone and AndroidTest APK builds succeeded. The earlier 0.14 device smoke test
passed; the final 0.15 rerun failed on screen visibility and is not a pass.
Background monitoring, calibration and physical acceptance remain unfinished.
See [dated evidence](acceptance/noise-voice-calibration-2026-10-28.md) and
[noise instructions](noise-quick-start.md). This is not a production release.

# Historical phone/watch checkpoint — October 27, 2026

The active runtime is Android phone plus Wear OS watch. Both build types now
share direct Data Layer connection code; the desktop runtime and relay have been
removed. Phone development fitting and release identities remain unchanged.
Installed development versions: phone 0.13 and watch 0.12. Read-only status,
recognition results and TTS completion were observed. The owner reports the
flow works and prefers shaking after considering alternatives. The watch now
uses two 120 ms ready pulses and one completion pulse, with optional voice
following phone settings. Tapping the visible watch home background is also
implemented; no side-button activation was added. Full sleep/lock reliability,
endurance and perception of every cue remain acceptance work. See
[latest evidence](acceptance/phone-watch-only-2026-10-27.md),
[voice setup](hands-free-quick-start.md) and
[direct setup](production-watch-connection.md).

All 27 shared checks passed. Phone 0.13 and watch 0.10 debug/release APKs and
release lint passed; the final watch 0.12 debug APK built and was installed.
Release lint/APK/AAB validation was not repeated for the last watch gesture
edits. This checkpoint is not a production release or Play-readiness claim.

The following dated entries are historical evidence of the versions actually
tested, not current installation instructions.

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

The retired desktop window and [Android debug prototype](../androidApp/README.md) share a standalone Kotlin PCM generator. Twenty-four waveform comparisons against restricted interpretation of original Signia encoder instructions matched PCM bytes exactly. [Evidence](../research/acoustic/fixtures/proto-waveform-validation.json) identifies the original methods and supplied profiles; this is not live capture or acoustic reception evidence.

The user confirms there is no pairing in their setup. Static basic-setup defaults support a manual address-zero test; an explicit preset is available. Real Chromebook/phone speaker playback and the user’s audible aid response remain pending. Neither prototype starts playback automatically or modifies the independent eight-hour Tasker observation trial.

The initial Chromebook test and gain-corrected retests produced no audible aid response, while the user confirmed Signia’s own left battery request works. The prototypes remain unaccepted on aids. Startup gain-divisor correction (4 to 1.2) is implemented, but it did not by itself resolve Chromebook transmission.

The prototype was subsequently installed and opened on the Pixel over the user’s wireless-debugging connection. Visible Signia settings show Media sound. Same-phone-speaker request testing is pending user feedback; installation alone is not aid acceptance.
