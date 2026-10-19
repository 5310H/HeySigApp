# Watch completion checks — October 18, 2026

Owner requested connection, fitting setup, voice, then sleep/shake checks in
that order. Source is the working tree based on 4d03bae; existing uncommitted
setup/import changes are retained.

## Findings and changes

Both the Pixel 9a and Pixel Watch 3 are reachable over their current authorized
debugging connections. Initial watch inspection showed the older development
relay app (`com.heysig.watchdev`), not the direct phone candidate
(`com.heysig.app`). Both installations are preserved. The watch repeatedly
returned to its lock/ambient screen during inspection; unlocking alone did not
establish a fresh connection or status result.

The direct watch candidate now holds its visible Activity screen on only while
an RPC reply is pending. Every RPC has its existing 30-second timeout. Reply,
send failure, oversized payload rejection, timeout and abandonment all clear
the hold when no RPC remains. Explicit navigation/locking still disconnects;
this adds no wake lock, background microphone, command retry or system timeout
change. Physical timeout/recovery acceptance is pending.

## Validation so far

- Shared regression task and both development APK build tasks passed using the
  pinned Gradle wrapper and original host cache (tasks were up to date).
- Development phone APK required-class DEX check passed. The development-only
  class-name check cannot validate R8-obfuscated release classes; the release
  mapping confirms `ManualRuntime` is renamed.
- Direct phone candidate updated in place successfully with the existing
  fitting-import review fix. No aid-control action was used to verify installation.
- Phone and watch development/release APK builds passed. Watch release lint
  passed with zero errors and two warnings. The direct watch candidate updated
  in place successfully. Phone native inventory/checksums and 16 KB ELF/ZIP
  alignment checks passed for both variants.
- Both APK signature verifications passed and their certificate SHA-256 digests
  match. The phone's local document-provider selection displayed the new fitting
  review dialog with Save/Cancel. Owner reviewed and authorized saving. Fresh
  Home inspection confirmed “Aid setup saved. Nothing sent.” and available
  controls. No command playback was used for setup verification.
- Temporary fitting-only file removed from phone Downloads after successful
  review/save. Private host export and original development installation remain
  preserved. Phone Watch connection shows Pixel Watch 3 enabled; a fresh watch
  Connect/status check has been requested from the owner because the watch
  repeatedly returned to keyguard during automated inspection.
- Owner then confirmed a fresh Connect → Pixel 9a → More → Read status reply
  appeared with the updated direct candidates and saved fitting. This confirms
  this status round trip, not aid reception or full recovery acceptance.
- Owner confirmed Speak → “status” → reviewed Send recognized the request and
  returned a status reply. This passes that foreground recognition/reconnect
  path; provider offline/network behavior and failure cases remain unverified.
- Owner enabled Spoken responses and repeated the voice status request: text
  returned, but no speech was heard. Spoken-response acceptance failed and is
  under investigation. Google TTS is installed; media is unmuted at 5/15 on the
  speaker. No volume or provider setting was changed during diagnosis. Existing
  code silently skipped unready TTS and ignored language/output errors. Both
  watch variants now add a Test reply action and visible initialization,
  language and output-error reporting. Both diagnostic variants built; shared
  checks and release lint passed, and the signed watch APK verified. Device
  update installed successfully in place; local Test reply acceptance is pending.
  Owner subsequently confirmed the exact diagnostic: “Speech engine unavailable.”
  This is an initialization failure; missing English data is not established. The watch's
  system TTS settings action resolved, but keyguard prevented inspection; no
  provider, language, volume or lock setting was changed.
- Owner reported cancellation or reconnect failed in the recovery sequence;
  the exact failed step/message is still requested. Fresh phone inspection found
  it awake/unlocked with Pixel Watch 3 still enabled. Watch inspection found its
  watch face, followed by keyguard/ambient on later inspections. Do not mark
  recovery passed or infer that phone locking caused this report.

Speech diagnostic watch APK SHA-256:
`d3546fb05e31d009690ae8fdea6cd7325c8b2208f8e4643b6093b39fdba3d30e`.

Connection/fitting candidate APK SHA-256 fingerprints (before the speech
diagnostic follow-up):

- Phone: `08e0f592de1699181c9c77aa62c87579eedf5f8f5bc16fbc026241f2aff8d0e7`.
- Watch: `e863f736a8eac4af8f065953f9ea31e94a9bc423bb8a898eee77d63f869e7a19`.

No aid reception, sleep/shake session, accessibility or endurance pass is claimed
here. Recognition success is limited to the owner-confirmed status request. Original
development data was not cleared. Automatic noise remains locked. These local
candidate checks do not establish Play approval or normal Play installation.
