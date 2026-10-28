# HeySig Android phone app

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
