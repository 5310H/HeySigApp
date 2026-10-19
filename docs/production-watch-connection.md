# Production phone/watch connection

The release candidates use Wear OS Data Layer MessageClient 20.0.1. They do not
use the Linux relay, ADB forwarding, pairing files, shared relay tokens or raw
network sockets. Google Play services restricts communication to apps with the
same package name and signing identity. Both releases use `com.heysig.app` and
the same configured upload signer; production Play App Signing must also be
configured consistently across form factors.

The existing debug IDs/signers and optional development relay are preserved.
They do not participate in production Data Layer pairing. Release installation
is separate and does not copy or overwrite the development fitting.

## Setup without developer tools

1. Pair the Wear OS watch with its Android phone using its normal companion app.
   Google Play services and the installed HeySig phone/watch release pair are
   required. This is not a Chromebook or iPhone watch connection.
2. Open HeySig on the phone. Use **Watch connection → Find paired watches**.
   Select your watch and read the data/command disclosure. Enable it explicitly.
3. Keep the phone open and unlocked. On the watch open HeySig, choose **Connect
   phone** and select the enabled phone. A successful response says connected.
4. Use **Read status** first. It sends no hearing-aid tones. An unconfigured
   release phone correctly reports unknown requested settings.
5. Request a command by voice or typed input. The phone uses its existing parser,
   fitting validation, speaker guards and single manual execution path. The
   watch never generates or plays hearing-aid command tones.
6. **Stop** is a fresh request, not a retry. It preserves the interrupted
   operation's lookup identifier. **Check last result** retrieves only the
   retained reply; it never resends the original command.
7. Leaving the phone app disconnects and cancels playback/capture. Leaving the
   watch abandons pending callbacks without replay. Reconnect explicitly after
   interruption. Speech recognition is a deliberate temporary departure: only
   freshly returned words can reconnect for a user-reviewed Send action.

Candidate APKs for controlled tests are in each module's
`build/outputs/apk/release/`; these are not a published Play app or permission to
submit. Normal distribution must use the applicable Play test track once the
other release requirements are met.

## Protocol and safety

Only the selected paired node is accepted. A foreground listener advertises a
capability after registration; no background listener service or durable
DataItem is registered. Connection availability failure is visible. Each
command needs a fresh, random one-use grant from the phone. It expires after
five seconds on the phone's monotonic clock. Closing/reconnecting invalidates
old grants. Requests have UUID identifiers, bounded payload/text/alternatives,
correlated replies and a 30-second visible timeout. No transport retry exists.
Ambiguous recognition still passes through the existing shared parser rules.
Program/restore confirmations stay bound to the initiating node/conversation.
While the watch Activity is visible, a pending RPC keeps its screen on until
reply, failure, cancellation or the 30-second timeout. This does not override
explicit locking/navigation or change the system screen timeout.

The phone journals UNKNOWN before starting playback. A duplicate retained
identifier returns its recorded result without new playback. The shared gate
also rejects reused grants. Results are bounded to 64 and expire after 24
hours, with deletion on next open/access. Clearing history disconnects first,
so delayed packets cannot turn deletion into a replay. Missing results remain
UNKNOWN; neither a network acknowledgement nor completed playback proves aid
reception.

All messages can contain sensitive requested settings. Data Layer uses Google's
encrypted transport and may route through Google-owned servers. MessageClient
is used instead of persistent DataItems. See the [privacy inventory](data-safety-review.md).

## Acceptance matrix

| Check | Required evidence |
|---|---|
| Shared safety | Wrong node/token, expiry boundary, duplicate, reconnect and retention regression |
| Production identity | Matched package/signer and distinct version codes in signed artifacts |
| No-ADB setup | Real paired Android/Wear devices, normal app installation, select/enable/connect/status |
| Disconnect/recovery | Disable Bluetooth/network, pause/lock phone, pause watch; no automatic playback on return |
| Uncertain result | Drop reply after submission; Check last result reads only; Stop preserves original lookup |
| Confirmation | Program ambiguity and restore remain bound across recognition activity departures |
| Physical command | Explicit user-approved command and aid reception feedback; never send merely to test installation |
| Privacy | Decline recognition; speech toggles independently off; clear history retains fitting; isolated full deletion/export |
| Layout | Round watch, large fonts, touch targets and Back; runtime evidence still required |

Hardware rows remain pending until actual devices are available. A successful
build or unit test must not be substituted for these results.

Official references checked October 16, 2026:
[Data Layer architecture, security and cloud routing](https://developer.android.com/training/wearables/data/overview),
[MessageClient persistence/retry characteristics](https://developer.android.com/training/wearables/data/client-types).
