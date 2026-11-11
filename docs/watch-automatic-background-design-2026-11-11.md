# Watch microphone Automatic service review — November 11, 2026

The owner explicitly chose watch-microphone decisions, rather than a watch remote
for phone-microphone analysis. This review covers the new watch capture session
and the phone's selected-watch receiver. Earlier six-scene foreground monitoring
and its acceptance gates are unchanged. Live enablement is not accuracy or
physical aid acceptance; no new acceptance/enrollment lock is introduced.

## User action and platform scope

The phone's Noise → Choose programs offers an explicit watch-microphone choice,
separate disclosure and On. Its existing microphone/mediaPlayback service starts
with mediaPlayback only in watch mode; it does not open the phone microphone.
The watch must already be selected/enabled through Watch connection. The owner
then opens Watch Automatic while the watch is visible/unlocked, reviews its
microphone/data disclosure, grants microphone/notification access if needed and
taps On. Remote messages never cold-start a microphone service. Existing runtime
permissions are reused; a new non-exported microphone foreground service is added
on Wear. Neither target SDK, application ID nor signing identity changes.

The watch service has an immediate persistent notification with Off, a bounded
partial wake lock, an eight-hour maximum and START_NOT_STICKY. Notification
registration has a five-second startup allowance; no capture is allowed before
registration. Removed permission, disabled/removed notification, Off, task stop,
process loss or expiry ends the session. There is no boot/alarm/sticky restart,
persistent On, microphone auto-start or replay. Screen-off/lock capture is allowed
only after this explicit foreground start, unlike the older foreground monitor.

Official Android references checked November 11:
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/training/wearables/data/client-types

These requirements support explicit microphone-service startup from a visible
Activity. The phone remains an explicitly started foreground playback controller;
its intermittent hearing-aid transport and service use still need release-specific
Play review. Documentation and successful builds do not establish store approval.

## Analysis, coordination and privacy

Both APKs use the same hash-pinned HSGMASK1 head, YAMNet encoder, RNNoise binaries
and retained/residual bands. Shared Android adapter/assets live in audioAndroid;
the com.heysig.app.NeuralSeparation package is retained for pinned JNI-symbol
compatibility. The numerical policy remains platform-independent Kotlin.
The watch captures three seconds, analyzes locally and erases PCM/intermediate
waveforms. No enrollment, stored profile, recording, transcript, audio streaming,
analytics or cleaned-audio playback is added. Wrist microphone behavior and
battery use are not qualified by the existing phone corpus.

Only compact masking/type/score/relative level, sample age/sequence, session
controls and requested-program status travel through Wear OS MessageClient.
Google's encrypted Data Layer can route through Google servers. No persistent
DataItems, durable listener service, raw audio or automatic transport retry is
used. Source preferences/consent are saved only by explicit actions; session
identifiers, evidence and bounded replay/lease state remain in memory.

The phone advertises a dedicated capability only during an explicitly armed
watch-input session. Only its selected watch node can access the receiver.
Each sample needs a fresh five-second one-use grant, the exact in-memory session,
matching model profile, strictly increasing sequence and bounded relative age.
Phone time minus a conservative age bound avoids comparing device monotonic clocks.
The bound adds phone grant-to-receive elapsed time to reported age, so transit
cannot make old evidence look fresh. Old-session packets leave a newly armed
session unchanged.
Wrong/stale/invalid evidence cannot queue a command. Missing requests/replies stop
without retry. Initial arming expires if the watch does not start; established
sessions use bounded contact timeouts. Off on either side closes authority.

Only one source can own normal Automatic. Phone mode and watch mode share the
same MaskingState, confirmations, cooldown, uncertain/unassigned holds, fitting
checks and validated ManualRuntime executor. Phone manual/Therapy controls stop
automatic authority. Watch speech/monitoring starts stop Watch Automatic first.
Before each watch capture, the phone must freshly confirm capture is allowed;
playback and settling pause capture. Evidence arriving during phone priority is
discarded and requires fresh agreement afterward. Physical reception remains
separate from a successful playback callback.

## Validation and release follow-up

Run shared age/session/sequence/profile regressions, both APK builds/lint and
native inventory/alignment checks. Verify native fixtures on the watch, round
192dp layouts and font scales 1/2. Test actual watch microphone captures with
both screens off, no phone capture, paired message freshness, silent executor
program transitions, manual priority, Off on either notification and disconnect.
Record any startup failures and short CPU/battery observations honestly; do not
infer all-day battery, speech understanding, intended-talker identity or aid
reception. Preserve fitting, saved choices and pre-upgrade preferences.
See [the working checklist](watch-noise-completion-checklist.md).
