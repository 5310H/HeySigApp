# How Automatic noise works

Automatic measures the sound level around your phone and selects the hearing-aid
program you assigned to that level.

## Set up your choices

Open **Noise → 1. Choose programs**. Assign your configured programs to six noise
levels, from **level 1 (quietest)** to **level 6 (loudest)**, then save.

Several levels may use the same program. An unused level causes no change.
Review the five level thresholds under **Advanced measurement settings**.
Calibration is optional; saved thresholds can be used without it.

## Turn Automatic on

Say **“Automatic on”** or tap **Automatic on** on the Noise screen.

Keep HeySig open and the phone unlocked. Review the microphone explanation and
allow microphone access when requested. If Android asks for permission, request
Automatic on again after granting it.

Start from a known program outside Therapy. Other routine schedules must be Off
before starting Automatic noise.

## How it chooses a program

1. **Measure sound.** With default settings, the phone waits ten seconds,
   measures sound for five seconds, then repeats with a ten-second pause between
   measurements. Speech, music and other sounds all contribute to the level.
2. **Find the assigned program.** The measured level is compared with your saved
   thresholds to choose one of the six noise levels and its assigned program.
3. **Wait for agreement.** By default, three consecutive valid measurements must
   point to the same program. Small fluctuations near a threshold are ignored
   to reduce unnecessary switching. Invalid or stale measurements clear that
   agreement.
4. **Check priorities.** Therapy protection, manual control, audio availability
   and cooldown can prevent a change. Unused levels send nothing. If the target
   is already the last requested program, no command is repeated.
5. **Send the change.** HeySig plays the existing program-change tones through
   the phone speaker. Position the phone so the hearing aids can receive them.
6. **Wait before changing again.** Successful playback starts the saved cooldown
   and allows at least ten seconds for sound to settle before fresh measurements.
   Failed or interrupted playback stops Automatic.

The default cooldown is **60 seconds**. An initial cooldown also applies, so the
first eligible change can take about **90 seconds** with default timing. Your
saved settings may use different timings.

## Example

Suppose a louder noise level is assigned to your **Noisy** program. When repeated
measurements qualify for that program and cooldown has finished, HeySig requests
Noisy. When quieter sound consistently qualifies for another assigned program,
it can request that program after cooldown.

This process follows measured loudness and your assignments. Phone position,
nearby speech and music affect the result.

## Turn Automatic off

Say **“Automatic off”** or tap **Automatic off**. This stops microphone capture
and future automatic requests. It cannot undo tones already emitted.

Leaving or locking HeySig, making a manual adjustment, or a microphone/playback
interruption also stops the process. Restart explicitly; it does not silently
resume.

Starting Automatic turns hands-free listening off. Use the visible **Automatic
off** button when voice input is unavailable. After updating the watch, its
Automatic noise controls send the same requests to the phone; this process uses
the phone microphone.

## Therapy protection

Automatic will neither select nor switch away from your protected Therapy
program. Programs named **Therapy**, and existing programs named **Sleep**, are
also protected. Select a program outside Therapy manually before restarting
Automatic.

## Why and when to calibrate

**Calibration is optional. You do not need it to turn Automatic on.** If your
saved thresholds select programs at the sound levels you want, leave them alone.

Calibration helps match the five boundaries between noise levels to readings
from your phone in the places where you use it. The supplied thresholds are
starting values; they were not measured for your particular phone and placement.
The same surroundings can produce different readings with a different phone,
microphone position, or an obstructed microphone.

Consider calibration when:

- Ordinary quiet surroundings repeatedly qualify for a louder level, or louder
  surroundings repeatedly qualify for a quieter level than you intended.
- Program changes consistently happen at the wrong sound levels, after allowing
  enough time for confirmation and cooldown.
- You change phones or consistently change where you place the phone while
  Automatic runs.
- A microphone or system change makes the previous readings behave differently.

Keep phone placement consistent first. Moving it between a table and a pocket
can change readings; one calibration cannot compensate for every placement.

**Calibration will not fix a wrong program assignment, cooldown delay, Therapy
protection, a stopped session, microphone permission, or tones the aids do not
receive.** Check those causes before changing thresholds. Changing which program
a level selects usually needs only an assignment change and Save.

### Automatic learning: fewer manual measurements

While **Automatic is on**, HeySig also learns from the valid sound-level
measurements it already takes. This starts with Automatic; no separate learning
session or extra microphone capture is needed. Your saved thresholds continue
controlling program selection throughout learning.

It keeps up to **240 level numbers in memory**, trimming older measurements when
new ones arrive after an hour. It does not save recordings, transcripts, or a
learning history to disk, and it does not upload learning measurements. Closing
the app process loses this temporary data. **Discard learned measurements**
clears it without changing saved settings; Automatic can start collecting again
with its next valid measurement.

A suggestion requires measurements spanning at least **five minutes**, with at
least **ten valid measurements on each side** of a boundary. The middle portions
of those two measured ranges must be separated enough to allow for the switching
margin. A single steady noise level cannot produce a useful suggestion. You do
not need to visit all six levels; boundaries without enough evidence stay as
saved. Never create louder sounds just to fill a level.

HeySig suggests a midpoint between the central measured ranges and limits each
boundary change to **2 dBFS per review**. Tiny changes below 0.5 dBFS and changes
that would invalidate threshold spacing are omitted or rejected. This is a
conservative suggestion based on observed loudness, not proof of where you want
a program change. Phone placement, speech and music still affect the result.

When the Noise screen says **Threshold suggestion ready**, choose **Review
learned thresholds**. The review shows all five old and proposed boundaries and
the number of measurements per level. Opening a ready review stops Automatic.
Choose **Keep current** to retain your thresholds, or **Approve & save** to save
the proposal. No threshold changes without that approval. Program assignments,
hearing-aid fitting and other timing settings remain unchanged.

The review expires after one minute. Changed settings, fitting, microphone or
measurements require a new review. Learning resets when saved noise settings or
fitting change; a different microphone configuration starts fresh learning.
Changing thresholds clears any guided calibration proof tied to the old values.
Approved suggestions are not marked as guided calibration proof. Automatic
stays Off after approval; request **Automatic on** to use the saved changes.

### What calibration does

The guided **Learn sound levels** flow takes three explicitly requested
measurements at each of six progressively louder everyday sound levels: eighteen
measurements in total. It suggests a boundary halfway between each pair of
adjacent measured ranges. Those ranges must be far enough apart to allow for the
margin that prevents switching back and forth near a boundary.

If the ranges overlap or are too close, calibration refuses to suggest
thresholds. Use comfortable, naturally occurring sounds; do not create louder
sound just to complete calibration. You can continue using your saved thresholds
without completing it.

Review the suggested thresholds, choose **Apply to draft**, and then **Save noise
settings** to use them. Applying alone does not replace saved settings. Saving
leaves Automatic Off; request **Automatic on** when ready.

Calibration adjusts noise-level thresholds only. It does not change the fitting,
volume, or processing settings inside a hearing-aid program, and it does not
teach HeySig to recognize particular kinds of sounds.

## What the measurements mean

The displayed level is relative microphone **dBFS**, not calibrated sound-pressure
decibels or a hearing-safety measurement. Calibration can help match thresholds
to the phone and its placement, but it is optional.

Noise audio is processed in memory and discarded by this feature. Optional
calibration saves scalar measurements and capture settings, not recordings.
Voice-command recognition is separate and its provider may use a network.

Completed playback confirms that the phone sent tones. HeySig has no electronic
readback confirming that the hearing aids received or applied the change.
