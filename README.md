# HeySig App

A Kotlin Android acoustic-control prototype and research workspace for a future replacement of the HeySig Tasker setup. Direct program, left/right battery, volume, Sound Sharpness and tinnitus requests have passed focused user acceptance on the tested Pixel/hearing-aid setup.

Start with [how the prototype works](docs/proto-code-guide.md), [Android build/use](androidApp/README.md), and [acceptance evidence](docs/acceptance/). Voice, noise/sleep automation, production setup/storage, watch support and an iPhone implementation remain future work. The earlier accessibility-based replacement plan is retained as historical planning.

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
├── wearApp/          Future watch client, pending device/platform selection
├── research/
│   └── acoustic/     Chirp notes, reproducible tooling and approved test fixtures
├── docs/
│   ├── plan.md       Implementation order and acceptance gates
│   ├── architecture/ Design decisions and platform boundaries
│   └── acceptance/   Recorded tests and evidence
└── tools/            Development and migration utilities
```

## Current implementation

`ProtoActivity.kt` provides foreground manual controls. `ProtoRequest.kt` generates PCM command tones; Android plays them through its built-in speaker with audio guards. The app remembers completed requests. In this acoustic setup neither HeySig nor Signia can query the aids' current state. User feedback provides separate acceptance of the tested changes.

Keep the shared core independent of Android classes. Android services implement its control interfaces; a future iPhone control implementation can reuse the command and decision logic.

See [current findings and evidence](docs/current-status.md), [the implementation plan](docs/plan.md) and [architecture boundaries](docs/architecture/boundaries.md). The existing HeySig phone endurance trial runs independently of this scaffold.

Existing protocol research and the dependency-free offline packet regression are included under `research/acoustic/`. The existing eight-hour trial is pending final collection; this app scaffold does not run that trial.

## Proto builds

See [Chromebook build and use](desktopApp/README.md) and [Android prototype build](androidApp/README.md). Both reuse the explicit-profile Kotlin renderer. Android exposes all implemented families; the desktop window currently exposes battery playback/export. Offline encoder validation and actual aid reception have separate evidence and scope.
