# Production phone/watch connection

Both build variants use Wear OS Data Layer MessageClient 20.0.1. They do not
use the Linux relay, ADB forwarding, pairing files, shared relay tokens or raw
network sockets. Google Play services restricts communication to apps with the
same package name and signing identity. Both releases use `com.heysig.app` and
the same configured upload signer; production Play App Signing must also be
configured consistently across form factors.

Development phone and watch now share `com.heysig.batteryprototype` and the existing phone development signer. The phone keeps its fitting and preferences. The former watch-only `com.heysig.watchdev` relay package is retired. Release installation remains separate and does not copy or overwrite development fitting.

## Setup without developer tools

1. Pair the Wear OS watch with its Android phone using its normal companion app.
   Google Play services and the installed HeySig phone/watch release pair are
   required. Install both devices from the same variant.
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
   manual watch screen abandons its callbacks without replay. An explicitly
   started Shake voice foreground session continues until Stop or expiry. Reconnect explicitly after
   interruption. The Shake voice session reconnects to the selected phone per turn without a
   chooser and performs bounded recognition without opening another Activity.
   Returned words expire after 15 seconds, including grant time; invalid or
   cancelled recognition sends nothing;
   ambiguous commands and restoration retain the phone's existing confirmations.

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


## Shake voice session (October 26 update)

After selecting the phone once, use Speak or More → Shake to speak to start a
voice session. Its one-time explanation and Android microphone/notification
permission requests are setup steps. Routine use is **three deliberate shakes
→ one optional “Say a command” prompt → speak → displayed/spoken result**.
There is no per-command phone chooser, review dialog or notification tap.
Prompt and response audio follow the phone's independent Voice prompts and
Spoken responses switches, refreshed from authenticated replies. Separate watch
speech toggles no longer override them. Unknown/missing phone choices mean off.

The explicitly started microphone/motion foreground service remains armed while
the screen sleeps. It captures only after a shake or Speak, for at most ten
seconds, and waits for prompt playback to end first. Between commands the
microphone is idle. A private persistent notification provides Stop. Only the
requested turn holds a bounded partial wake lock. Stop, Recents removal, process
death or eight hours ends the session; there is no reboot restart. An active session works while PIN-locked; starting a new session requires unlocking. The phone must still expose its enabled foreground watch
connection or an explicitly started hands-free watch session. A shake does not
silently enable phone playback permissions.

Ordinary results/errors are visible without modal dialogs; meaningful phone
ambiguity, fitting and restore confirmations remain. Connection failure never
queues speech or retries a hearing-aid command. Repeated shakes during a turn
are ignored. The watch sends recognized text/alternatives and receives results;
all aid playback remains in the phone's existing serialized executor.

See [screen-asleep voice design review](watch-shake-voice-design.md) for lifecycle,
privacy and runtime acceptance. This extends the earlier foreground-only watch
recognition design; it is not a Play submission approval.

## Phone/watch-only builds — October 27, 2026

Both build types now compile the same direct connection and watch voice sources
from `src/direct/kotlin`. The desktop runtime, relay tooling, loopback listener,
pairing receiver and development-only unavailable screen are removed. Release
identity/signing remain unchanged. Debug phone keeps its package/signing/fitting;
debug watch now uses the matching phone package and signer. Its former standalone
watch development package cannot communicate through Data Layer and is retired.
Use HeySig Dev on both for development, or HeySig on both for release. Do not mix.
The old watch package and either phone package are not uninstalled or wiped.
Historical acceptance files remain records of earlier tests.

## Foreground restoration — phone 0.13 / watch 0.8

Phone Enable watch is remembered separately from watch selection. Returning to
the unlocked phone app restores that explicitly authorized foreground listener
without another dialog. Background/lock still immediately closes its gate and
listener. Disconnect persists Off; Forget removes the selection too. Existing
selections without the new explicit enable choice remain Off until enabled.
No commands are queued/replayed by restoration. A running hands-free service
retains its separate connection ownership. Watch 0.8 likewise obtains a fresh
hello on resume instead of treating a stored phone ID as readiness.
