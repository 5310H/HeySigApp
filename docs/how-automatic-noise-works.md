# Automatic: TV and noise together

Development behavior, November 16, 2026. Choose Universal, Noisy, Very Noisy and TV under Noise → Choose programs and save. Unassigned situations keep the current program. Calibration and teaching are optional.

Automatic analyzes one ten-second microphone capture. The full capture feeds a fixed local TV head; three disjoint three-second sections feed the existing speech detector, neural retained/removed-sound measurements and relative level calculations. A majority of sections is needed for a noise category. There is at least a ten-second pause plus processing between captures.

| Evidence | Expected saved program |
| --- | --- |
| Sustained very noisy sound | Very Noisy, including while TV plays |
| Sustained noisy sound | Noisy, including while TV plays |
| Recognized TV without stronger noise | TV |
| Normal sound or independently confirmed quiet | Universal |
| Uncertain, invalid/clipped capture or missing assignment | Keep current program |

For speech, retained-to-removed sound estimates must agree with the existing speech head, except a strong independently recognized machinery/traffic/broadband-noise event can support the reduction estimate when the speech head abstains. For recognized background noise with no reliable speech, saved relative digital thresholds distinguish levels: levels 1–3 Normal, 4–5 Noisy, 6 Very Noisy, with the saved margin. This is not a calibrated SPL meter or a measurement of understanding. Quiet return needs actual low-level evidence, never an uncertainty timeout.

Watching TV remains a saved optional listening preference: clear sound uses TV while that preference is on, and quiet still returns to Universal. TV recognition also runs with it off. Turn the preference off and save to test automatic TV-to-normal return without that override.

Changes need repeated fresh checks: the saved count (normally three, at least two) for TV/Noisy/Very Noisy, two agreeing targets for Universal. The saved cooldown (normally 60 seconds) clears confirmation until it finishes. A return may take over a minute. TV and Normal confirmations cannot be merged merely because both use the same speech severity. Manual controls, Therapy, phone audio and the shared validated executor retain priority. A completed send does not prove the aids received it.

Phone Automatic starts from an open, unlocked app after its microphone explanation and may continue with the screen locked under its visible notification. Tap green On or notification Off to stop. Sessions stop after eight hours and never restart themselves. The standalone Check TV and noise is foreground-only, cancellable, and sends no aid command.

## Remembering a correction

Run the ten-second check or use Automatic, then open Teach Automatic within two minutes. Teaching stops Automatic. Review its last situation, choose Normal/Noisy/Very Noisy/TV and tap Remember. Up to 24 compact 40-number summaries and your category are saved privately on the phone. No audio, words or transcript is saved. Saving enables reuse; you can disable memories without deleting them, or Forget remembered corrections. Changes never silently fit thresholds or rewrite program assignments.

A correction applies only to very similar sound with the same model, phone/watch source and fitting identity. Conflicting matches abstain. Strong detected noise keeps priority over a remembered TV/Normal category; confirmations, cooldown and Therapy/manual protections remain. This is a bounded preference memory, not continuous retraining or a promise to recognize every place.

The selected watch runs the same detector and sends scene estimates plus a compact summary through encrypted Google Wear OS Data Layer, potentially through Google servers. It sends no raw audio or transcript. Reviewed memories are stored on the phone; phone and watch experiences are separate. Both builds must share the same profile. Off/disconnection ends capture and future automatic requests.

## Known limits and actual checks

Public-recording development tests remain small. The expanded TV head correctly classified 48 of 52 validation windows, with two false TV windows from one conversation recording and two uncertain piano windows. Two gains of the same excerpt are correlated, not independent experiences. Most validation sources were already inspected in the initial experiment; only the newly frozen Annerie TV source is fresh validation for the expanded fit. The distant-TV example missed initially was moved into development and is no longer independent validation.

Native fixture checks establish packaged-model consistency; policy checks establish confirmation/priority behavior. Neither establishes accuracy in this user's room, wrist position, hearing-aid reception or battery life. Check real TV, conversation, background noise and quiet separately and record mistakes before claiming physical acceptance.

November16 measured watch timing: the initial standalone fixture took 79.498s while the watch was dozing; repeating with screen wake commands took 21.864s. Neither met the former three-second pipeline's 15s limit. The combined adapter now reuses one section encoder instead of loading it three times. The watch retains actual capture-end timestamps and rejects a result whose processing plus conservative transport age exceeds 30s. Lease bounds remain under five seconds; source, session, strict sequence, manual/Therapy and cooldown checks remain unchanged. Confirmation continuity is separate from individual age: watch-only runtime gap allowance is at least saved wait (minimum10) + ten-second capture + 30-second processing/transport budget + five-second lease margin (55s at default wait). Saved settings are not rewritten; phone microphone timing is unchanged. Tests exercise three repeated decisions 45s apart and still reject >30s results without consuming sequence. Native fixture checks now hold a bounded partial wake lock matching the real service; physical screen-off microphone/transport and battery acceptance remain deferred. Watch changes can take a few minutes after repeated checks and cooldown.
