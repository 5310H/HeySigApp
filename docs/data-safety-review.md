# Release data inventory and Data safety worksheet

October 16, 2026. This is implementation evidence for owner review, not a
submitted or approved Data safety form. Publisher identity/contact and final
provider behavior are still unverified. See [policy draft](privacy-policy-draft.md).

| Flow | Release implementation | Retention/control | Form review |
|---|---|---|---|
| Hearing-aid setup/settings | Private phone preferences; used to render validated tone requests | Until local deletion/uninstall; export optional | Assess health/device/settings categories from intended use; avoid classifying by permission alone |
| Noise microphone | Bounded foreground PCM to in-memory scalar statistics; no file/network writer in this feature | Discarded after capture; Stop/onPause; Android permission controls | Distinguish local-only noise processing from off-device speech |
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
