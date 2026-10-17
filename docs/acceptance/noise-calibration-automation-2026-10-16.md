# Noise calibration and guarded automation — October 16, 2026

The existing six-level Observe feature now has an explicit, guided 18-sample
calibration workflow and automatic preview. Calibration, proof matching and
automatic decisions live in platform-independent Kotlin. Android owns capture,
storage, consent and lifecycle. The [quick start](../noise-quick-start.md)
describes the controls and actual scalar data retained.

Real automatic playback is source-locked by
`NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED = false`. Both the start and final
executor boundary enforce that lock. No preference can authorize it. Preview
simulates completions and cooldown without calling the executor or changing
requested/restoration records. Existing aid and noise preferences are preserved
until the user explicitly saves changes.

## Required acceptance before changing the lock

- Measure actual built-in microphone behavior on each intended phone; confirm
  range separation and repeatability in comfortable everyday environments.
- Review six mappings and protected Sleep selection; exercise repeated mappings,
  unused levels, direct jumps and thresholds/hysteresis in both directions.
- Confirm cooldown after successful playback and fresh confirmations afterward;
  no repeat for the same last-requested target; no physical-aid reception claims
  from playback completion alone.
- Confirm manual requests, Stop, Sleep, speech, pending confirmations, audio
  contention, locking, permission loss, navigation, pause and process death have
  priority and cannot trigger replay or automatic restart.
- On a separately reviewed acceptance build, verify physical aid reception,
  interrupted/failed playback, storage failure, settling after command tones and
  no overwrite of the user's manually saved restoration record. Record evidence
  before modifying the production gate.
- Test live phone/watch speech and microphone interactions, TalkBack and real
  round-watch controls. Automatic preview runs on the phone sampler only.

No physical calibration or aid command is part of ordinary build/UI validation.
No background capture, medical assessment or Play approval is established here.

## Validation results

- Shared Gradle regression suite: five tests, zero failures/errors. This includes
  existing manual/voice and measurement checks plus calibration construction,
  overlapping ranges, source/capture/threshold invalidation, all readiness
  guards, Sleep in/out, unknown baseline, confirmations, cooldown boundaries,
  clock rollback, no-op targets, rejected/stale samples, failure and stale tokens.
- [Standalone noise checks](evidence/noise-calibration-shared-2026-10-16.txt) passed.
- [Development relay regression](evidence/noise-calibration-relay-2026-10-16.txt):
  six tests passed; status-only automatic playback remains blocked.
- An intermediate build caught a source-snapshot mismatch while the delivery-age
  helper was being added; the finalized source was rebuilt consistently. This
  was a compile failure, not a passing device or release check.

- Chromebook Android 13/API 33, 412dp app window: normal and 2× text device
  runs passed, including new calibration/preview/Sleep controls and locked
  Automatic, isolated version-1 settings migration, corrupt proof rejection,
  native navigation/system Back and privacy regression checks.
  [Normal report](evidence/noise-calibration-chromebook-2026-10-16.txt),
  [large-font report](evidence/noise-calibration-chromebook-large-font-2026-10-16.txt).
- [Updated microphone explanation](evidence/noise-calibration-disclosure-2026-10-16.txt)
  appeared after requesting Sample. Not now dismissed it; Continue was never
  selected, and no microphone permission or sample was initiated. The first QA
  probe recognized the explanation but missed Android's uppercase Not now label;
  a case-insensitive probe completed dismissal successfully. This was a QA probe
  correction, not a bypass of consent.
- Real fitting/noise preference digests matched before/after device runs;
  temporary font scale was restored to 1.0. Native previews were reviewed at both
  text sizes and remain under ignored `private/noise-preview/`, outside Git.
- Phone and Wear debug APKs passed signature verification against their existing
  development identities, and their familiar local APK aliases were refreshed.

- Full build passed in 13m 34s (231 tasks: 60 executed, 171 up-to-date),
  including phone/Wear debug and release APKs, signed bundles and full release
  lint. Lint reported zero errors, 23 phone warnings and 9 Wear warnings;
  localization/resource/icon warnings are not suppressed.
- Final review restricted “Would select / No command sent” to Observe/Preview,
  and made automatic waiting/status text distinguish real requests. Permission
  retry text now also covers calibration. The final artifact rebuild and repeat
  device check cover these status refinements. Final review also added per-capture
  draft validation and stops future automatic activity for authenticated watch
  manual requests before Busy rejection, without queuing or replaying them.

- [Final development APK device run](evidence/noise-calibration-chromebook-final-2026-10-16.txt)
  passed after the status and guard refinements. The final APK is installed on
  the selected Chromebook and its native Noise view was reviewed. The earlier
  2× run covers the same unchanged layout; final changes concern guarded
  requests, validation and status wording, not layout dimensions.
- [Settings preservation check](evidence/noise-calibration-settings-preserved-2026-10-16.txt)
  matched the original fitting/noise preference digests and font scale after
  the final installation. No user calibration samples or aid commands were used.

- [Final signed bundle validation](evidence/noise-calibration-bundles-2026-10-16.txt)
  passed for both candidates: shared production package, distinct version codes,
  targets 36/35, expected upload signer, no development transport/native libraries,
  backup exclusions and foreground companion packaging. Artifact hashes are in
  the report. The built-manifest preflight also passed; this is not Play approval.

The final consistent-source build passed in 8m 17s (189 tasks: 25 executed,
164 up-to-date). It reran the changed phone builds/lint and reused unchanged
shared/Wear checks. Final lint remains zero errors, 23 phone warnings and 9 Wear
warnings. Whitespace and credential-pattern checks passed. Source and candidate
artifacts remain subject to the physical acceptance matrix above; Automatic was
not enabled, and nothing was uploaded to Play.
