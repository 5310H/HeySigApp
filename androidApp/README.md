# HeySig Android phone app

November 4 development update: Automatic on/off controls foreground noise-level
program switching. Automatic also learns threshold suggestions from its existing
measurements; nothing is saved without review and approval. Calibration remains
optional. Home and Settings show only Build plus the Git revision. Existing
fitting/settings were preserved during the in-place update. See
[how it works](../docs/how-automatic-noise-works.md) and
[verification and limits](../docs/acceptance/noise-threshold-learning-2026-11-04.md).

Native phone controls, saved fitting/settings, voice and reviewed background
services share the validated acoustic executor. The watch connects directly
through Wear OS Data Layer in both development and release builds.

Use matching phone/watch builds: release `com.heysig.app`, development
`com.heysig.batteryprototype` (launcher **HeySig Dev**). Updating the existing phone
package preserves its fitting. No fitting is copied between package identities.
There is no desktop relay, loopback listener or pairing broadcast receiver.

See [phone/watch setup](../docs/production-watch-connection.md),
[build instructions](../docs/gradle-release-builds.md) and
[voice guide](../docs/voice-quick-start.md). Speech switches live on the phone
and independently control prompts and responses on both devices.
