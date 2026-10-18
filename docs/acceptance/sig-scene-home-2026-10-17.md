# Sig Scene Home card — October 17, 2026

The previously empty Home area now contains a native Sig Scene card. It extends
the existing ear mark with decorative teal sound rings, a last-sent program
and volume snapshot, and a shortcut to Personal phrases in Voice & speech.
The snapshot explicitly identifies unverified reception; missing volume remains
“not set.” It does not read live aid state, estimate battery, sample audio,
start recognition or send a command. No new permission or data flow is added
by the card. Its static artwork has no animation or background work.

Text uses scalable native views in the existing scroll area. Speak, Stop and navigation remain in the
fixed footer. The only new action opens Voice & speech. Existing program and
control actions retain their behavior.

The first visual/device check caught an oversized layout: the Teach action
required scrolling. The card was compacted rather than relaxing the Home action
visibility check. Final results are appended after rebuilding.
Physical aid settings are not modified to inspect this UI.

Final validation:

- Phone/Wear debug APKs and the Android test APK build passed. Phone release
  Kotlin compilation also passed; no release publication was performed.
- The unchanged no-audio Android smoke runner passed on `emulator-5554`
  (Android 13 / target 36), including all Home actions visible without scrolling,
  48dp targets, navigation, personal-phrase persistence and isolated executor /
  privacy / noise checks. Final report: `/tmp/heysig-sig-scene-smoke.txt`.
- Pixel 9a debug update installed successfully with `adb install -r`. Final
  visual inspection at the user's existing display/font configuration confirms
  the entire compact card, Teach action, Speak, Stop and navigation are visible
  together. Screenshot during the session: `/tmp/heysig-home-after.png`.
  No fitting values were edited and no hearing-aid command or capture was used
  to verify the layout. Last-sent TV / volume 8 remained visible.

## Follow-up: clipped artwork

The owner reported that the scene looked cut off. The original 320×100 drawing
area let the outer sound rings extend above and below the view, and independent
horizontal/vertical scaling stretched the ear. The artwork now fits a padded
320×160 scene using one uniform scale and centers it in the existing 64dp view.
The card and Home action heights are unchanged. Device verification follows.

The owner also requested a simpler summary: show only the configured program,
volume, sharpness (clarified from “balance”) and tinnitus values. Missing values
are omitted. Removed the repeated unverified-reception label and the Teach
shortcut from this card; personal phrases remain available in Voice & speech.
The noise success message and development prototype playback message also omit
the repeated unverified wording. Error handling and actual state tracking are
unchanged; this UI does not imply aid acknowledgement.

Program/level readouts and spoken replies use direct status labels, without
“Last sent” or “Last requested” prefixes. The underlying command state remains
unchanged; no aid readback or new data flow was introduced.

Follow-up phone debug/test APK builds passed. Pixel update installed with
`adb install -r`; screenshot `/tmp/heysig-scene-after.png` confirms complete,
proportional artwork and Program: TV / Volume: 8, with unset fields omitted.
Speak, Stop and navigation remain visible. Existing fitting and saved snapshot
bytes match the pre-update baseline. No real aid commands or capture were used.

Final Android smoke run passed, including Home action visibility and saved
settings checks. One preceding run lost window focus before Back navigation;
rerunning after waking the test display passed with windowFocusBeforeBack=true.

The owner clarified that Teach a phrase should remain. The shortcut is restored
in the sleep-management update; the compact artwork and status-only values remain.
