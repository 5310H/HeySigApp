# Sleep routine management

Open **Settings → Sleep routine**. Choose bedtime and wake-up clock times and
select a configured program for each, then **Save routine**. The 22:00 / 07:00
values are editable suggestions; neither program is chosen automatically.
Both programs are required and the times must differ. Save sends no aid command.

Use **Sleep now** or **Wake now**, review the saved target and tap **Switch**.
These explicit actions use the existing shared executor and preserve the saved
restoration snapshot. Routine mode records commands, not sensed sleep. Program
selection does not imply standby, power-down or microphone muting.

Choose **Enable sleep reminders** and allow notifications if asked. Reminders
open this screen; tap Sleep now or Wake now to switch. Nothing plays automatically
while the phone is locked. Android may delay reminders; delivery over 15 minutes
late is skipped. Times follow the current timezone, skip nonexistent DST times
and use the first repeated occurrence. Enabled reminders replan future times
after reboot/update or clock/timezone changes. Saving choices turns reminders Off.

Manual program/level commands pause the routine. **Resume sleep routine** allows
future reminders without sending a command. **Pause sleep routine** pauses
delivery; **Disable sleep reminders** disarms alarms. Failed/interrupted playback
leaves mode Unknown with override On. Review and explicitly request Sleep now or
Wake now; interrupted commands are never replayed.

Phone and watch voice setup:

1. **Configure sleep**.
2. **Set sleep time to ten PM** (or **22:00**).
3. **Set wake time to seven AM**.
4. **Set sleep program to Sleep** (use your configured name or slot).
5. **Set wake program to Universal** (use your configured name or slot).
6. **Review sleep routine**, then **save sleep routine**.

Say **read sleep settings** to hear saved choices and preview, or **cancel sleep
setup** to discard the voice draft. Voice drafts expire after five minutes and
remain scoped to the device/conversation that started them. Stop cancels drafts.
Ambiguous times need explicit AM/PM or a 24-hour HH:mm time. Setup changes require
routine review and Save again. Screen drafts have Save/Discard; navigation asks
before discarding changes. Saved choices and explicit reminder preferences survive restart.
Routine choices are included in optional local data export and app-data deletion.

Voice management: **sleep now**, **wake now**, **pause sleep routine**, **resume
sleep routine**, **enable sleep schedule**, **disable sleep schedule**, **sleep
status**. Schedule commands control reminders; notification access must first be
granted on the phone. Watch commands retain existing connection/session guards.
See [background design](sleep-management-design.md) for recovery and data handling.
