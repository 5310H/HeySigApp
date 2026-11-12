# Phone Automatic completion checklist — November 12, 2026

Latest installed: **HeySig — Build b7dddd5 · Nov 12, 2026** (phone and watch).
The November 12 [follow-up](acceptance/phone-automatic-completion-2026-11-12.md)
tracks the new comparison workflow, native interruption checks and wearer reports.
The [final update record](acceptance/automatic-completion-2026-11-11.md) covers
the in-place upgrade, paired checks and preserved user preferences. Earlier
SDK 37 phone smoke and pinned watch fixtures remain version-specific evidence.

Owner scope: replace the earlier live spectral-suppression detector with the
neural speech/residual detector; finish the phone first. That phone-first scope
is retained here; subsequent watch inference has its own
[completion checklist](watch-noise-completion-checklist.md).
Checked items need evidence; unknown results remain open.
Live operation remains available on explicit On, without enrollment, calibration
or an acceptance lock. Completion of engineering is separate from demonstrated
listening benefit and Google Play readiness.

## Implementation

- [x] Route normal phone Automatic through baseline speech evidence plus aligned
  neural denoising and shared Kotlin agreement/band decisions.
- [x] Retire HSGMASK2 from the live path and phone APK assets; retain its original
  model under third_party/masking for reproducible historical comparisons.
- [x] Use Universal in the phone role labels; retain the persisted `everyday` key
  and existing assignments without migration or fitting changes.
- [x] Retain bounded three-second captures, in-memory processing, explicit On,
  notification Off, eight-hour limit, no automatic restart and no audio history.
- [x] Retain confirmation/cooldown, uncertain/unassigned holds, manual/Therapy
  priority, and the one validated executor. Do not infer aid reception from playback.
- [x] Pin denoiser libraries/model bands and include their source/license notices.
- [x] Complete shared noise shell regressions (all passed November 10).
- [x] Complete Gradle shared tests (37 passed on the latest candidate), phone/test APK builds and Android lint (zero errors).
- [x] Verify native APK inventory, alignment, active assets and absence of retired head (24 pinned libraries; ELF/ZIP >=16KB).
- [x] Run Android native fixtures, UI/choice preservation and silent lifecycle checks on the phone (screen-off/manual and foreground notification-Off runs).
- [x] Commit/push app updates before final APKs; initial neural fix 7b51fe2 and latest candidate b7dddd5 have separate versioned evidence.
- [x] Upgrade the physical phone in place and verify Build revision/date; initial five-file preservation and latest 13 phone/one watch user-preference preservation are recorded separately.

- [x] Fix and verify foreground notification startup: no microphone before notification registration; bounded startup wait; notification Off stops the session.

## Measured detector evidence

- [x] Preserve existing neural regression results and limitations in
  [the acceptance report](acceptance/phone-neural-noise-2026-11-10.md).
- [ ] Evaluate independent, previously unseen speakers/backgrounds with real TV,
  music, quiet, appliance, traffic and overlapping-conversation controls.
- [x] Run one new TV-only native control: five UNKNOWN holds, zero requests over 60 seconds (not broad accuracy acceptance).
- [ ] Reduce background-only false changes and TV mistaken for wanted speech.
- [ ] Improve moderate masking coverage without increasing wrong accepted changes.
- [ ] Validate sustained return to Universal in representative quiet environments.
- [ ] Measure complete transitions: response delay, false switches/hour, holds,
  repeated toggles and return delay; record denominators and actual conditions.
- [ ] Test phone on table, held, in pocket and in bag; record blocked-microphone behavior.
- [ ] Include wind, microphone rub, own voice and distant speech as distinct
  controls; evaluate missed useful changes as well as false changes and quiet return.

## Research-informed follow-up — November 11

Use the [manufacturer-source review](research/hearing-aid-concepts-2026-11-11.md)
as design input; it does not establish equivalent hardware capabilities or benefit.

- [x] Audit visible source, masking estimate, hold reason, confirmation/cooldown
  and proposed saved program together; use existing bounded samples only.
- [x] Implement an optional comparison of two saved daytime programs with immediate
  return, separate understanding/effort/comfort/awareness questions and memory-only
  feedback by default. No enrollment, adaptive wearer learning or enable lock.
- [x] Keep RNNoise retained/residual ratio and classifier confidence labelled as
  estimates, not ear-level target-speaker SNR or measured intelligibility.

## Listening and physical acceptance

- [ ] Obtain the audiologist's existing Universal/Noisy/Very Noisy configuration
  and intended use by email; no appointment or fitting changes requested.
- [ ] Verify saved role-to-program numbers against that configuration.
- [x] Record owner-confirmed Noisy → Universal reception and Universal restored (November 11).
- [x] Confirm Very Noisy reception and return to Universal; owner confirmed both on November 12.
- [x] Run a preliminary Universal/Noisy comparison with TV and a nearby speaker: understanding about the same; effort/comfort/awareness uncertain; Universal restored.
- [ ] Compare understanding, effort, comfort and environmental awareness in representative situations;
  record both benefits and worse outcomes. Do not label model SNR as intelligibility.
- [x] Measure an initial ten-minute screen-off native pilot: 43 samples, no requests, 18.64 seconds analysis CPU, battery/thermal endpoints recorded; settings preserved.
- [ ] Qualify longer representative battery/thermal endurance with appropriate baseline and workload; the short pilot is not all-day evidence.
- [x] Verify native phone notification Off and manual cancellation (Build b7dddd5; earlier evidence remains versioned).
- [x] Verify phone microphone permission removal and microphone privacy toggle with restoration (Build b7dddd5).
- [x] Verify notification-permission removal and restoration; expected Android process termination and capture stop confirmed.
- [x] Verify explicit process stop and reopening both apps without Automatic restart on final candidate.
- [ ] Verify actual phone calls and other audio interruptions on the physical phone (owner deferred real call check November 12).

## Release and later work

- [x] Refresh in-app/service data disclosures and include model notices and pinned dependency inventory.
- [ ] Complete remaining [Play readiness](play-store-readiness.md) requirements,
  signed AAB/release validation, physical tests and applicable Console track.
- [x] Separately scope and implement watch-local inference with phone execution;
  physical completion remains tracked in the [watch checklist](watch-noise-completion-checklist.md).

Do not mark physical/listening or release items complete based on host fixtures,
an emulator, command synthesis, a successful APK build or enabling Automatic.
