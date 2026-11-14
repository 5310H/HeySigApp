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
fresh checks rather than the saved three. Clear-speech return from TV also uses two checks, as clarified below; other changes retain saved counts.
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
Older six-scene acceptance gates are unchanged. Installation evidence is recorded below; actual quiet-return reception remains pending.

## Owner clarification before installation

The owner clarified that return need not require quiet: “just not TV.” The current
explicit Watching TV context is not TV-source recognition. The committed fix does
not establish that TV has stopped while nearby people continue talking. The owner accepted return to Universal during clear/low-noise speech, including
during clear TV dialogue, and later re-entry to TV when speech becomes masked.
CLEAR now maps to Universal even in Watching TV; exit from the TV program uses
two matching CLEAR checks. MODERATE/HEAVY still map to TV with saved confirmation
counts. QUIET also uses two matching checks. Saved cooldown and priority guards
remain unchanged. This is a masking-based program choice, not TV recognition.

## Final build and phone installation

**Build f5ecd36 · Nov 14, 2026**, source
`f5ecd3671f852cd19a705e58328c718ecbd57e10`, was committed and pushed before
final APK production. [Cloud run 43643782627](https://github.com/5310H/HeySigApp/actions/runs/43643782627)
passed pinned shared checks, both app/test builds, Android lint, APK/native pins
and alignment checks, and API 36 silent emulator smoke checks (including TV UI
and watch font/layout checks). The explicit TV clear-speech round-trip test also
passed locally against the revised shared policy.

The phone was updated in place with the existing development signer. All 37
preference files, including 13 HeySig user preference files, matched before and
after installation; installed APK SHA-256 matched the signed artifact. See
`quiet-return-2026-11-14/phone-upgrade.json`. Automatic was already stopped. No
hearing-aid commands were sent to verify installation.

Watch APK is built and signed but installation awaits the charging watch and a
current debugging address. Last installed watch remains Build aef19f7 · Nov 13,
2026. Watch-source Automatic requires the matching quiet-v2 profile update; use
the phone microphone until both devices are updated. Real-room return timing,
physical reception and listening benefit remain unverified for this build.
These are development artifacts, not a production release or Play approval.

After launch, the owner enabled phone Automatic. A read-only UI snapshot showed
the revised Watching TV wording, Automatic On, one completed check and Universal
already selected. No return transition or physical reception is established by
that snapshot. Twelve of thirteen HeySig preference files still matched the
pre-install snapshot; `heysig-core-v1.xml` changed during post-launch/live use.
The all-37 match above is specifically the completed installation checkpoint,
not a claim that runtime state never changes afterward. The build label was not
visible on the Noise screen; exact installed source was verified by APK hash.
