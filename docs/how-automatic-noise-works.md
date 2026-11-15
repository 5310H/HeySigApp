# How Noise Automatic should work

Updated November 15, 2026. This describes current speech-aware Automatic and
what to check in real use. Software tests have passed; reliable performance in
every environment and actual hearing-aid reception have not been established.

Automatic should choose your saved program when listening conditions change,
then return when conditions improve. You choose the program assigned to each
role. HeySig sends program-change tones; it does not change the fitting or add
new noise reduction to your aids.

## Set up a session

1. On **Home**, choose your usual daytime program.
2. Open **Noise → Choose programs**. Assign your own programs to **Universal**,
   **Noisy**, and **Very Noisy**. An unassigned role keeps the current program.
3. For a TV session, also assign your TV program and turn **Watching TV** on.
   For other listening, leave Watching TV off. Save your choices.
4. Return to Noise and tap **Off** to turn Automatic **On**. Follow its microphone
   explanation and permission prompts. Keep the phone where the aids can hear
   the program-change tones. The screen may be locked afterward.

The phone microphone is the default. If you explicitly choose watch input,
follow [Watch Automatic setup](watch-automatic.md); the phone still sends the
program-change tones. Microphone position matters: sound at a phone in a pocket
or at your wrist can differ from sound at your ears.

No calibration, enrollment or learning period is required. Saved six-level
assignments and optional level calibration remain available for older measurement
tools; adjusting them does not retrain normal speech-aware Automatic.

## Which program should be selected?

The decision estimates how much background sound masks speech. A place being
loud, or being called a restaurant, does not by itself select a program.

| Repeated sound evidence | Watching TV Off | Watching TV On |
| --- | --- | --- |
| Quiet | Universal | Universal |
| Speech stands out clearly | Universal | TV |
| Speech partly masked | Noisy | Noisy |
| Speech heavily masked | Very Noisy | Very Noisy |
| Not sure, conflicting estimates or unusable capture | Keep current program | Keep current program |

These are **your saved role assignments**, not guaranteed program names or slot
numbers. Check the assignments if the program considered does not match what
you intended. If the correct target is already the last program requested,
Automatic should not send it repeatedly.

**Watching TV must never override a confirmed Noisy or Very Noisy decision.**
The November 15 correction removes that earlier routing problem.

Watching TV is your listening choice, not automatic recognition of a television.
It stays selected until you turn it off and save. If you leave it on after a TV
session, later clear speech can still select TV. To change context, turn Automatic
Off, change Watching TV in Choose programs, save, then turn Automatic On again.

## How long should a change take?

Automatic should respond to sustained changes rather than every brief sound.

- Each phone capture lasts **three seconds**, followed by your saved pause of
  **at least ten seconds**, plus processing time.
- Entry to Noisy, Very Noisy or TV uses your saved confirmation count, with a
  minimum of two checks. The default is **three**.
- Return to Universal uses **two fresh checks agreeing on Universal**. With
  Watching TV off, quiet and clear speech may jointly confirm that return.
  With Watching TV on, clear speech means TV and quiet means Universal, so
  alternating those results does not confirm either program.
- After successful program playback, the saved cooldown applies in both
  directions. Its default is **60 seconds**. Checks during cooldown do not
  count toward the next change; fresh agreement is required afterward.
- Uncertain, stale or interrupted checks clear confirmation progress.

With a three-second capture and ten-second pause, completed checks are roughly
13 seconds apart **before adding processing and scheduling time**. After the
first agreeing completed check, a two-check decision needs about one more cycle;
a three-check decision needs about two more. The initial capture, cooldown,
playback and interruptions add time. Your saved timings may differ.

A change can therefore take tens of seconds, and return shortly after a previous
switch can take over a minute. This is not an exact deadline. Check the displayed
confirmation count and cooldown rather than expecting an immediate switch.

## What should happen when noise ends?

With Watching TV off, sustained quiet or clear speech should return to Universal
once cooldown and fresh confirmation requirements are satisfied.

With Watching TV on, sustained clear speech should return to TV; sustained quiet
should return to Universal. TV can be selected again if clear speech resumes.

There is no rule that turns an uncertain result into quiet after a timeout.
Music, competing voices, handling noise, clipping and ordinary background sounds
can produce uncertain checks. Pure noise without reliable speech evidence can
also hold the current program. **Very Noisy is not a simple loudness threshold.**
A long hold may be a detector limitation; it is not evidence that Automatic
worked correctly. If an ordinary quiet room remains unrecognized after cooldown
and several fresh checks, record that as a missed return for investigation.

## What the status means

| Status | What to expect |
| --- | --- |
| Checking again: 1 of 3 | More fresh agreement is needed; no change yet. |
| Waiting for cooldown | The next change is being held. Confirmation starts again afterward. |
| Not sure / estimates disagree | Keep current program; confirmation progress clears. |
| No program chosen | No change for that role until you explicitly assign one. |
| Current program already matches | No duplicate tones are needed. |
| Sending / request in progress | The shared executor is handling one command. |
| Sent / last program requested | Playback completed. Hearing-aid receipt and improved listening are not confirmed. |
| Paused for speech or other audio | Capture waits, then needs fresh checks. |
| Automatic Off / error | Future Automatic requests have stopped; review the reason before restarting. |

The graph shows relative microphone level, not calibrated sound-pressure level,
speech understanding or the actual hearing-aid program. A falling graph alone
does not prove that quiet-return evidence has been recognized.

## Stop, manual control and Therapy

Tap the green **On** button to stop, or use **Off** in the Automatic notification.
Off stops capture and future Automatic requests and cancels owned playback;
tones already emitted cannot be undone.

Manual program/control commands take priority. Therapy stops noise Automatic;
Automatic must not switch into or out of a protected Therapy program. A Therapy
timer can later send the saved Wake program, but it does not restart noise
Automatic. After Therapy, choose a daytime program and explicitly start
Automatic when you want it again.

Sessions end after eight hours. Removed microphone/notification access, changed
fitting or saved choices, app stop, microphone/model failure, and failed or
interrupted program playback also stop the session. It does not restart itself
or queue a retry from old evidence.

## A practical field check

Use ordinary sounds already around you; there is no need to create very loud
sounds. Check saved assignments before testing and allow the displayed cooldown
and confirmation process to finish. The table describes expected outcomes when
the detector consistently supplies the corresponding evidence.

| Session | Sequence to check | Expected requested programs |
| --- | --- | --- |
| Normal listening, Watching TV Off | Quiet → partly masked speech → heavily masked speech → quiet | Universal → Noisy → Very Noisy → Universal |
| TV listening, Watching TV On | Clear TV speech → heavily masked speech → clear TV speech → quiet | TV → Very Noisy → TV → Universal |
| Either session | Brief sound or uncertain evidence | Hold; no switch from that check alone. |
| Either session | Select a program manually | Manual request wins; Automatic stops. |
| Therapy | Start Therapy, including timed Therapy | Noise Automatic stops and stays Off after timed Wake. |

For each wrong switch, missed switch or missed return, record:

- Build revision, phone/watch microphone source, Watching TV setting and saved roles.
- Actual listening situation and the program you expected.
- Displayed sound/masking result, program considered, confirmation/cooldown status
  and last program requested. The latest completed-check diagnostics can help.
- Whether the app reported playback, whether you confirmed the aid changed,
  and whether listening improved. Record these separately.

A single correct switch is useful evidence, not proof across environments.
The detector cannot identify the speaker you want, measure your understanding,
or guarantee that another program will help. If a decision is wrong, you can
select a program manually; that stops Automatic.

## Audio and privacy

Noise Automatic processes short captures locally in memory and discards the
waveform. This feature does not save or upload recordings or transcripts. Its
recent graph is bounded and memory-only; role choices and consent are saved
locally. Watch input transfers compact estimates/control/status over the existing
Wear OS Data Layer, not raw monitoring audio. Voice commands use a separate
recognition provider and disclosure and may involve a network.

See [setup details](speech-aware-automatic.md),
[the current service/data-flow review](speech-automatic-background-design-2026-11-09.md),
and [the November 15 correction and validation record](acceptance/automatic-and-therapy-timer-2026-11-15.md).
