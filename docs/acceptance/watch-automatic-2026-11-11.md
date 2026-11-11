# Watch-microphone Automatic — November 11, 2026

Owner selected local watch-microphone analysis with the phone responsible for
hearing-aid commands. See the [background/data-flow review](../watch-automatic-background-design-2026-11-11.md),
[user setup](../watch-automatic.md), and [completion checklist](../watch-noise-completion-checklist.md).

## Implementation

The pinned phone detector/adapters/assets are shared from audioAndroid. Native
JNI package identity is retained, avoiding changes to the verified binaries.
Both APKs verify the same head, band, model, license and native pins. HSGMASK2
remains archived outside app assets. There is no new inference network call,
wearer enrollment, stored noise profile, recording or cleaned-audio streaming.

The phone's explicit watch-source session opens no phone microphone. It registers
an in-memory selected-node MessageClient endpoint and capability, bound to a fresh
session, fixed detector profile, one-use five-second grants and strict sample
sequences. A conservative capture-age bound includes grant transit. Old-session
messages cannot affect a new session. Current invalid/expired evidence stops
without replay. Source/role/fitting checks, manual/Therapy priority, confirmations,
cooldown and the shared validated executor remain on the phone.

The watch requires a visible/unlocked explicit start, feature disclosure and
microphone/notification permission. Its non-sticky foreground service captures
three-second windows only after the phone confirms capture is allowed and the
watch notification is registered. Voice/legacy monitoring stops it before taking
the microphone. Local Off cancels capture immediately; remote Off and lost contact
close authority with bounded network timeouts. No boot/sticky/reboot restart is
added. Existing six-scene qualification gates remain unchanged and false.

## Host/build checks

- Shared Gradle tests: 35 passed, zero failures/errors/skips.
- Full noise shell suite passed, including new selected node/session/profile,
  strict sequence, finite evidence, stale/negative age, conservative transit age,
  uncertain hold and closed-session regressions.
- The first build encountered a notice pin snapshot during an in-progress source
  update; pins were rechecked against the finalized shared assets before rerun.
- A callback-label compile error in the watch status callback was corrected.
  Both phone/watch main and device-test APKs subsequently built successfully.
- The successful local full build also completed lint with zero errors (phone
  32 warnings, watch 21). A final rerun was initiated after the selected-node
  guard and test wake-window changes; its completion was not retained across
  the session transition, so it is not counted as evidence.
- The owner requested moving compilation and automated checks off the slow
  Chromebook. The [GitHub workflow](../github-builds.md) now owns those checks;
  the current watch source must pass there before final device artifacts.
- Physical-device results remain pending. No new watch build has been installed.

## Evidence limits

The existing phone regression corpus is viewed and correlated. Moving identical
weights to a wrist microphone does not qualify watch accuracy, intended-talker
handling, quiet detection, battery endurance or speech understanding. Native
fixtures test consistency only. Controlled paired tests use an isolated phone
fitting and a transport that cannot emit hearing-aid tones. They never establish
actual hearing-aid reception. User fitting and programme roles must survive
upgrades; source choice is changed only explicitly. No Play submission or
regulatory/compatibility approval is asserted.
