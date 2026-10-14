# Module boundaries

- `shared`: command vocabulary/validation, program and control models, settings schemas, automation decisions and transport-neutral control contracts. Protocol algorithms belong here only after validation; hardware/audio playback remains platform-specific.
- `androidApp`: Compose UI, speech recognition/text-to-speech, accessibility tree reads/gesture dispatch, Android microphone/playback, durable storage and background lifecycle. One executor serializes device operations and returns explicit results.
- `iosApp`: reserved for an iPhone implementation. Shared Kotlin logic does not provide Android accessibility capabilities on iOS.
- `wearApp`: reserved for a watch companion after selecting the actual watch platform.
- `research/acoustic`: experiments and reference comparisons. Confirm addressing, profile, complete waveform and actual aid behavior before promoting a direct-control implementation.
- `docs/acceptance`: evidence distinguishes app readback from independently verified hearing-aid response. Only battery checks currently have expected audible feedback.

The scaffold deliberately has no build configuration or runtime implementation yet. Dependency versions, app identifiers, minimum OS support and release signing will be set when the first Android feasibility project is created.
