# HeySig development and release standards

The user requires Google Play readiness throughout development. Follow
[the dated release audit](docs/play-store-readiness.md) and recheck official
Google Play requirements before preparing a submission. An offline check or
successful APK build is not Play approval.

- Keep development APKs, ADB provisioning, relay secrets and debug signing
  separate from production artifacts. Do not publish prototype builds.
- Preserve existing user fitting/settings and acceptance evidence. Do not
  change hearing-aid settings merely to verify installation or UI behavior.
- Keep noise decisions, validation and parsing in platform-independent Kotlin.
  Android/Wear adapters own permissions, audio, storage and lifecycle. All aid
  playback must use the existing validated transport and one shared executor.
- Ask for runtime permissions only for a requested feature. Explain microphone
  purpose and data handling before access. Keep microphone capture bounded,
  cancellable and foreground-only until a separately reviewed background design
  is implemented. Never add transcript/audio collection or analytics silently.
- Keep prompts and responses independently optional; expose visible status when
  speech is off. Test accessibility, large fonts and round-watch layouts.
- Run relevant shared checks and APK builds for changes. Release validation
  additionally requires pinned Gradle tooling, Android lint, signed AAB
  validation, device tests and the applicable Play Console test track.
- Validate target SDK against the current official phone and Wear requirements;
  do not just increase the manifest number without testing behavior changes.
- Phone and non-standalone Wear releases must share the application ID and
  app-signing identity; use distinct form-factor version codes. Preserve the
  existing development IDs/signers when changing production packaging.
- Document actual data flows and third-party recognition/network behavior before
  writing privacy or Data safety claims. Do not claim everything stays on-device
  or that no data is collected without verifying the release configuration.
- Do not declare this hearing-aid controller a non-medical app by assumption.
  Record the medical-device assessment and health-app declaration before release.
- Keep six-level Automatic noise switching disabled until calibration,
  confirmation/cooldown, override/sleep priority and physical acceptance pass.
- Publishing, account declarations and regulatory assertions require real
  verified information; never invent owner identity, contact details,
  certifications, manufacturer affiliation or compatibility claims.
