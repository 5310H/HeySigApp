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

- Shared noise policy checks: all 14 shell checks passed locally, including the
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
- Phone/watch APK builds, lint and emulator execution use the existing
  [Phone and watch checks workflow](https://github.com/5310H/HeySigApp/actions/workflows/android-checks.yml).
  Its commit-specific result and artifacts are the build evidence; this source
  record does not assert an outcome before the workflow runs.
- Installation, real TV listening, quiet return, screen-off TV sequence and
  physical reception: not performed for this update. No benefit, detector
  acceptance, Play approval or all-day battery claim is made.
