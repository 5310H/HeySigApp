# Automatic noise return investigation — November 7, 2026

The owner reports that Automatic selected Very Noisy in a loud environment,
but did not return after the surroundings became quiet. This is user-reported
physical behavior; the exact runtime status, measured index and timing were
not captured. It does not establish acceptance of the return transition.

Code inspection found a phone-adapter failure path: `PhoneNoiseSampler`
reports temporary audio-priority interruptions as generic capture failures.
`NoiseFeature` then stops Automatic unless its fixed playback-settling window
is still active. Speech continuing beyond that window, or beginning during a
subsequent capture, can therefore turn Automatic off after a successful change.
This is a possible explanation, not a confirmed diagnosis of the owner's run.

The sampler now identifies priority pauses separately. Repeating sessions
discard interrupted measurements, clear confirmations and retry on their
existing schedule. Actual microphone, permission and route errors still stop
the feature. Explicit Stop, manual/Therapy priority, lifecycle cancellation,
foreground/unlocked guards, saved assignments/thresholds, cooldown and the
shared validated command executor are preserved. No audio/history is saved.

`tools/check-noise.sh` passed, including a new loud-to-quiet regression with
cooldown and an intervening audio-priority pause. This exercises the shared
decision reducer, not the Android microphone or physical aid reception.
Android debug Kotlin compilation and `:shared:test` passed using the pinned
Gradle wrapper and the documented ARM aapt2 override after restoring the
missing temporary SDK. Official platform/build-tools archive checksums matched
Google's repository index; the ARM archive matched the project's recorded hash.
The phone was unreachable at the supplied wireless ADB endpoint during this
investigation; device return-transition acceptance remains outstanding.
