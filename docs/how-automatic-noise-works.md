# Automatic noise behavior

Development behavior, November 17, 2026. Choose Universal, Noisy and Very Noisy under Noise → Choose programs and save. Automatic no longer identifies TV as a separate situation or selects the saved TV role. Existing TV program choices and Watching TV preference remain in saved settings for compatibility, but Automatic ignores them. Manual program selection still works.

Automatic uses a ten-second microphone capture. Three separate three-second sections estimate speech masking, retained sound, removed sound and background noise. A majority is needed for a noise category. It does not measure the hearing aids' internal noise cancellation; the phone estimates how much speech is masked from its own microphone.

| Evidence | Expected saved program |
| --- | --- |
| Clear speech or independently confirmed quiet | Universal |
| Speech partly masked / first supported noise level | Noisy |
| Speech heavily masked / stronger noise requiring more support | Very Noisy |
| Uncertain, invalid/clipped capture or missing assignment | Keep current program |

With repeated checks, Automatic can move from Universal to Noisy when speech is partly masked, then to Very Noisy if later evidence shows heavier masking. As masking falls, fresh clear or quiet evidence returns it to Universal. A single short quiet gap does not cause a return. Noisy destinations use the saved confirmation count (normally three, at least two); Universal return needs two fresh matching checks. The saved cooldown (normally 60 seconds) clears confirmation until it finishes. A change or return can take over a minute. Uncertain evidence clears confirmation; it never becomes quiet merely because time passed.

Speech masking uses a local model and retained-to-removed sound estimates. Recognized background noise without reliable speech uses saved relative digital thresholds (levels 1–3 Normal, 4–5 Noisy, 6 Very Noisy, with the saved margin). These phone levels are not calibrated sound-pressure readings. Music, ambiguous sound and conflicting evidence may hold the current program. The estimates do not measure understanding or identify a target talker. A missed or false classification remains possible.

Phone Automatic starts from an open, unlocked app after its microphone explanation and may continue with the screen locked under its visible notification. Tap green On or notification Off to stop. The standalone Estimate noise check is foreground-only, cancellable and sends no aid command. Captured audio is processed in memory and discarded. Reviewed memories save at most 24 compact summaries, never audio or transcript. Legacy TV memories remain stored but are ignored. The profile version changed so earlier memories cannot override the new noise categories.

Manual controls, Therapy and the shared validated executor retain priority. A completed send does not prove that the aids received it. Sessions stop after eight hours and never restart themselves.

The selected watch runs the same noise detector and sends scene estimates plus a compact summary through encrypted Google Wear OS Data Layer, potentially through Google servers. It sends no raw audio or transcript. Reviewed memories remain on the phone; phone and watch experiences are separate. Both builds must share the same profile. Off or disconnection ends capture and future automatic requests.

## Known limits and physical checks

The former TV detector returned TV both with the TV playing and during owner-confirmed conversation with the TV off. TV source recognition failed the room comparison and has been removed from Automatic selection. The earlier public-recording experiment also showed conversation false positives. This change avoids TV-driven switching; it does not establish reliable masking estimates in this room.

Native fixture checks establish packaged-model consistency; policy code controls confirmation and priority. Neither establishes accuracy in this user's room, wrist position, hearing-aid reception or battery life. Test clear/quiet, partly masked and heavily masked sound separately. Record mistakes and actual physical aid response before claiming acceptance. Enabling Automatic is not physical acceptance.

The watch's combined capture can take up to 30 seconds to analyze after recording. It rejects stale results using capture-end timestamps. Confirmation continuity accounts for capture, processing and transport delay; saved settings are not rewritten. Physical screen-off microphone, transport and battery behavior still require device checks.

## Remembering a correction

Open Teach Automatic after a recent ten-second check. Teaching stops Automatic. Choose Normal, Noisy or Very Noisy and tap Remember. Up to 24 compact summaries and categories are saved privately on the phone. No audio or transcript is saved. Saving enables reuse; you can disable memories without deleting them, or forget them. Memories never fit thresholds or rewrite program assignments. Old TV corrections remain saved but are ignored.
