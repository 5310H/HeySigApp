# Automatic noise service design and safety review — November 8, 2026

Owner requested quiet return, usable On/Off controls, a noise graph, and operation
without leaving the app open. This is the separately reviewed background design
for ordinary level-based phone Automatic. Preview, calibration, semantic monitoring
and voice capture retain their existing foreground constraints. This engineering
review does not establish physical aid reception or Google Play approval.

## Platform and lifecycle review

Reviewed Android's current [microphone service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types)
and [while-in-use startup restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
Automatic is explicitly started from the resumed/unlocked Activity after microphone
disclosure and permission. A second disclosure explains screen-off capture, real
program tones, battery impact and the eight-hour limit. Notifications must be
allowed, including the channel. The existing microphone and media-playback foreground
permissions/types cover sampling and tone delivery. No target SDK change is made.
The service owns its notification, bounded wake lock and sampling, and never holds
an Activity. Android 17 background audio restrictions additionally require actual
screen-off device verification; declaring a service type alone is not acceptance.

No boot receiver, persistent enable bit, alarm restart, sticky service restart or
replay is introduced. Off invalidates pending startup and closes the sampler;
in-flight tone cancellation uses the shared executor's existing cancellation flag.
Already emitted tones cannot be undone. Task removal, process loss, permission
loss, disabled notifications, fitting/settings changes, manual override, Therapy,
terminal capture error, uncertain playback, or eight-hour expiry ends the session.
Temporary audio-priority pauses discard incomplete captures and retry; they do not
silently turn Automatic off. Screen lock/Activity pause alone no longer stops an
explicitly authorised Automatic session. Calibration and preview still stop.

## Decision and transport review

The shared Kotlin reducer has an explicit microphone-session safety input, default
false. Only the active service asserts it. It permits locked-screen/background
measurement while retaining audio priority, manual override, Therapy protection,
sample age, assignments, confirmation count, cooldown, pending command tokens and
failure halt. Service dispatch uses ManualRuntime.execute with automatic=true,
its original validation, per-program restoration, journaling and transport. No
parallel executor or independent tone implementation is added. Manual execution
cancels pending or active noise sessions before attempting a command. Hands-free
On/Stop also cancels the service, including when no Activity is present.

The previous shared loud-to-quiet reducer regression already passed. The owner's
failure remains a real unaccepted physical report; no exact runtime trace was
available. The old adapter stops on Activity pause; cooldown/confirmation waits and
unassigned quiet levels were poorly explained. This change removes the lifecycle
failure and makes those states visible, without inventing a quiet assignment or
silently changing saved timing/thresholds. Quiet all-zero/muted/invalid samples
remain rejected rather than treating unavailable microphone data as silence.

## Data and UI review

Captures remain 1–10 seconds using the built-in microphone, interleaved with the
saved wait. PCM is reduced in memory and discarded; the service adds no raw audio,
transcript, analytics or network collection. Up to 120 scalar timestamps/levels
are kept in memory for the graph. Threshold learning still uses only samples
captured while the Activity is visible, stays bounded to 240 scalars, and requires
existing explicit review before saving. Existing fitting/settings and calibration
evidence are preserved. Speech recognition and companion-network disclosures are
separate; this is not a blanket on-device/no-collection claim for the whole app.

On is green and Off red, with text and accessible action descriptions so colour
is not the only signal. One button toggles; status shows last requested program,
measured relative dBFS, candidate, confirmations, cooldown and session time.
The graph has scalable text, a spoken range/latest-value alternative and dotted
saved threshold lines; gaps represent missed measurements, not interpolated audio.
Physical hearing-aid acceptance, screen-off battery testing and foreground-service
Play declarations remain required before release.
