# HeySig App

A Kotlin Android acoustic-control prototype and research workspace for a future replacement of the HeySig Tasker setup. Direct program, left/right battery, volume, Sound Sharpness and tinnitus requests have passed focused user acceptance on the tested Pixel/hearing-aid setup.

The [coding specification](docs/coding-spec.md) defines screens, functions, data contracts and phone/watch execution flows ([printable PDF](docs/HeySigApp-coding-spec.pdf)).

Google Play release work follows the [dated readiness audit and release gates](docs/play-store-readiness.md). The [Gradle build guide](docs/gradle-release-builds.md) covers preserved development signing, separated release identities, API 36 and signed app-bundle validation. These candidates are not ready for store submission. After bundle validation, run `python3 tools/check-play-readiness.py` for release-manifest checks; this does not replace Play review.

The [HeySigApp program specification](docs/program-spec.md) defines the replacement app, including voice access to every function through both phone and watch.

Start with [how the prototype works](docs/proto-code-guide.md), [Android build/use](androidApp/README.md), and [acceptance evidence](docs/acceptance/). Full guided voice setup, automatic noise/sleep control, production setup/storage and watch pairing acceptance, and an iPhone implementation remain future work. The earlier accessibility-based replacement plan is retained as historical planning.

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

Development now targets the actual Android app running in the Chromebook Google Play environment. See [Chromebook-first development and current manual-core scope](docs/chromebook-development.md). `HomeActivity` provides native Home/Setup/Saved/Voice/Noise screens. Experimental [six-level Noise Setup and Observe](docs/noise-quick-start.md) propose programs without playback; automatic noise switching remains pending. Phone and watch each have independent saved switches for voice prompts and spoken responses. Release candidates now include a [foreground Data Layer watch connection](docs/production-watch-connection.md) and Privacy and data controls. Physical pairing acceptance and [public policy finalization](docs/privacy-policy-draft.md) remain pending. Watch parity and full guided voice configuration remain in progress.

The debug source set retains `ProtoActivity.kt` and the development watch relay; release builds exclude them. `ProtoRequest.kt` generates PCM command tones; Android plays them through its built-in speaker with audio guards. The app remembers completed requests. In this acoustic setup neither HeySig nor Signia can query the aids' current state. User feedback provides separate acceptance of the tested changes.

Keep the shared core independent of Android classes. Android services implement its control interfaces; a future iPhone control implementation can reuse the command and decision logic.

See [current findings and evidence](docs/current-status.md), [the implementation plan](docs/plan.md) and [architecture boundaries](docs/architecture/boundaries.md). The existing HeySig phone endurance trial runs independently of this scaffold.

Existing protocol research and the dependency-free offline packet regression are included under `research/acoustic/`. The existing eight-hour trial is pending final collection; this app scaffold does not run that trial.

## Proto builds

See [Chromebook build and use](desktopApp/README.md) and [Android prototype build](androidApp/README.md). Both reuse the explicit-profile Kotlin renderer. Android exposes all implemented families; the desktop window currently exposes battery playback/export. Offline encoder validation and actual aid reception have separate evidence and scope.
