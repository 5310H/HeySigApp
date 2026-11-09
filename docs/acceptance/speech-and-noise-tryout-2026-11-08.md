# Display-only speech/noise try-out — November 8, 2026

The owner approved trying three descriptions before allowing sound-type judgments
to change hearing-aid programs. The new foreground-only phone page shows Mostly
speech, Speech and noise, Mostly noise or Not sure yet. It has no program-send,
fitting or executor dependency. Entering it stops ordinary Automatic; starting it
also stops other monitoring/hands-free input. Manual/voice actions, Stop,
navigation, lock/Activity pause and a five-minute deadline stop or interrupt
capture. Ordinary Automatic's screen-off service and saved level assignments are
unchanged and do not consume this new result. Both experimental environment
classification dispatch gates remain false.

A dedicated disclosure precedes microphone permission/access. Captures last three
seconds and start no more often than every ten seconds. The existing built-in
microphone adapter, MicrophoneLease, cancellation/PCM erasure, pinned YAMNet model
and scalar event outputs are reused. No model/dependency/SDK or manifest permission
is added. Model execution is local; this path has no network, recording or
transcript API. Up to 32 frame maps exist transiently during inference; the UI
retains only one enum/counter/timestamp. No audio, transcript or answer history is
saved. Only a feature-consent boolean is stored in a new preferences file.

Decisions are pure shared Kotlin. Existing event-score outputs are not calibrated
probabilities, intelligibility, SNR, speaker identity or evidence of a nearby
conversation. The conservative provisional reducer requires full finite event
evidence, three agreeing frames and >=75% within-capture agreement, then two
matching fresh captures. Speech plus competing babble/noise is distinguished
from speech alone. Music, weak/ambiguous/quiet evidence and missing/invalid inputs
remain unknown. Errors, priority loss and stale samples clear confirmations.
These thresholds are display heuristics, not classifier accuracy qualification.
The existing six-scene heads retain their original outputs and release gates;
optional frame observers do not change old callers or pinned model bytes.

Validation so far: the complete tools/check-noise.sh suite passed, including the
new reducer/freshness checks; all 34 Gradle shared tests passed with zero failures;
phone and Wear development APK builds passed. Android lint passed with zero
errors and the same 31 existing warnings. On the local Android 13 device, the
new route/native model/live microphone/foreground-loss cancellation checks
passed. Full Api36Smoke passed with the matching test runner (an initial run
using the previous test APK failed at the changed model constructor; rebuilding
and installing the matching runner resolved it). Large-font (2.0) route/model
checks passed and the system font setting was restored. Native fixture results:
quiet = Not sure yet; conversation = Mostly speech; non-speech noise = Not sure
yet. The last result is a known limitation of this provisional conservative
reducer, not a successful noise-class accuracy test. Live local sound was
Mostly speech; ambient identity was not independently established.

Commit 44083ec was pushed to origin/main before producing final development
phone/test/Wear APKs. Their final Gradle build passed; phone and Wear generated
Build IDs both identify 44083ec. The phone was updated in place. On Pixel 9a
Android 17, native fixture and route checks passed. The first live attempt
correctly refused capture while the phone was locked/screen off; after the owner
unlocked it, bounded live capture and foreground-loss cancellation passed. The
live description was Not sure yet; no ambient accuracy inference is made.
The sound check was left Off, its isolated test consent was removed, and no
hearing-aid commands or tones were generated. Phone and 2.0-font local previews
were visually checked. Four saved-setting SHA-256 fingerprints matched before
installation and after the live test (core fitting, phrases, Therapy and noise).
No stored fitting/program choices were changed. Actual field classification and
speech-clarity benefit remain unaccepted; no sound-type-driven dispatch exists. Test fixtures are
licensed development recordings and cannot establish independent field accuracy.
Live microphone checks will not play sounds or hearing-aid tones. The owner must
compare descriptions with actual comfortable everyday environments before any
future proposal to connect them to program switching. No release, regulatory
claim or Play submission is prepared.
