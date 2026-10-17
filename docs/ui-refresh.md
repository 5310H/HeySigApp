# Native UI refresh

The phone uses large headings, a soft neutral background, inset white cards,
blue primary actions and a four-tab layout: Home, Noise, Saved and Settings.
It takes cues from Apple's clear visual hierarchy while retaining native
Android controls, Android Back, system speech/document pickers and scalable
system text. No Apple fonts or assets are bundled.

Home presents the last requested program, named program tiles and level
sliders. Moving a slider changes a draft value only; Send still goes through
the existing validation and guarded acoustic executor. Battery actions request
the aids' existing indications; there are no invented battery percentages or
live-state claims.

Speak and Stop remain available below the scrolling page. Aid setup, voice,
watch and privacy controls live under Settings. Large fonts use a two-column
tab arrangement on narrow, tall screens. On-screen keyboards hide the tab bar
to leave room for editing. Wide windows keep content at a comfortable maximum
width. Decorative subtitles hide at large text sizes, and title/brand scale
limits preserve space for controls while body text follows the system scale.
Setup retains its unsaved-edit confirmation; noise drafts retain their
existing validation and explicit Save/Discard flow. Measurement settings are
available through an expandable section instead of filling the main noise page.

Both Wear variants share a black background, rounded controls, clear blue
speech action and at least 52dp button height. Speech choices and connectivity
behavior are unchanged. The existing development relay stays separate from
production Data Layer pairing.

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
