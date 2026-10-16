# Signia startup: fresh-process observation and static initialization trace

October 15, 2026. Installed Signia 2.8.0.18214 on the Pixel 9a. Static findings use the previously extracted assemblies; live findings use UID-filtered Android logs, launch timing, UI snapshots and audio-service history. Internal method entry/exit hooks and PCM capture were not available.

## Live launch

Fresh initial UI showed **TV active, volume 8, selected Volume tab, tinnitus tab absent**. Only Signia was force-stopped, then its installed main activity was launched with `am start -W`. No app data was cleared and no setup wizard was entered. This is an existing-installation fresh-process launch, not a first-ever install or a phone reboot.

Android reported:

- `Status: ok`, `LaunchState: COLD`.
- `TotalTime: 2503 ms`, `WaitTime: 2506 ms`. These are activity-launch timings, not proof every background initialization task finished.
- Old PID 16839, new PID 5963.

The UID-filtered capture spans launch plus ten seconds after the activity-launch command completed, followed by UI inspection. The final screen again showed **TV active, volume 8, selected Volume tab, tinnitus absent**.

There were **zero Signia playback-player creations for the new PID** in the captured audio-service history and no `frames delivered` completion in the new-process log. Thus this observed startup did not produce a logged acoustic playback. It is not evidence that every startup, reconnection, firmware or setup path is silent, nor a physical microphone recording.

Logs show .NET/Mono and audio-service listener initialization. Library/resource loader warnings also appear, but the app successfully reached Controls; these warnings do not establish a failed acoustic initialization. Ordinary logs did not expose active acoustic addresses, tone-generator values or every internal branch.

Raw diagnostics are in ignored `private/signia-startup-20261015/`. The [sanitized live observation](../fixtures/live-startup-observation-2026-10-15.json) records the relevant results.

## Static launch chain

In `RTA.Maui.Signia.dll`, `MainActivity.OnCreate` (`0x06000147`) performs Android/Maui initialization and lifecycle/native-service setup. `App.OnStart` (`0x060000c8`) forwards to the application lifecycle service. The launch-page callback (`0x060000d0`) invokes `IAppLaunchManager.InitiateLaunchSequence`.

In `RTA.Core.dll`:

```text
InitiateLaunchSequence
  -> StartServerBackendCheckAsync
     -> NavigateToFirstScreen
        -> SetRootPage
        -> saved setup/consent checks
     -> Android-platform branch: InitialiseAcousticStream
     -> background backend-alert validation

NavigateToHomeScreen
  -> read saved PairingType into currentSetupType
  -> SetAcousticHearingSystem
  -> create navigation drawer / home controls
  -> conditional notification-permission handling
```

`CheckIsPairingCompleted` accepts the saved RF-setup flag or acoustic-setup flag. Its internal terminology does not mean a Bluetooth handshake is mandatory for acoustic operation. Navigation and consent branches depend on stored installation state; not every new-install branch was exercised in the live run.

## Loading configured aids

`NavigateToHomeScreen` awaits `SetAcousticHearingSystem` before building the home navigation. That method checks saved acoustic setup, obtains stored acoustic IDs, loads an existing system through `IHearingSystemFactory.LoadAsync`, and selects it in the provider. The missing-ID branch creates a basic hearing system from saved left/right acoustic addresses. This is app-side reconstruction of configuration, not proof of communication with physical aids.

The separately inspected `HearingSystemInitializationService.InitializeAsync` also calls `SetBasicHearingSystem`, initializes the remote-control component, sets up hearing-system IDs and initiates reconnection handling. `SetUpHearingSystemIds` reads stored Bluetooth IDs. These are supported initialization/reconnection paths; no live method hooks establish that each branch ran in this acoustic-only startup. Do not confuse the available Bluetooth reconnection machinery with a demonstrated Bluetooth connection.

See the [setup/capability trace](setup-capabilities-2026-10-15.md) for local device GUIDs, per-ear program flags and qualified acoustic addresses.

## Initializing audio

Two independent settings matter:

1. **Tone amplitude:** `Bootstrapper.RegisterAcousticClasses` (`RTA.Maui.Signia.dll`, `0x0600033d`) calls acoustic initialization and sets `IToneGenerationConfigurator.BroadcastLevel = 2`. The previously traced setter indexes gain divisors `[4.0, 2.0, 1.2]` and recalculates. Thus this inspected registration path selects divisor **1.2**, overriding constructor divisor 4. This is static code evidence, not a runtime dump of the current generator instance.
2. **Android stream:** `AppLaunchManager.GetDeviceStream` reads `ConfigurationKeys.AudioStream`. `InitialiseAcousticStream` chooses `SetDeviceStreamToRinger` for saved value 1 and `SetDeviceStreamToMedia` otherwise. This selects the app's playback stream; it is not evidence of changing system volume. Earlier live battery/program/slider traces observed media playback.

No tone-send call is present in these inspected gain/stream initialization methods. The live no-playback result is consistent with initializing a transmitter without sending an aid command.

## Meaning for the battery investigation

The working Signia app can reopen with its saved TV/8 state without a logged startup transmission. This weakens a blanket explanation that every launch must first send an acoustic unlocking/arming chirp. It does not rule out other aid-state dependencies.

The known startup gain override is already implemented in the prototype preset. Startup tracing still does not recover the active address, exact PCM, or explain the failed prototype request. Same-phone playback comparison and exact address/waveform evidence remain the unresolved battery gates.

## Reproduction

[Method fingerprints](../fixtures/app-startup-trace.json) identify nineteen inspected startup methods in two original assemblies. Semantic findings are manual CIL interpretation. Audio gain implementation is separately fingerprinted in the [battery trace evidence](../fixtures/proto-request-trace.json).

```sh
python3 research/acoustic/tools/trace_app_startup.py /private/assemblies --output /tmp/app-startup-trace.json
```
