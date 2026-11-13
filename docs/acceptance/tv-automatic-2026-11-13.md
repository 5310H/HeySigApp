# Watching TV Automatic update — November 13, 2026

Owner authorization: add TV to Automatic, confirmed existing TV slot 5. Implemented
as the explicit Watching TV listening context described before authorization,
not inferred TV detection. No fitting, saved noise thresholds, legacy six
assignments, model assets or wearer preferences were edited by this development
work. The app does not hardcode slot 5 for other fittings.

Behavior: choose an existing TV program, enable Watching TV and explicitly start
Automatic. CLEAR/MODERATE/HEAVY evidence targets TV; QUIET targets the saved
Universal role; UNKNOWN holds. This uses the existing fresh repeated checks,
cooldown, pending-token and shared validated-executor path. Noisy and Very Noisy
roles are retained for normal listening. Switching Watching TV off restores their
normal use. Music-only/uncertain audio can delay TV entry. Quiet return does not
clear Watching TV: later speech can select TV again until the owner disables the
context. This limitation is disclosed before starting and in the user guide.

This prevents Noisy/Very Noisy selection **within the explicit TV context**; it
does not establish that normal Automatic's reported early TV switch is fixed.
No aid commands were issued during implementation. Existing physical acceptance
records remain unchanged; this new TV path has no physical acceptance yet.

## Validation record

- Shared noise policy checks: the complete shell suite passed locally, including the
  added TV routing cases. Local compiler was Kotlin 2.4.21; the GitHub workflow
  separately pins Kotlin 2.2.10 and the project Gradle tooling.
- Test additions cover normal-role preservation, TV entry from each speech
  severity, unknown hold, duplicate suppression, quiet return, cooldown,
  unassigned return hold, protected TV targets, manual/audio priority and invalid
  TV assignments. Existing freshness/failure/confirmation tests run unchanged.
- Android detached-UI smoke check uses isolated test preferences: legacy schema
  loading, explicit TV assignment, unassigned rejection, slot-5 persistence,
  Therapy exclusion, normal-mode restoration and fitting-signature mismatch.
  It does not request microphone access or send commands.
- Phone/watch APK and test-package builds, lint, native validation and emulator
  execution passed in the [commit-specific workflow](https://github.com/5310H/HeySigApp/actions/runs/43515236291).
  Its artifacts identify the pushed source revision and original APK hashes.
- Real TV listening, quiet return, screen-off TV sequence and physical TV-program
  reception: not performed for this update. No benefit, detector acceptance,
  Play approval or all-day battery claim is made.

## Physical-device installation

[Build 747a1fe cloud checks](https://github.com/5310H/HeySigApp/actions/runs/43515236291)
passed shared tests, phone/watch APK and test-package builds, lint, native pins
and alignment, and API 36 emulator smoke. The new isolated TV preference/UI
check passed, including legacy choice preservation and TV slot 5 persistence.

The owner requested installation. Downloaded artifacts were checked against
the pushed source revision and original APK hashes, re-signed with the existing
development identity, and compared with each installed application's signer
before in-place updates. Phone and watch main/test packages installed successfully;
both installed main APK hashes match the verified signed artifacts. Phone: 37
preference files (13 user files) unchanged through installation; watch: its one
preference file unchanged. Both devices' preferences also matched after launch/navigation.

The phone visibly showed **Build 747a1fe · Nov 13, 2026**, Universal as the last
requested program, Automatic Off, the new TV selector and Watching TV Off. No
choices were saved or enabled, no microphone session started, and no hearing-aid
command was sent for these installation checks. TV remains unassigned until the
owner selects the existing program 5 and explicitly enables the context.

Wear OS returned no usable UI hierarchy after launch, so a watch on-screen build
label/smoke pass is not asserted; its installed-byte/hash verification passed.
This is installation evidence, not physical acceptance of TV switching or benefit.
Scalar installation records are in [the evidence directory](tv-automatic-2026-11-13).
