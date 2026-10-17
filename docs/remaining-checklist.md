# HeySig remaining work checklist

Updated October 17, 2026. Check an item only after its result is recorded with
build/commit, device/OS, date and an acceptance-record link. A build or preview
pass does not establish physical aid reception or Play approval.

Noise implementation and its automated/build checks are complete. Real
Automatic remains source-locked. Existing fitting, saved restoration settings
and historical acceptance evidence must be preserved throughout testing.

The compact phone/watch layout and ear launcher are documented in the
[October 17 acceptance record](acceptance/compact-ui-and-ear-icon-2026-10-17.md).
Physical-device and release gates below remain separate.

## 1. Noise — next priority

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

- [ ] Finish guided voice setup/configuration and remaining phone/watch parity.
- [ ] Implement and accept native sleep scheduling, including overnight/reboot
  behavior; keep this separate from Noise's protected Sleep-program priority.
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
