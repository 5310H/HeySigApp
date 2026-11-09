# Automatic noise usability and screen-off validation — November 8, 2026

The owner again reports loud-program switching without return when quiet, and
requests screen-off operation, one On/Off control, useful colours and a graph.
This report is preserved as unresolved physical acceptance; it is not replaced
by reducer or silent-transport checks.

Changes: ordinary phone Automatic runs in the separately disclosed, bounded
microphone/media-playback service. Activity pause no longer ends that explicit
session. On is green, Off red, with accessible text/action descriptions. The
main Noise screen shows a bounded scalar graph, last requested program, candidate,
confirmation progress and remaining cooldown. Unassigned levels explicitly keep
the current program. Advanced learning/calibration actions are moved off the
main control card. Existing fitting, assignments, thresholds and saved timing
are preserved. First startup no longer invents an initial successful-playback
cooldown; actual successful changes still start the saved cooldown, preserved
across explicit restarts within the process.

See [design and safety review](../automatic-noise-background-design-2026-11-08.md)
for permission, lifecycle, data and executor details. Existing foreground-only
constraints remain for preview, calibration, semantic monitoring and voice.

Shared checks and phone/Wear Kotlin compilation passed, and both development APKs
built. The shared regression checks loud → cooldown/audio pause → quiet, plus the
new locked-screen authorised-session case, while preserving manual/Therapy priority.

On the local Android 13 device, NoiseUiAcceptance passed with the screen verified
non-interactive: four valid captures (-53.8, -57.4, -57.9, -58.0 dBFS), continued
capture after isolated silent program playback, zero background learning samples,
and notification Off ending the session. The debug-only service uses isolated
core/routine/phrase preferences and a fake transport that cannot reach the speaker.
The real microphone is sampled in bounded one-second captures; PCM is discarded.
One accessible On/Off control, graph history/description, and native drawing at
font scales 1/2 passed. A separate full-screen check at 2× system font passed and
the original 1.0 setting was restored. Screenshot was visually inspected; content
scrolls beneath fixed navigation, and the Off control remains readable.

Android lint passed with 0 errors and 31 warnings. The broader navigation runner
initially failed an old “Sleep now” expectation; the UI already says “Therapy now”.
The assertion was updated to current terminology. The full Api36Smoke rerun
passed: Noise navigation/storage migration, >=48dp touch targets, current-runtime
insets, 192dp round-watch layouts at fonts 1/2, and isolated manual/routine/voice/
snapshot/privacy checks. No physical aid playback was used. Final phone results
will be added after completion. No Google Play
submission is being prepared. Physical aid reception and prolonged battery/field
acceptance remain outstanding.

Before updating the Pixel 9a (Android 17), read-only SettingsFingerprintSmoke
reported the following SHA-256 values, with no UI, edits or playback:

- core: `3c593d31f2be625d8aad0c8283bd53fd1b9ccc471d07c2605ded8b68d48c5df4`
- personal phrases: `03d545773e96eb515d849b04b9e1a43e4ae5216fddd7f42e68ca90d462412baa`
- routine: `ca63fad25bf5082c2be099c29f0c469108b78c6188752a26e0bc066bc668aa03`
- noise: `a9672863339302ffe83e173be31fa8e787658ac2e03b814b4ec2970eb53a9b00`

Read-only inspection of the phone’s saved noise choices confirmed assignments
`[1,1,3,3,3,6]`, boundaries `[-60,-50,-40,-30,-20]`, margin 2, three confirmations
and 60-second cooldown. Thus Universal requires the quiet levels below roughly
-50 dBFS; a quieter environment above that boundary still maps to Noisy. No
assignment or threshold was changed by this investigation. Current ambient
measurements are not proof of the owner’s earlier acoustic conditions.

Owner follow-up: simplify instructions for hearing-aid users who are not computer
users, and show edits in the editor. The guide and in-app How to use now explain
program choices, the red Off/green On toggle, waiting for quiet return, screen-off
use and stopping from the phone shade in short everyday steps. The main graph
uses Louder/Quieter; numeric microphone values and learning controls remain in
details. Source and before/after views were opened in the local editor. This
wording follow-up passed the APK build, lint (0 errors / 31 warnings), focused
screen-off service check and full navigation checks. Its focused rerun returned
three valid levels (-52.7, -58.0, -58.0 dBFS), with the screen verified
non-interactive and no background learning or physical aid playback. A fresh
normal-font screenshot was inspected and opened in the editor.
