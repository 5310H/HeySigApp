# Button feedback and dated Build labels — November 9, 2026

Owner requests: reverse button colors while pressed, restore afterward, and add
the date after the Build ID. The latter updates the older revision-only label
instruction in AGENTS.md.

Phone action roles (including On/Off and destructive actions) and watch actions
use native pressed/enabled drawable and text states. Pressing swaps foreground
and background. Release or cancellation restores the original state without
wrapping command listeners or changing their timing. An On/Off button still
shows its actual running state after the tap. Disabled buttons do not invert.
Framework dialog buttons inherit matching theme selectors on phone and watch.
Existing operation progress/status and disabled states remain authoritative;
feedback itself does not send a command or indicate that an aid received it.

Phone Home/Settings and watch About show `Build <revision> · <commit date>`.
The date is derived from the Git commit, so both APKs and repeat builds of the
same revision agree. Version codes, build types and version names remain hidden.

Local Android 13 UI smoke passed for phone roles, watch actions, dialog
buttons, disabled actions, navigation, 48dp targets and font scales 1/2 on
isolated round-watch layouts. The final rerun also passed navigation-tab feedback. Phone and watch debug
builds passed; lint reports zero errors (phone 33 warnings, watch 18 warnings). The isolated
ButtonFeedbackCheck exercises reversal/restoration for every phone action role,
watch Speak/Connect/Stop/More, and a framework dialog button without clicking
any hearing-aid command. Existing UI smoke coverage checks large fonts and
round watch layouts.

Final development APKs identify pushed code revision 81267cf. The Pixel's
installed Home screen was inspected with `Build 81267cf · Nov 9, 2026`. All four
existing phone preference-file fingerprints matched exactly after upgrade,
before live feature testing. Device installation does not establish Play
approval or hearing-aid reception.

Watch installation also succeeded at the owner-confirmed wireless endpoint. Its
heysig-watch-v1.xml fingerprint remained exactly
88180051545332a30497c1446ed38a171dc2056d25b8b88d055692c7bb624b3a
after upgrade. No hearing-aid command was issued for installation/UI checks.
