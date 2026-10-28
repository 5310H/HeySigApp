# HeySig App

A Kotlin Android acoustic-control prototype and research workspace for a future replacement of the HeySig Tasker setup. Direct program, left/right battery, volume, Sound Sharpness and tinnitus requests have passed focused user acceptance on the tested Pixel/hearing-aid setup.

The [coding specification](docs/coding-spec.md) defines screens, functions, data contracts and phone/watch execution flows ([printable PDF](docs/HeySigApp-coding-spec.pdf)).

Track outstanding tests, owner inputs and release work in the [remaining work checklist](docs/remaining-checklist.md).

Google Play release work follows the [dated readiness audit and release gates](docs/play-store-readiness.md). The [Gradle build guide](docs/gradle-release-builds.md) covers preserved development signing, separated release identities, API 36 and signed app-bundle validation. These candidates are not ready for store submission. After bundle validation, run `python3 tools/check-play-readiness.py` for release-manifest checks; this does not replace Play review.

The [HeySigApp program specification](docs/program-spec.md) defines the replacement app, including voice access to every function through both phone and watch.

Start with [how the prototype works](docs/proto-code-guide.md), [Android build/use](androidApp/README.md), and [acceptance evidence](docs/acceptance/). Full guided voice setup, physical automatic-noise acceptance and native sleep scheduling, production setup/storage and watch pairing acceptance, and an iPhone implementation remain future work. The earlier accessibility-based replacement plan is retained as historical planning.

Research: [noise adaptation in other hearing aids and proposed HeySig improvements](docs/research/noise-adaptation-2026-10-16.md).

## Directory layout

```text
HeySigApp/
├── androidApp/       Android UI, accessibility, audio, voice and background services
│   └── src/
│       ├── main/     Kotlin implementation and Android resources
│       ├── test/     Local Android-module tests
│       └── androidTest/  On-device acceptance tests
├── shared/           Platform-independent Kotlin core
│   └── src/
│       ├── commonMain/  Commands, control interfaces, models, settings, automation, protocol
│       └── commonTest/  Shared logic tests
├── iosApp/           Future iPhone client, pending a viable control path
├── wearApp/          Wear development client and separate release candidate
├── research/
│   └── acoustic/     Chirp notes, reproducible tooling and approved test fixtures
├── docs/
│   ├── plan.md       Implementation order and acceptance gates
│   ├── architecture/ Design decisions and platform boundaries
│   └── acceptance/   Recorded tests and evidence
└── tools/            Development and migration utilities
```

## Current implementation

The [native UI refresh](docs/ui-refresh.md) adds four-tab phone navigation, grouped cards, explicit Send sliders and a matching dark watch style. Existing fitting, saved settings and transport behavior are preserved.

Development targets the Android phone and Wear OS watch only. Both build types
use the direct Data Layer connection; the desktop relay and desktop runtime have
been removed. Phone speech switches govern prompts and replies on both devices.
See [phone/watch setup](docs/production-watch-connection.md).

`ProtoRequest.kt` generates PCM command tones through the guarded phone speaker
executor. Completed playback is remembered; it does not confirm hearing-aid receipt.
Watch voice now has observed recognition and spoken status completion, with
owner-reported operation. The current checkpoint is phone 0.13/watch 0.12;
see [device evidence and remaining acceptance](docs/acceptance/phone-watch-only-2026-10-27.md).

Keep the shared core independent of Android classes. Android services implement its control interfaces; a future iPhone control implementation can reuse the command and decision logic.

See [current findings and evidence](docs/current-status.md), [the implementation plan](docs/plan.md) and [architecture boundaries](docs/architecture/boundaries.md). The existing HeySig phone endurance trial runs independently of this scaffold.

Existing protocol research and the dependency-free offline packet regression are included under `research/acoustic/`. The existing eight-hour trial is pending final collection; this app scaffold does not run that trial.

## Proto builds

See [Android phone builds](androidApp/README.md) and [watch builds](wearApp/README.md). Both variants use direct pairing. Offline encoder validation and actual aid reception have separate evidence and scope.

Optional [hands-free sessions](docs/hands-free-quick-start.md) now add local Hey Sig detection, locked-phone manual commands, watch On/Pause/Off/End controls and separate default-off shake choices. The explicitly started watch session can receive shakes while its screen sleeps;
full locked-session reliability and battery acceptance remain pending. See the
[current session setup](docs/hands-free-quick-start.md). See the [October 17 implementation and acceptance record](docs/acceptance/hands-free-2026-10-17.md).

For the simplified setup checklist, Sleep reminders and guided Noise preview, see
[the UX quick start](docs/ux-quick-start.md).

Optional [automatic sleep switching](docs/sleep-quick-start.md) adds a separately
reviewed mode for future bedtime/morning program changes while locked. It starts
Off and requires phone alarm/notification access. Reminder mode remains available;
physical automatic playback acceptance and release review are pending.
