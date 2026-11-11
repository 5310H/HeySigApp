# Phone Automatic completion checklist — November 10, 2026

Owner scope: replace the earlier live spectral-suppression detector with the
neural speech/residual detector; finish the phone first. Watch inference is
outside this update. Checked items need evidence; unknown results remain open.
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
- [x] Complete Gradle shared tests (35 passed), phone/test APK builds and Android lint (zero errors).
- [x] Verify native APK inventory, alignment, active assets and absence of retired head (24 pinned libraries; ELF/ZIP >=16KB).
- [ ] Run Android native fixtures, UI/choice preservation and silent lifecycle checks.
- [ ] Commit and push only this update; build final device APK from that pushed commit.
- [ ] Upgrade the physical phone in place; verify Build revision/date and unchanged preferences.

## Measured detector evidence

- [x] Preserve existing neural regression results and limitations in
  [the acceptance report](acceptance/phone-neural-noise-2026-11-10.md).
- [ ] Evaluate independent, previously unseen speakers/backgrounds with real TV,
  music, quiet, appliance, traffic and overlapping-conversation controls.
- [ ] Reduce background-only false changes and TV mistaken for wanted speech.
- [ ] Improve moderate masking coverage without increasing wrong accepted changes.
- [ ] Validate sustained return to Universal in representative quiet environments.
- [ ] Measure complete transitions: response delay, false switches/hour, holds,
  repeated toggles and return delay; record denominators and actual conditions.
- [ ] Test phone on table, held, in pocket and in bag; record blocked-microphone behavior.

## Listening and physical acceptance

- [ ] Obtain the audiologist's existing Universal/Noisy/Very Noisy configuration
  and intended use by email; no appointment or fitting changes requested.
- [ ] Verify saved role-to-program numbers against that configuration.
- [ ] Record actual aid reception for each program command with wearer confirmation.
- [ ] Compare understanding, effort and comfort in representative situations;
  record both benefits and worse outcomes. Do not label model SNR as intelligibility.
- [ ] Measure sustained screen-off operation, CPU/battery use and thermal impact.
- [ ] Verify phone calls/audio interruptions, permission removal, microphone privacy
  toggle, notification Off and process/task stop on the physical phone.

## Release and later work

- [x] Refresh in-app/service data disclosures and include model notices and pinned dependency inventory.
- [ ] Complete remaining [Play readiness](play-store-readiness.md) requirements,
  signed AAB/release validation, physical tests and applicable Console track.
- [ ] Later: separately scope watch control versus standalone watch inference.

Do not mark physical/listening or release items complete based on host fixtures,
an emulator, command synthesis, a successful APK build or enabling Automatic.
