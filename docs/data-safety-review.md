# Release data inventory and Data safety worksheet

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
