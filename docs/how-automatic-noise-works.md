# How Automatic helps

HeySig checks sound around your phone and chooses the hearing-aid program you
saved for that sound level. You choose the programs; HeySig does not change your
hearing-aid fitting.

For everyday instructions, see [Using Automatic](noise-quick-start.md).

## What happens when sound changes

HeySig takes a short sound check, then pauses before checking again. It waits
for several checks to agree before changing programs. After a change, it waits
again. This helps avoid repeated changes when sound briefly gets louder or quieter.

With the usual settings, a sound check takes five seconds, checks are separated
by ten seconds, and three checks need to agree. After a program change, there is
also a one-minute wait. A return to the quiet program can therefore take about a
minute and a half after the last change. Your saved choices may use other times.
The Noise screen shows what it is waiting for.

A sound level with no program chosen keeps the current program. If the chosen
program is already the last one HeySig sent, it does not send it again.

Phone position, nearby voices and music affect the checks. The graph shows
changes measured at the phone. It is not a medical sound-level meter.

## Keeping Automatic on

Turn it on while HeySig is open and the phone is unlocked. Follow the microphone
and notification prompts. Then you can lock the screen or use another app.

A HeySig phone notification shows that Automatic is on and has an **Off** button.
Automatic runs for up to eight hours and uses more battery while running.

Choosing a program yourself, using Therapy, stopping the app or a microphone or
program-playback problem stops Automatic. It does not restart by itself. If
speech or other playback briefly needs the microphone, Automatic pauses and
tries again afterward.

Automatic does not switch into or out of Therapy. Choose a daytime program
after Therapy before turning Automatic on again. Other automatic schedules
must be off before starting Automatic noise changes.

## Optional help

**Try without changing programs** shows which program HeySig would choose
without sending a change to the aids. Keep the app open and unlocked for this.

**Optional sound setup** helps check this phone in six everyday places, from
quietest to loudest. Use comfortable sounds that are already around you. There
is no need to create loud sounds. Your saved choices stay the same until you
review and save changes.

HeySig can also suggest changes from sound checks already taken while the app
is visible. It does not change your saved sound settings unless you choose
**Review learned thresholds**, review the suggestion, and tap **Approve & save**.
These extra controls are on the try-out/details screen. You do not need to use
them to turn Automatic on.

## Microphone and privacy

Noise checks process sound in memory and discard it afterward. This feature
does not save or upload audio recordings. The graph keeps only recent sound-level
numbers in memory. Optional learning also keeps a limited set of numbers in
memory. Optional sound setup saves level numbers and microphone settings locally
only when you save them.

Voice commands are separate. Your installed speech service may use a network;
follow its explanation before using speech.

## If the quiet program does not return

Check the Noise screen. It shows the wait and the program being checked.

Make sure you chose a program for quiet places. If the phone still measures the
sound as belonging to a noisy setting, HeySig follows that saved choice. You can
try the optional sound setup or review the extra sound settings. Do not make
sounds louder to test it.

HeySig reports what program it sent. It cannot read back the actual hearing-aid
program or confirm that the aids received the tones.

Implementation and validation details are kept in the
[service design review](automatic-noise-background-design-2026-11-08.md) and
[validation record](acceptance/automatic-noise-usability-2026-11-08.md).
