# Personal phrase teaching — October 17, 2026

Scope: initial explicit phrase teaching, separate from speech-model retraining.
The platform-independent `PersonalPhrases` resolver owns normalization, exact
matching, validation, memory-only learning/reset confirmation, fitting scope,
200-record limit, paged review and deletion. `PersonalPhraseStore` owns Android
private preferences. Home, foreground watch requests and hands-free requests
resolve through the same phone-owned store and existing executor.

Learning uses `learn phrase TV time means program TV`, a displayed/spoken
readback, and the distinct `remember that correction` within 60 seconds from
the same origin. Generic Yes does not save a mapping. Learning never dispatches
or replays a command. Confirmation prompts respect voice-prompts settings;
results respect spoken-responses settings and remain visible with speech off.
Known commands and reserved management/control phrases cannot be overridden;
numeric/negated/compound aliases are rejected. Actual execution still validates
fitting, ranges, requested baseline, capability and audio preflight normally.

Confirmed records contain exact phrase, existing command meaning, fitting
generation, origin, confirmation timestamp, schema and ID. General export
excludes these records. The feature adds no audio or conversation-history
collection. Review/forget/disable/reset are available by UI and voice. Setup
changes invalidate dependent mappings, including after changing back. A corrupt
store is preserved and personal matching disabled until an explicit confirmed
reset; ordinary exact commands remain usable.

Validation: `bash tools/check-manual.sh` passes all 429 existing voice phrase
cases plus personal-learning assertions for explicit consent, rejection of
reserved/numeric/compound phrases, same-origin confirmation, expiry, fitting
scope, simulated restart persistence, storage failure, immediate forgetting,
confirmed reset, record cap without eviction and bounded paged review.

Android isolated-store acceptance covers actual SharedPreferences restart
persistence, disable persistence, reserved subsystem commands, setup-change
invalidation including change-back, forget/reset, origin mismatch, corruption
preservation and explicit recovery. These tests contain no microphone request,
user fitting mutation or acoustic transport. Build/device results are recorded
below after execution.

Remaining scope: actual spoken teaching/recognition, real paired-watch flows,
large-font physical acceptance and hearing-aid receipt are not established by
parser/storage checks. Conversational “No, I said…” handling and separately
previewed phrase export/import remain future work; this is not completion of
all V2 specification gates. No Play submission or release approval is claimed.

Build/device evidence:

- ARM Gradle debug phone, Wear and instrumentation APK build passed.
- `python3 tools/run-device-smoke.py --device emulator-5554` passed on the
  Chromebook Android 13 / SDK 33 environment with target SDK 36. It reports
  `personalPhrases=PASS` for actual isolated-store consent/persistence,
  disable/forget/reset, source confirmation, setup invalidation and corruption
  recovery. The same run passed navigation, 48dp controls, current-runtime
  insets, isolated privacy/noise and executor checks, plus round-watch layout
  contexts at font scales 1 and 2. It requested no capture and played no tones.
  Report during this session: `/tmp/heysig-personal-phrases-smoke.txt`.
- Android 16 runtime, physical voice, paired-watch and physical large-font
  acceptance remain pending; the Chromebook run does not establish them.

Final checks: the Gradle shared regression suite passed. The final phone/Wear
and test APK build plus phone release Kotlin compilation passed. A subsequent
no-audio Android smoke run against the compact Home-card build passed all
personal-phrase checks (`/tmp/heysig-sig-scene-smoke.txt`). The Pixel development
update installed successfully with existing app data preserved. No real user
mapping was added or aid setting changed merely for installation verification.
