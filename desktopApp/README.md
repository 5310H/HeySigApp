# Chromebook audio proto

This JVM desktop window runs in ChromeOS's Linux environment and uses the same Kotlin battery generator as the Android prototype. It provides separate left/right playback and WAV-export actions, explicit address/profile fields, an output-device selector, and Stop. It never plays audio on startup. Playback completion does not confirm aid reception or report a battery percentage.

Build and open:

```sh
tools/build-desktop.sh
java -jar desktopApp/build/heysig-proto.jar
```

Requires Kotlin/JDK 17 and Linux desktop/audio integration. Use the Chromebook's built-in speakers, disconnect headphones/Bluetooth outputs, and check the ChromeOS audio output and volume. The Linux mixer selector cannot inspect ChromeOS's final physical routing or Do Not Disturb state.

The profile fields initially show the extracted encoder's constructor defaults. Both addresses start empty. Enter the **fully qualified address** for the selected aid (brand identifier shifted left four bits, OR the configured acoustic address); do not substitute an arbitrary example address. The selected profile must match the aid configuration. Stop can interrupt playback, but cannot undo a packet already received.

The renderer has cross-language regression tests and restricted original-CIL comparisons. Real Chromebook-speaker reception remains pending the user's audible feedback. The desktop app stores no profile, recordings, or settings and runs no background listening.

For the user’s no-pairing setup, **Use basic Signia defaults** selects the statically traced basic address-zero case and the startup profile (gain divisor 1.2). This is a manual test preset; try one side and report the audible aid response. It does not assume a Bluetooth pairing or require entering pairing numbers. The preset’s match to the current device remains a test hypothesis.

Startup tracing found that Signia overrides the constructor gain divisor 4 with **1.2**. The basic preset now applies that override; the initial empty-address form still shows constructor values until a preset or explicit profile is chosen. See [startup findings](../research/acoustic/notes/proto-request-trace-2026-10-14.md).
