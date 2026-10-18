# Release data inventory and Data safety worksheet

October 18 setup/phrase update: guided fitting drafts are memory-only and expire
in five minutes. A changed saved fitting archives prior fitting/requested/snapshot/
per-program JSON locally (at most 20 archives; no silent overwrite), then clears
active assumptions. Archives are included in optional local app-data export and
full local deletion. Fitting import reads only verified-format addresses, labels
and capabilities into a reviewed draft; it never replays exported commands.

Explicit conversational phrase proposals still need a separate Remember action.
Optional separate phrase export contains active phrase text and meanings. Import
stages all validated mappings for review and explicit confirmation; unconfirmed
imports remain memory-only for at most five minutes. A chosen document provider
may upload selected/exported files and its copies are outside app deletion.
General app-data export still excludes phrases. No background text/audio
collection, recognition measurement logging or analytics was added. Final form
classification/provider verification remains pending at the owner's direction.

October 17 sleep-management update: the owner chose tap-to-switch reminders.
Local preferences now also retain routine mode, manual override, interrupted
transition state, reminder enable/due timestamps and last delivery outcome.
Inexact Android alarms post private local notifications and replan future times
after reboot/update or timezone/clock changes. They never access the microphone
or execute hearing-aid commands. Notifications open sleep settings; switching
requires an explicit button/voice request through the existing executor. See
[design and recovery](sleep-management-design.md). Export/deletion cover this
state. Physical notification/privacy/Doze/reboot acceptance remains pending.

October 16, 2026. This is implementation evidence for owner review, not a
submitted or approved Data safety form. Publisher identity/contact and final
provider behavior are still unverified. See [policy draft](privacy-policy-draft.md).

| Flow | Release implementation | Retention/control | Form review |
|---|---|---|---|
| Hearing-aid setup/settings | Private phone preferences; used to render validated tone requests | Until local deletion/uninstall; export optional | Assess health/device/settings categories from intended use; avoid classifying by permission alone |
| Noise microphone | Bounded foreground PCM to in-memory scalar statistics; no recording or audio uploader | PCM discarded after capture; optional 18 scalar calibration values and microphone/capture configuration saved locally with settings; Stop/onPause and Android permission controls | Distinguish local-only noise processing from off-device speech |
| Recognition audio | Delegated ACTION_RECOGNIZE_SPEECH on either device | Controlled by selected provider, not HeySig | Verify provider and retention/network behavior on supported devices; do not claim offline-only |
| Recognized words | Transient phone memory; watch sends words/alternatives to phone via MessageClient | No command-body logging; clear display/local data | Assess ephemeral processing and user-initiated transfer under current form definitions |
| Watch transport | Data Layer SDK 20.0.1; package/signer-restricted, encrypted routing; may use Google servers | Nonpersistent MessageClient; no DataItems, retries or app backend | Review Google SDK data disclosures plus paired-device transport; no blanket no-collection claim |
| Spoken text | Optional installed TTS engine; can include requested settings | Provider/selected voice behavior | Network voices can process text off-device; verify providers |
| Result journal | Phone private preferences: paired node/request IDs, timestamp, reply | At most 64, expire after 24h, purge on open/access; explicit clear | Replies can contain hearing-aid settings; journal excludes raw command words and alternatives |
| Watch preferences | Selected node/name, last submitted request/node ID, speech/disclosure choices | Until clear/uninstall; no stored transcript or result body | Node identifiers are not hardware/advertising IDs; review SDK processing separately |
| Export | Android document picker writes JSON only after confirmation | User/provider-selected copy outside app deletion | Disclose optional export and any cloud provider selected by user |
| Policy browsing | ACTION_VIEW opens verified public HTTPS policy URL, once configured | Browser/host policies | Verify public host, publisher contact, log/retention practices before publication |

Android backup is disabled and data extraction rules exclude all app data
domains. Verify behavior on supported OEM devices. No advertising or analytics
library was intentionally added. Dependency inventories, merged manifests,
network/device checks and the medical/health review remain required; the table
does not replace them.

Full deletion uses Android's own clearApplicationUserData operation to erase
private app/SDK data, reset permissions and close the app. Deletion is per
device and explicitly confirmed. Deleting local fitting data
does not send commands or change the aids. Saved restoration records survive
the narrower history-clear operation. Do not run destructive data-clear
acceptance against the user's existing fitting; use isolated test storage.

## October 17 hands-free change (release review required)

An explicitly started microphone foreground service now supports on-device
Hey Sig keyword detection and bounded command recognition while locked. Wake
PCM is memory-only and the app adds no audio/transcript history. Recognition
prefers the Android on-device service, with a disclosed installed-provider
fallback that may transmit command audio; provider/network behavior must be
verified on the final release. TTS choices remain independent. The new native
engine/model ship with notices and pinned hashes. Hands-free disclosure/watch
choices are local; session state/deadlines are not persisted or restarted.
The already-selected watch can send commands and listening controls during the
live session using the existing bounded result journal. Off retains this
foreground control session; End closes it. Update microphone-FGS declarations,
privacy policy and device/false-trigger/battery evidence before submission.

Optional shake activation processes acceleration in memory only; separate phone/watch On/Off choices are local/exportable/deleted with app data. No motion history or sensor analytics is added. Phone gesture is limited to listening On; watch gesture is activity-only. VIBRATE is added on both devices for a short acknowledgement. Physical false-trigger and battery trials remain pending.

October 18 extension: a separately started watch motion foreground session
offers vibration plus a user-tapped notification while the screen is off.
Acceleration and one-use 30-second notification tokens remain in memory only;
no new network or microphone path is added. Session authority is not persisted
and expires after eight hours. Watch adds POST_NOTIFICATIONS, FOREGROUND_SERVICE
and FOREGROUND_SERVICE_SPECIAL_USE. Provider speech flows apply after the tap.
Release-specific foreground-service eligibility/declaration and motion/battery
acceptance remain pending; this supersedes the activity-only scope above.

## Personal phrase learning — October 17, 2026

A separate phone-private preference store, `heysig-personal-phrases-v1`, now
retains up to 200 explicitly confirmed exact phrase mappings: phrase, existing
command meaning, setup generation, origin, confirmation timestamp, schema and
record ID. No raw audio or general transcript history is introduced. Pending
learning/reset confirmations are memory-only and expire after 60 seconds.
Mappings can be reviewed, disabled, forgotten or explicitly reset through the
phone UI and phone/watch commands; setup changes deactivate prior records.
General settings export excludes this store; local app-data deletion includes
it. No dedicated mapping export/import is implemented. Watch-origin phrases and
mapping review replies use the existing companion transport and its existing
bounded reply retention. The installed recognizer/TTS provider data flows still
apply. Update final release privacy and Data safety disclosures to account for
this opt-in retention of phrase text; it is not speech-model retraining.

## Sleep routine and watch Wi-Fi check — October 17, 2026

Sleep setup retains explicitly saved local times, configured slots and fitting /
revision context in `heysig-sleep-v1`. Unsaved voice drafts are memory-only and
expire after five minutes. This setup-only build creates no alarm, sleep sensor
collection, worker, microphone session or automatic command. Routine choices
are included in the user-selected local export and removed with local app data.

Watch More → Check Wi-Fi adds normal ACCESS_NETWORK_STATE permission to the
release watch app (development already has it). A passive network callback runs
for at most five seconds or until the dialog/activity is closed. It reports
Wi-Fi transport and Android's validated-internet flag, without SSID, IP address,
location, scan, persistence or network requests. Optional Check phone link uses
the existing status request/transport and existing reply retention. Opening
Wi-Fi settings delegates to the OS; HeySig does not change radio state.
