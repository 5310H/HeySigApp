# HeySig privacy policy — release draft

**Not yet a published policy.** Publisher identity, contact and hosting must be
supplied and verified before this can be finalized. This draft describes the
release candidate, not the separate development relay.

Effective date: October 16, 2026, subject to review before publication.

Publisher: **pending owner confirmation**. Privacy contact: **pending owner
confirmation**. Public HTTPS policy URL: **pending owner confirmation**.

HeySig generates speaker tones from your saved hearing-aid setup and requested
controls. Completing playback does not establish that the aids received a
change. No regulatory or device-compatibility classification is made here.

## Information used and stored

The phone stores your fitting addresses, program names/capabilities, last-sent
requests, a saved restoration record, noise settings, optional speech choices
and disclosure choices in private app storage. The phone and watch store their
selected companion's Data Layer identifier and display name. These identify
the app's paired node, not an IMEI or advertising identifier. The watch also
stores its last submitted request identifier and selected phone identifier so
you can check a result without repeating a command.

Settings remain until you delete them or uninstall the app. Android app backup
and device-to-device extraction are disabled by the app's manifest and
extraction rules. This does not describe copies you create with Export.

The phone retains up to 64 watch request-result records, including identifiers,
timestamps and reply text that can contain requested hearing-aid settings.
Records expire after 24 hours and are removed on the next app open or history
access. Raw command words and recognition alternatives are not written to this
journal. Heard words and reply displays can remain in memory until cleared or
the screen/process ends. The app does not retain audio recordings.

## Optional microphone and speech features

Noise Sample/Observe uses bounded microphone capture while the phone app is
open, after an explanation and runtime permission. Samples are processed in
memory into relative sound statistics and discarded; this feature does not
write recordings or upload noise audio. Stop or leave the app to stop capture.
Automatic program switching remains disabled.

Speech recognition is optional. Your installed recognition service accesses
the microphone and may send audio to its provider over a network. Its privacy
policy, retention and deletion controls apply to that processing. HeySig receives
recognized command text and alternatives. On the watch these are sent to the
phone for command interpretation. Cancelled recognition sends no command.
Buttons and typed commands are available instead.

Optional voice prompts and spoken responses use your installed text-to-speech
engine. A network voice may send reply text, including requested settings, to
that provider. These choices are independent and start off in new production
installations. Text status remains visible. Installed speech providers and
their network/data behavior still require release-device verification.

## Phone/watch transport and other providers

The release connection uses Google's Wear OS Data Layer in Google Play services.
Command text, recognition alternatives, request identifiers and replies travel
between your paired Android phone and watch. Google manages encrypted
transport. Messages may use Google-owned servers when Bluetooth is unavailable;
this is not an offline-only or Bluetooth-only feature. The release app has no
developer-operated relay or command server. It registers an Activity-scoped
listener only while the phone app is open and the selected watch is enabled.
There is no durable command queue or automatic replay after interruption.

The code does not integrate advertising or analytics SDKs. Google Play services,
speech providers, the operating system and selected document/browser providers
have separate data practices. Final third-party SDK disclosure and network
verification remain required before publisher approval of this policy or the
Play Data safety form. Do not interpret this draft as a claim of no data
collection by third parties.

## Your controls

- Turn voice prompts and spoken responses on/off independently on each device.
- Decline speech recognition and use buttons/typed commands instead.
- Stop noise sampling; revoke microphone permission through Android settings.
- Disconnect or forget the selected watch on the phone.
- Clear heard text and phone watch history from Privacy and data. This retains
  fitting and saved restoration settings.
- Export local settings and retained results using Android's document picker.
  The file can contain hearing-aid settings. A location you choose may upload
  the file to a cloud provider; protect and delete that copy separately.
- Delete local app data after confirmation. Android closes the app, resets its
  permissions and erases private app data, including that device's setup,
  preferences, companion selection and local history without transmitting an
  aid command. Delete data separately on your other device. Uninstall also
  removes local app storage.

HeySig has no app account or developer backend in this release. Device deletion
cannot delete exported files or records held by your speech/Google/document
providers; use their own data controls. Production support/requests must use
the verified publisher contact once supplied above.

## Development builds

Development builds retain separate package IDs and the optional ADB/Linux
relay. That relay has its own SQLite command-digest/reply history, retention
and deletion responsibilities. It is not part of the production app or covered
by the release transport statements above. Deleting phone data does not erase
the computer's relay database.

## Publication checklist

Supply real publisher/contact details; verify installed speech/TTS providers
and SDK behavior; reconcile the final release manifest and Data safety form;
publish a publicly accessible, non-PDF HTTPS policy; set the verified public
fields in `gradle/release-public.properties`; verify the in-app phone/watch
links. This draft must not be submitted as a finished declaration.

References checked October 16, 2026:
[Wear Data Layer transport/security](https://developer.android.com/training/wearables/data/overview),
[Google Play User Data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).
