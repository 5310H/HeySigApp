# HeySig App

A planned Kotlin Android replacement for the HeySig Tasker setup, with voice, saved settings and optional noise/sleep automation. This repository scaffold contains a plan and directory structure; it is not yet a buildable app.

The first version will operate the installed Signia app through an Android accessibility service. Direct hearing-aid chirps are a separate research milestone. No working direct transmitter or equivalent iPhone control path is claimed.

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

## First milestone

Create the Gradle project, Android manifest and accessibility service. Read the active Signia program, select another configured program, verify fresh readback and restore the starting selection. Then implement microphone-button voice and the manual controls before continuous wake listening and automation.

Keep the shared core independent of Android classes. Android services implement its control interfaces; a future iPhone control implementation can reuse the command and decision logic.

See [current findings and evidence](docs/current-status.md), [the implementation plan](docs/plan.md) and [architecture boundaries](docs/architecture/boundaries.md). The existing HeySig phone endurance trial runs independently of this scaffold.

Existing protocol research and the dependency-free offline packet regression are included under `research/acoustic/`. The existing eight-hour trial is pending final collection; this app scaffold does not run that trial.
