# HeySig remaining work checklist

## Current checkpoint — November 13, 2026

This checkpoint and the current [phone](phone-noise-completion-checklist.md) and
[watch](watch-noise-completion-checklist.md) checklists take precedence over
historical sections below. Normal speech-aware Automatic is available on explicit
On with saved roles, without calibration, enrollment, learning or an acceptance
lock. Phone and watch use the pinned neural detector; watch-source Automatic
analyzes locally on the watch and sends compact evidence to the phone executor.
The older experimental six-scene qualification gates remain unchanged.

- [x] Implement phone neural speech/residual analysis and watch-source Automatic,
  preserving saved assignments, fitting, manual/Therapy priority and one executor.
- [x] Validate pushed app source with cloud shared checks, builds, lint,
  native/model/license pins and API 36 emulator smoke checks.
- [x] Update Pixel 9a and Pixel Watch 3 to **HeySig — Build aef19f7 · Nov 13, 2026**,
  preserving 13 phone and one watch user-preference files through upgrade and tests.
- [x] Correct stale speech-aware Automatic startup/detailed-status paths;
  record [Build aef19f7 validation and installation](acceptance/automatic-status-2026-11-13.md).
- [x] Add explicit Watching TV context, preserve normal roles, and verify TV
  preference persistence in the emulator; record the [installed TV update](acceptance/tv-automatic-2026-11-13.md).
- [ ] Confirm live TV-program reception and quiet return; normal-mode early
  switching with TV and wanted nearby speech remains unresolved.
- [x] Retain earlier phone SDK 37 silent smoke and six watch native-fixture
  evidence, plus 192dp round controls at font scales 1 and 2. These are earlier
  version-specific passes, not rerun physical checks for the TV update.
- [x] Record manufacturer-source research and distinguish scene selection from
  audio processing and demonstrated listening benefit.
- [ ] Complete review of the two supplied videos when playback/captions become
  accessible; verified titles and manufacturer research are not full video review.
- [x] Complete paired silent transitions, rapid restart, screen-off watch capture,
  phone-mic-idle, recording-access-loss and watch notification Off checks.
- [x] Confirm short manual physical reception: wearer heard Noisy → Universal
  and confirmed Universal restored using existing saved per-program settings.
- [x] Implement optional manual saved-program comparison with memory-only feedback.
- [x] Verify final-build native phone notification Off, privacy/permission loss and restoration, process stop, manual priority and no restart on reopening.
- [x] Confirm Very Noisy → Universal reception and run preliminary TV/nearby-person comparison; no demonstrated advantage.
- [x] Run one-minute TV-only native control: five holds, zero requests; retain its limited denominator.
- [x] Record initial ten-minute phone screen-off resource pilot: 43 samples, zero requests; longer endurance still open.
- [ ] Measure independent real-world accuracy, source placement, listening benefit
  and sustained battery/thermal behavior; preserve failures and denominators.
- [ ] Complete wider automatic field/listening acceptance and separate release/Play requirements.

Evidence: [November 12 phone completion](acceptance/phone-automatic-completion-2026-11-12.md),
[paired update and device checks](acceptance/automatic-completion-2026-11-11.md),
[watch implementation](acceptance/watch-automatic-2026-11-11.md) and
[research findings and limits](research/hearing-aid-concepts-2026-11-11.md).

## Next work in order

1. **Finish remaining interruption acceptance.** Controlled transitions, rapid
   restart, real screen-off capture, phone-mic-idle, recording-access loss and
   notification Off on both devices and manual priority passed on Build c53746e.
   Phone native notification/permission/privacy checks, process stop and reopen
   checks also passed on Build b7dddd5. Actual calls are owner-deferred. Actual
   radio outage, watch notification-permission loss and maximum session expiry
   remain open. Preserve failures and distinguish process loss
   from a real radio outage.
2. **Retain clear decision status.** Source, masking estimate, hold reason,
   proposed program and last request are now exposed. Continue checking their
   clarity during field trials; requests do not establish reception or benefit.
   No recording, transcript history or extra capture was added.
3. **Evaluate difficult scenes and placement.** Use independent speakers and
   backgrounds: TV, music, quiet, appliances, traffic, wind, own voice, overlapping
   talkers and distant speech. Compare table/held/pocket/bag phone placement and
   natural wrist positions, including rub/occlusion. Record false switches/hour,
   missed useful switches, holds, response/return delay and repeated toggles.
   Evaluate any policy adjustment offline before changing live decisions, including
   regressions for sustained quiet/clear return to Universal.
4. **Verify wearer benefit.** Confirm saved roles against the audiologist's
   existing program configuration and record actual reception separately. Offer
   an optional manual comparison of two saved daytime programs with an immediate
   return action; assess words understood, effort, comfort and environmental
   awareness separately. The optional comparison UI is implemented; the first
   trial found understanding about the same and other outcomes uncertain. Feedback
   stays memory-only and no saving feature is implemented. This is
   not an enrollment/learning prerequisite for Automatic.
5. **Finish endurance and release work.** Measure sustained phone/watch battery,
   thermal and interruption behavior, then complete the release checklist and
   dated readiness audit. Do not substitute short CPU observations or fixture
   consistency for field accuracy, intelligibility or all-day endurance.

Manufacturer ideas inform these tasks; they do not establish target-speaker
identity, ear-level SNR, head direction or a HeySig listening benefit. Sensor
fusion, speaker identification and cleaned-audio streaming remain separate design
work, not additions to this completion scope.

## Historical checkpoints

November 4, 2026 owner-directed update: ordinary level-based Automatic is now
available through Automatic on/off using saved thresholds and assignments;
calibration is optional and the acceptance lock/development trial are removed.
See [current behavior](noise-quick-start.md) and
[change/validation record](acceptance/automatic-noise-voice-2026-11-04.md).
Earlier lock/trial statements below are historical. The archived semantic
classifier remains unqualified; actual physical acceptance and Play release
requirements remain outstanding. Enabling Automatic is not acceptance evidence.

## Historical checkpoint — November 4, 2026

- [x] Implement Automatic on/off for saved noise-level program assignments, with
  optional calibration and existing runtime priorities.
- [x] Add bounded, memory-only learning and explicit review/approval of threshold
  suggestions; preserve assignments, fitting and timing settings.
- [x] Use Therapy wording/voice aliases and Git-only build displays.
- [x] Pass 33 shared Gradle tests, phone/watch debug builds and lint, native APK
  checks and phone compiled-class validation.
- [x] Update and launch both devices in place; verify installed bytes and preserve
  26 phone preference files and the watch preference file before launch.
- [ ] Run updated Android instrumentation, large-font/round-layout checks and
  real-user threshold/aid acceptance without treating build success as reception.
- [ ] Complete endurance, release/Play and the other dated acceptance requirements.

Evidence: [threshold learning and device update](acceptance/noise-threshold-learning-2026-11-04.md).

## Historical checkpoint — November 1, 2026

- [x] Update phone and watch in place to build **2026.11.001** (codes 16/13),
  verify installed bytes and launch, and preserve all existing preference files.
- [x] Use **Set to …** for successful program/level text and optional speech;
  expose the build on phone Home/Settings and watch More → App version.
- [x] Pass shared tests, both debug APK builds, native inventory/16 KB alignment
  and phone required-class checks for this update.
- [ ] Accept live speech with the new wording, physical watch monitoring,
  independent classifier accuracy, aid reception and endurance. No microphone
  capture or aid commands were used for the latest installation checks.
- [ ] Complete accessibility and production release/Play validation. Both
  automatic qualification gates remain disabled.

Evidence: [current update](acceptance/wording-version-update-2026-11-01.md),
[monitor scope](acceptance/environment-primary-preserving-fallback-2026-11-01.md)
and [current status](current-status.md). Earlier dated checklist entries below
remain historical evidence and do not override this checkpoint.

## Earlier checklist — October 18, 2026

Watch follow-up: [October 18 completion checks](acceptance/watch-completion-2026-10-18.md)
record the direct-candidate updates and bounded request screen safeguard.
Reviewed fitting import, direct status and spoken status input passed. Spoken
replies and recovery have reported failures; aid commands and sleep/shake remain
pending.

Updated October 18, 2026. Check an item only after its result is recorded with
build/commit, device/OS, date and an acceptance-record link. A build or preview
pass does not establish physical aid reception or Play approval.

Noise implementation and its automated/build checks are complete. Real
Automatic remains source-locked. Existing fitting, saved restoration settings
and historical acceptance evidence must be preserved throughout testing.

The compact phone/watch layout and ear launcher are documented in the
[October 17 acceptance record](acceptance/compact-ui-and-ear-icon-2026-10-17.md).
Physical-device and release gates below remain separate.

## Owner review checklist — October 18

Aid-state reporting is removed from the remaining feature scope. The current
speaker/acoustic transport sends commands and receives no electronic aid-state,
setting or receipt response. Audible program/battery indications can be heard by
the user; they are not electronic readback to HeySig. Keep command completion,
blocked/failed/cancelled results and remembered requested values clearly labelled.
Internal unknown-state guards remain necessary for relative commands and safe
recovery. This scope change does not remove existing app controls or saved data.

Completed implementation items (physical acceptance remains separate):

- [x] Retain volume, Balance and tinnitus targets per program.
- [x] Rename Sharpness to Balance and change phone blue accents to teal.
- [x] Add Home Refresh for current known configuration, including voice
  confirmation and independently optional prompts/responses.
- [x] Move Battery check to Settings and remove its Restore button.
- [x] Repair incomplete APK packaging and verify phone/Chromebook startup.

Evidence: [program settings](acceptance/program-settings-2026-10-18.md) and
[Refresh controls](acceptance/refresh-controls-2026-10-18.md).

Remaining implementation and acceptance:

- [x] Implement Balance up/down and tinnitus up/down from known baselines.
  Shared tests and phone/Wear builds passed; physical acceptance remains pending.
  See [relative controls evidence](acceptance/relative-balance-tinnitus-2026-10-18.md).
- [x] Implement guided fitting/setup voice drafts, program names/aliases, reviewed
  fitting import, Help and cancel/retry recovery for phone and forwarded watch input.
  See [guided setup evidence](acceptance/guided-setup-and-phrases-2026-10-18.md).
- [ ] Physically accept guided setup on phone/watch, including speech switches,
  document-provider interaction and disposable-fitting recovery.
- [ ] Complete noise calibration and physical acceptance before enabling real
  Automatic; use the detailed Noise checklist below.
- [ ] Validate implemented tap-to-switch sleep reminders on real devices,
  including Doze, notification permissions, reboot and clock changes.
- [ ] Accept full watch voice parity and production pairing without ADB/relay.
- [ ] Accept hands-free wake/locked-phone use, interruptions, shake and battery
  behavior. Sleeping-watch shake now has a separate vibration-and-tap session;
  physical gesture/battery acceptance remains pending.
- [x] Implement the owner-selected sleeping-watch vibration-and-tap session in
  More, with explicit Start/Stop, bounded in-memory authority and no background
  microphone. Shared checks, development builds and in-place watch update passed;
  owner confirmed the menu entry. See [implementation evidence](acceptance/sleeping-watch-shake-2026-10-18.md).
- [x] Implement explicit conversational phrase proposals and reviewed personal-phrase
  import/export, with confirmation and no replay.
  See [phrase evidence](acceptance/guided-setup-and-phrases-2026-10-18.md).
- [ ] Physically accept corrections/import/export and actual recognition providers.
- [ ] Complete recognition baseline measurements and controlled voice-improvement
  evaluation using the [trial plan](voice-evaluation.md); text examples require explicit opt-in.
- [ ] Complete endurance, accessibility, lifecycle/recovery and supported-device
  acceptance using the detailed everyday-use checklist below.
- [ ] Complete production packaging, verified privacy/health/rights/account work
  and applicable Play testing using the release checklist below.
- [x] Reconcile the written specifications with approved retained program targets,
  Balance/relative controls, Refresh, reviewed setup/phrases and tap-to-switch sleep reminders.
  Current-decision sections take precedence over the older architecture sketches.

Optional future work:

- [ ] Evaluate a local LLM and only integrate it after its separate validation gates.
- [ ] Establish a viable iPhone control path before promising an iPhone client.

October 18 development updates: Pixel, Chromebook and watch updated in place.
Pixel/Chromebook isolated workflow/archive/import/relative-snapshot checks passed;
protected preference hashes were unchanged. Watch startup and preference checks
passed. See [device update evidence](acceptance/device-updates-2026-10-18.md).
Physical acceptance and release items remain unchecked. October 18 follow-up:
sleeping-watch shake now offers a separately started vibration-and-tap session;
see the [session design](architecture/hands-free-session.md#october-18-sleeping-watch-extension).

October 18 direct watch setup: matching production-connection candidates are now
installed separately on Pixel/watch. Phone discovery, watch selection, explicit
enable, fresh watch connection and a status-only round trip passed. Candidate
fitting was subsequently reviewed and saved; lifecycle/physical acceptance remains
pending. Existing development settings were preserved. See
[direct setup evidence](acceptance/watch-production-setup-2026-10-18.md).

## 1. Historical level-based Noise acceptance matrix

Retain this matrix for the level-based path and its dated evidence. Calibration
is optional, and these older preview/gate instructions do not restrict current
speech-aware Automatic. Use the current phone/watch checklists above for the
normal neural path. Sleep in this historical matrix means Therapy; persisted
keys and legacy aliases remain compatible.

Use the [Noise quick start](noise-quick-start.md) and
[physical acceptance matrix](acceptance/noise-calibration-automation-2026-10-16.md).

- [ ] Review the six level-to-program assignments and protected Sleep program.
- [ ] Complete guided calibration on each intended sampling device: three valid
  captures for each of six comfortable everyday environments. Review, apply and
  explicitly save the measured thresholds; never substitute illustrative values.
- [ ] Verify calibration repeatability, rejected/overlapping levels, Cancel,
  Discard and invalidation after source/capture/margin/threshold changes.
- [ ] Run live Observe: check level boundaries, hysteresis in both directions,
  direct jumps, repeated program assignments and unused levels.
- [ ] Run live Automatic preview: check consecutive confirmations, cooldown,
  fresh confirmations afterward and suppression of repeated targets. Verify no
  aid commands or remembered aid-setting changes occur.
- [ ] Test microphone denial, revocation, privacy switch, unavailable/muted mic,
  audio contention, rejected captures and delayed/stale results.
- [ ] Verify Stop, manual requests/repeats, Sleep, speech and pending
  confirmations have priority; include a manual watch request during Busy.
- [ ] Test lock/unlock, leaving the activity, navigation, rotation/recreation,
  process death and restart. Record unfinished-draft behavior; fix unacceptable
  loss or recovery behavior. Sessions must never restart or replay silently.
- [ ] In a separately reviewed, explicitly authorized physical acceptance trial,
  verify aid reception, command-tone settling, cooldown, interrupted/failed
  playback and storage failure. Preserve the manually saved restoration record.
- [ ] Test live watch speech/replies near the phone sampler and simultaneous
  microphone/audio use. Confirm behavior matches the disclosures.
- [ ] Record the complete physical results and resolve failures before changing
  `NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED`. Review the gate, tests and user
  messages together, then rerun acceptance on the candidate build.

Noise acceptance evidence: _pending physical testing_. Latest completed
[automated/build/device checks](acceptance/noise-calibration-automation-2026-10-16.md)
remain separate from these unchecked items.

Optional hands-free and default-off shake controls are implemented and installed;
see the [October 17 record](acceptance/hands-free-2026-10-17.md). The physical
items below remain unchecked.

## 2. Phone/watch and everyday-use acceptance

Follow the [production companion matrix](production-watch-connection.md).
Use disposable test installations for destructive checks; do not erase the
user's fitting or change aid settings merely to test screens or installation.

- [ ] Pair a real phone/watch using the release connection without ADB or a
  developer relay; verify phone/watch selection, status, disconnect and Forget.
- [ ] Test disconnect/reconnect, unavailable phone, stale/duplicate requests,
  expired grants, result lookup, confirmations and Stop without command replay.
- [ ] Accept the full supported watch command set, including live noise edits
  and independent prompts/responses on/off combinations.
- [ ] Test actual recognition and TTS providers, offline/network behavior,
  cancellation, permission failures and speech-off visible status.
- [ ] Accept hands-free locked-phone wake accuracy/false triggers, Off/Pause/End,
  permission revocation, notification/tile controls, watch interaction and battery
  behavior, deliberate shake/accidental motion and haptic acknowledgement on
  both devices using the [hands-free design](architecture/hands-free-session.md).
- [ ] Review the full UI with TalkBack, large text and real round watches;
  verify scrolling, touch targets, Back/swipe-back, keyboard and state retention.
- [ ] Test clean installation and upgrades on disposable release installations,
  exact Android 16 and the supported phone/Wear device matrix.
- [ ] Test export, clear history/display, full local deletion and backup exclusion
  on disposable installations, including effects on the other device and copies.
- [ ] Complete supported aid-model/fitting reception and interruption/recovery
  acceptance beyond the existing focused prototype evidence.
- [ ] Run endurance and battery-impact trials; collect the outstanding historical
  eight-hour trial result separately, without treating it as new-app evidence.

Acceptance evidence: _pending links_.

## 3. Production packaging and Google Play release

These items track the [dated readiness audit](play-store-readiness.md); recheck
current official requirements before submission rather than treating this list
as a fresh policy determination.

- [ ] Reproduce the pinned build on a standard build host; resolve required
  quality, localization/resource and launch-icon work, then rerun release lint.
- [ ] Back up the upload key securely, document custody and complete Play App
  Signing setup. Verify phone/Wear production identity and version-code rules.
- [ ] Supply and verify publisher name, privacy contact and public HTTPS host.
- [ ] Verify final recognition/TTS/Google SDK data flows and retention, then
  finalize and publish the accurate policy and connect its in-app links.
- [ ] Complete the release-specific Data safety review/declaration from the
  [data inventory](data-safety-review.md), including scalar calibration storage.
- [ ] Record intended use, supported devices/countries and the medical-device
  assessment; obtain applicable evidence and complete the Health apps declaration.
- [ ] Complete dependency/license, protocol/asset distribution-rights and
  manufacturer-affiliation/compatibility wording reviews.
- [ ] Complete developer-account verification and applicable testing/production
  access requirements; verify the actual account's requirements in Play Console.
- [ ] Prepare verified store text, screenshots, content rating, app-access and
  other required declarations. Use no invented owner or compatibility claims.
- [ ] On the final candidate, rerun shared checks, phone/Wear APK builds, lint,
  signed AAB validation and manifest preflight; retain hashes and device evidence.
- [ ] Complete applicable Play test tracks and pre-launch reports; resolve findings.
- [ ] Obtain owner review of the concrete final submission before publishing.

Release evidence and owner-supplied facts: _pending links_. No store submission
or policy publication is marked complete by the local build checks.

## 4. Separate roadmap work

These are product-scope items, not additional Noise implementation blockers.
They require their own implementation and acceptance before being advertised.

- [x] Implement guided voice setup/configuration and reviewed personal phrases.
- [ ] Physically accept guided setup and remaining phone/watch parity.
- [ ] Complete physical acceptance of the implemented tap-to-switch sleep
  reminders, including overnight/reboot behavior. Automatic scheduled aid
  switching was replaced by the owner-approved reminder design.
- [ ] Establish a viable iPhone control path before committing to an iPhone client.
- [ ] Finish physical acceptance of the separately reviewed hands-free session.
  Background noise automation remains separate and source-locked.

## Evidence entry template

Copy this for a completed item:

- Item:
- Build/commit:
- Date and tester:
- Device, OS and relevant provider:
- Steps and expected result:
- Observed result and limitations:
- Evidence link (keep private fitting/audio/secrets out of Git):
- Follow-up issues:
