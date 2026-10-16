# Pixel Watch status acceptance — 2026-10-16

Tested the installed HeySig Watch development companion on the Pixel Watch,
connected over Wi-Fi to the pinned TLS development relay and the foreground
HeySig Android app on Chromebook. The Chromebook's Watch Wi-Fi control was
explicitly enabled. The relay remained in status-only mode.

## Results

- Reconnected the watch through its current Wireless debugging connection port.
  Both HeySig apps were already installed.
- Restored the ADB loopback forward on TCP 18765 and started the TLS relay on
  TCP 8765.
- Selected Read status on the watch. The relay recorded a FINAL reply from the
  Chromebook: Universal, slot 1; volume 8; Sound Sharpness −4; tinnitus 8.
- Asked the user to select Speak command and say “current program.” The user
  confirmed: “Displayed and spoke the reply.” This verifies the spoken status
  workflow and audible reply on this setup.
- The relay's five automated checks passed earlier in this session.

These values describe the last settings sent by the app, not hearing-aid
readback. In status-only mode, recognized status questions are forwarded as a
general status request. No hearing-aid controls were enabled for these tests.

## Still pending

Live acoustic controls through the watch require a separate listening test.
Stop during playback, uncertain-request recovery on devices, and connection
loss during live requests have not been accepted on hardware in this session.
The relay remains a foreground development connection, not a production
background service. Tiles and guided voice setup remain unimplemented.
