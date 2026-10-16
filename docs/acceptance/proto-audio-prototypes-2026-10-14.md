# Battery audio prototypes: build and offline validation

October 14, 2026. These checks cover experimental Chromebook Linux/JVM and Android battery-audio prototypes. They do not accept the full replacement app or aid reception.

- The Chromebook JVM window compiled with Kotlin 2.4.21 and JDK 17, passed its shared-generator runtime check, and rendered in the actual Linux desktop session. The UI exposes an ALSA default playback output. Its window-only screenshot was inspected; status text and controls were visible. No audio was played by this check.
- The final Android debug APK compiled, converted with D8/R8 9.5.26, packaged using ARM-native tools, and passed `apksigner verify`. Manifest inspection confirms package `com.heysig.batteryprototype`, min API 29, target API 35, the launcher Activity and a literal application label. APK resources use API 29 definitions for compatibility with the installed ARM packager; Kotlin compilation uses API 35. This initial packaging check did not install the app; subsequent phone launch testing is recorded below.
- Twenty-four Kotlin WAV/reference comparisons and seven invalid-input checks passed. Twenty-four additional comparisons against a bounded interpreter of original encoder CIL matched PCM bytes exactly. The original-CIL oracle models BCL math and arrays; it neither loads the DLL in .NET nor calls Android/native methods. [Detailed evidence](../../research/acoustic/fixtures/proto-waveform-validation.json).
- The existing packet regression passed 131,072 frames plus command-range, checksum and invalid-input checks. Source/shell syntax and whitespace checks passed.
- The user clarified there is no pairing in their setup. Static basic-setup defaults support a manual address-zero preset. No automatic sweep or playback occurs. The [call-path findings](../../research/acoustic/notes/proto-request-trace-2026-10-14.md) distinguish a test hypothesis from the active device configuration.

Build outputs (ignored, local files):

| Artifact | SHA-256 |
| --- | --- |
| `desktopApp/build/heysig-proto.jar` | `479ccbffba30a39006e27809b5b117738f8245ba422ae2e023cabd05a88b5c45` |
| `androidApp/build/heysig-proto.apk` | `62057e434ac6add01c5507d2672c5082481aac7d4218b23016ff5a4dcff69756` |

The Chromebook window was opened for the user's manual test. Pending: actual playback, Stop during playback, confirmed speaker routing at the ChromeOS layer, the user's audible aid response on each side, comparison with a live Signia reference, and phone runtime checks. Silence alone cannot distinguish a wrong profile/address from an unsuitable speaker route or speaker frequency response.

The eight-hour Tasker observation trial was not accessed or modified. No battery result or aid reception is inferred from successful file generation, UI rendering, or APK signing.

## Startup-level correction and live feedback

Offline startup tracing found that Signia overrides the constructor gain divisor 4 with 1.2. Both basic presets were rebuilt with divisor 1.2, and the waveform comparison suite was expanded to 24 cases, all exactly matching original-CIL PCM for those inputs. The existing desktop window can use the new level by manually setting its gain field to 1.2.

User feedback: no aid response from Chromebook requests at divisor 1.2, both with Signia closed and after a normal Signia launch. The original Signia left battery button still produced its usual audible aid indication. Thus Signia startup alone did not resolve Chromebook transmission. Active configuration differences, speaker/frequency response, and Linux/ChromeOS audio routing remain candidates; a working direct sender is not accepted. The user explicitly requested preservation of the saved setup. No reset, re-pairing or configuration change was performed.

## Pixel installation and visible settings

Reconnected wirelessly using the user-supplied debugging port. Signia’s App settings displays Smartphone sound = Media sound. Standard private-file access is denied because Signia is not debuggable; no access bypass was attempted. No acoustic address is exposed on the inspected page, and Set up the app was not selected.

The separate `com.heysig.batteryprototype` APK installed successfully and launched on the Pixel. Its visible tree confirms the numeric profile inputs, startup gain divisor 1.2, preset and side buttons. This accepts installation and initial rendering only. A manual left-request test on the Pixel’s speaker is pending user feedback. Signia data and settings were preserved.

The additional foreground phone use and APK installation may contribute to total-phone battery use during the observation period. Trial configuration and sampling controls were not changed.
