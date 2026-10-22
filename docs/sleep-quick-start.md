# Sleep routine

Open **Settings → Sleep routine**. Existing times and program choices are kept.
Choose only programs already fitted and configured for your aids.

## Automatic bedtime and morning switching

1. Review bedtime, morning time and both programs. To change them, open
   **Edit bedtime & morning routine → Review & save routine**. Choose
   **Save & start automatic**, or **Save only** to leave schedules Off.
2. For an already saved routine, choose **Start automatic routine**. Review the
   programs, times and conditions, then **Enable automatic**.
3. Allow **Alarms & reminders** in Android settings if requested, return to
   HeySig, and allow notifications if requested. Check the main status and
   dated next switch. Automatic stays Off until these requirements are met.
4. Keep the phone speaker near the aids, media volume at least halfway, Do Not
   Disturb off and external audio disconnected. At future scheduled times the
   phone plays the validated command tones, including while locked. Other people
   may hear them. Program selection uses retained known targets as with manual
   selection; unknown values are not invented.

Automatic starts Off on upgrade. It uses exact-alarm access and a short-lived
media-playback foreground service per occurrence. It does not listen, record
sound, track sleep, power off or mute the aids. Playback completion does not prove
hearing-aid receipt. Physical acceptance remains required, including locked-phone
output, audio routes, permissions and power restrictions.

**Stop automatic routine**, the ongoing notification's **Stop automatic**, and
global **Stop** cancel future actions and request cancellation of active playback.
Emitted tones cannot be undone. Manual program/level changes pause automatic.
Review and start it again deliberately afterward.

An occurrence more than two minutes late, an active command/review, an invalid
routine or denied audio conditions is skipped or blocked. Failed/interrupted
playback is not retried. Check **Last automatic result** and the last program
result before another request. Interrupted claims never replay after process
death or reboot. Recovery replans only future times; interrupted work requires
review. Times follow the phone timezone, skip nonexistent DST times and use the
first repeated occurrence. Changing the saved routine or aid setup disarms
automatic and requires a fresh review.

## Immediate controls and optional reminders

**Sleep now** and **Wake now** ask you to review the target and tap **Switch**.
They use the same executor and preserve the independent saved restoration
snapshot. **Check device audio** checks conditions without playing tones.
The latest Sleep/Wake result persists locally, including an audio blocker.

For reminders asking you to tap, open **Reminder options → Start routine
reminders**. This turns automatic switching Off. Starting automatic turns these
reminders Off. **Test reminder** posts one notification without changing the
schedule or program; it does not prove future timed delivery. **Stop reminders**
cancels reminder alarms. Android may delay inexact reminders; reminders over
15 minutes late are skipped. Reminder notification taps only open this screen.

Voice setup: **configure sleep**, **set sleep time to ten PM**, **set wake time
to seven AM**, **set sleep program to Sleep**, **set wake program to Universal**,
**review sleep routine**, **save sleep routine**. Use your configured names.
Voice Save leaves schedules Off. **Enable sleep schedule** or **start automatic
routine** re-enables a previously reviewed unchanged routine when permissions
are available; first-time automatic review happens on the phone. **Disable sleep
schedule** or **stop automatic routine** disables future actions. Voice drafts
expire after five minutes and remain scoped to their originating device. Cancel
preserves saved choices. No command replays while configuring a routine.

Routine choices, enable flags, revisions, scheduling/claim identifiers and latest
results are local, covered by existing export/deletion controls. Automatic adds
no recognition, network or analytics service. See [the automatic design](architecture/automatic-sleep.md).
