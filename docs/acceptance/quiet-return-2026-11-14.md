# Quiet return correction — November 14, 2026

The owner reported that TV was off and the room had neither noise nor speech,
but Automatic remained in TV after many checks. The connected phone showed
UNKNOWN (including “No reliable speech evidence; keep current program”) after
1,249 checks, with no active cooldown indication. The existing rule required
Silence >= 0.6 and RMS <= -65 dBFS; low speech evidence alone never implied quiet.
This was a detector limitation, not evidence that the owner needed to wait longer.
See `tv-return-diagnosis-2026-11-14.json` for the read-only observation.

## Behavior

Preserve the original strict quiet path. Add a second, conservative path for
ordinary low-level room tone: valid full three-second capture, RMS between
-120 and -45 dBFS, peak <= -30 dBFS, no speech/media event >= .15 in any frame,
and no other tracked activity >= .35. These are digital levels, not calibrated
sound-pressure thresholds. Highly periodic sound also requires predominantly
low-frequency energy; this admits quiet room hum while vetoing the tested soft
music. The neural check must independently report no reliable speech, speech
activity <= .05, and both input/residual speech scores <= .15.

The owner then requested quicker switching: QUIET now requires **two** consecutive
fresh checks rather than the saved three. Other severities retain saved counts.
At the owner's current three-second capture and ten-second wait, this removes
approximately 13 seconds, plus variable inference time, from confirmation.
It is not an exact return-time guarantee: capture phase, inference and the saved
60-second cooldown after a successful change still apply. Speech/UNKNOWN resets
the count. Invalid, muted/all-zero, interrupted or stale captures do not become
quiet through a timeout. Manual/Therapy priority, protected programs, cooldown,
pending-command guards and the shared validated executor remain in force.

No fitting, program assignments, saved thresholds, model weights or permission
settings change. Watching TV remains explicit context; subsequent speech can
select TV again. The watch/phone detector profile is bumped to quiet-v2 so an old
peer cannot supply decisions under the new profile. Both devices need updating.

## Evidence and limits

`quiet-return-2026-11-14/summary.json` and `recorded-decisions.tsv` record 22
public/synthetic cases: four correlated indoor room-tone windows/gains qualified;
eight soft clean-speech, four conversation and two synthetic-music controls did
not. Four forest/dripping-water cases were held and treated as context controls,
not indoor silence ground truth. Sources, hashes, attribution and scalar fixture
outputs are in `shared/src/commonTest/fixtures/noise/quiet-return/`.

The frozen 1,182-case scalar regression changed only the quiet surrogate from
UNKNOWN to QUIET; none of its 1,152 speech/noise mixtures newly became QUIET.
These historical inputs contain averaged event scores; the new 22-case check uses
actual per-frame scores. Public recordings were already inspected during
engineering, not a blinded accuracy evaluation. This does not establish field
accuracy, improved intelligibility or the owner's physical acceptance.

All added processing uses the existing bounded capture. A transient low-frequency
energy scalar and neural corroboration are computed in memory; no wearer audio,
transcript or new history is saved. Neural work now also runs on qualifying quiet
candidates that previously returned UNKNOWN early. Battery cost is unqualified.
Older six-scene acceptance gates are unchanged. Device validation and actual
quiet-return reception remain pending until recorded after installation.
