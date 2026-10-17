# Native UI refresh

The phone uses large headings, a soft neutral background, inset white cards,
blue primary actions and three tabs: Home, Noise and Settings.
It takes cues from Apple's clear visual hierarchy while retaining native
Android controls, Android Back, system speech/document pickers and scalable
system text. No Apple fonts or assets are bundled.

Home shows the last requested program as a native chooser and four shortcuts:
Volume, Tinnitus, Sharpness and Batteries. Each opens its own focused page.
Moving a slider changes a draft value only; Send still goes through the
existing validation and guarded acoustic executor. Battery actions request
the aids' existing indications; no battery percentages or live-state claims
are invented.

Speak and Stop remain available below the page. Saved settings, aid setup,
voice, watch and privacy live under Settings. The three tabs stay in one row;
large text drops decorative tab icons. Status has a compact Info action for
its full message. On-screen keyboards hide the tab bar to leave room for
editing. Wide windows retain a comfortable maximum content width.

Noise opens with Observe, Sample, Stop and a concise status. Noise setup,
Calibrate, Measurement settings and Details & preview have dedicated pages.
Calibration exposes the relevant next action. Draft validation, explicit
Save/Discard and the Automatic release lock remain in place.

The watch home shows status, a visible Speech off indicator when applicable,
a primary Speak action (Connect on an unconnected release watch), and Stop/More.
More holds the secondary actions and independently optional speech choices.
Both Wear variants share this compact, black native layout with at least 52dp
button height. Development relay and production Data Layer pairing remain
separate.

The phone and watch launcher use the owner-supplied teal ear artwork, with the
T, bees and subtitle removed. Adaptive and themed icons use the same ear paths.
The [clean SVG and preview](../assets/branding/) preserve the source geometry;
the supplied original is unchanged.

Styling does not change storage formats, microphone permissions, speech/data
handling, fitting, noise thresholds, Automatic availability or aid transport.
Privacy disclosures and explicit destructive confirmations remain in place.
Technical waveform constants were removed from the setup UI; the actual
validated profile and its documentation are unchanged.

Optional instrumentation previews are local native view renders. They are not
production collection features. Instrumentation writes them to app files and
the QA runner copies them locally; no screenshot uploader is added to the app.
Screens containing user
settings stay under ignored `private/ui-preview/`, outside Git. Round-watch
style checks use isolated 192dp round contexts at font scales 1 and 2 and do
not substitute for physical Wear OS acceptance. See the dated acceptance record
for actual device/build results and limitations.
