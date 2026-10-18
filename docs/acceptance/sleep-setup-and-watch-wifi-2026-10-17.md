# Sleep setup and watch Wi-Fi check — October 17, 2026

Sleep setup adds Settings → Sleep routine with local bedtime/wake-up time
pickers, explicit configured-program selectors, Save/Discard and next-event
preview. Suggestions are 22:00/07:00 with neither program preselected. Shared
Kotlin validates program/time choices, parses voice commands and plans DST-aware
preview dates. Android owns private preference storage and screen lifecycle.
Saved choices, fitting/revision context and corruption handling are explicit.
Saving never calls the executor, changes fitting/aid state or arms a schedule.
Automatic overnight switching remains unavailable with a visible disabled
control and an honest voice reply; no alarm, receiver or worker is added.
This implements routine setup, not unattended scheduling or Sleep now/Wake now.

Phone/watch voice edits use scoped, five-minute drafts with separate review and
Save. AM/PM or 24-hour HH:mm is required. Configured names retain punctuation;
"program Sleep" retains ordinary program selection. Draft cancellation and
stale saved-revision/fitting checks preserve prior records. Saved routines
survive restart with scheduling Off. Export and privacy inventory include them.

Watch More → Check Wi-Fi adds a bounded passive network callback and normal
ACCESS_NETWORK_STATE permission in release. It observes actual Wi-Fi transport
separately from the default phone/Bluetooth route. Android's validation flag is
reported as such, not treated as proof of the HeySig phone link. Check phone
link requests status using the existing transport, without an aid command.
Callbacks close after five seconds, dialog dismissal or activity pause. No
SSID/IP/location, scans, persistence or radio toggles are introduced. The same
check can be opened by watch voice: "check watch Wi-Fi" or "Wi-Fi status".
Physical watch connection acceptance is pending because the watch is not
connected to the development host.

API reference consulted: [ConnectivityManager callbacks](https://developer.android.com/reference/android/net/ConnectivityManager#registerNetworkCallback(android.net.NetworkRequest,%20android.net.ConnectivityManager.NetworkCallback)).

Initial validation: `bash tools/check-sleep.sh` passed shared settings/range,
explicit time, program, next-day, timezone and DST skip/repeat checks. Build and
isolated Android persistence/navigation results are appended after execution.
No actual hearing-aid setting changes are used for verification.

Build and Android results:

- Shared Gradle regression suite passed, including sleep preview/time parsing.
- Phone/Wear debug APKs and phone instrumentation APK built successfully; both
  release Kotlin source variants compiled. No submission artifact was published.
- The Android 13 / SDK 33 Chromebook runner passed sleep setup navigation,
  disabled scheduling control, isolated save/validation, restart persistence,
  Off gate, draft source/expiry/cancellation, stale revision rejection, fitting
  invalidation and corrupted-record preservation. It also passed existing
  navigation, personal-phrase, privacy/noise and fake-executor checks, plus
  round-watch home layout contexts at font scales 1 and 2.
  Session report: `/tmp/heysig-sleep-wifi-smoke.txt`.
- The Pixel 9a development update installed successfully with existing app data
  preserved. No real routine was saved or aid command sent for verification.
- Native watch APK manifest inspection confirms application label HeySig and
  ACCESS_NETWORK_STATE permission. Watch installation/old-launcher replacement
  is being performed separately after wireless-debugging reconnection; do not
  infer physical Wi-Fi acceptance from an APK build or manifest check.

Watch update and launcher migration:

- Reconnected the Pixel Watch after its Wireless debugging port changed.
  Native HeySig debug update installed successfully with `adb install -r`,
  preserving the existing development package/signing identity and private data.
- Both Android component-specific disable attempts were rejected by the watch
  OS. AutoWear's own settings presented an additional overlay-permission gate;
  that permission was not granted.
- The owner then explicitly authorized disabling the old AutoWear companion on
  the watch, including all its launcher entries while retaining saved data.
  `pm disable-user --user 0 com.joaomgcd.autowear` returned `disabled-user`.
  This is a reversible package-disable operation, not an uninstall or data wipe.
  Native `com.heysig.watchdev` remains installed as HeySig. No phone Tasker
  configuration was removed, and no hearing-aid command was sent.
- UI automation returned a null accessibility root while checking the native
  watch screen; do not treat that attempt as physical Wi-Fi-dialog acceptance.
