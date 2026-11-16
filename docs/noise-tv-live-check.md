# TV and noise: bounded room check

November 15, 2026. First target: the owner's built-in TV speakers.

Open **Noise → Check speech and noise → Check TV and noise (10 seconds)**.
Read the microphone explanation and select **Start check**. If Android asks for
microphone permission, choose whether to allow it, then start the check again.
No saved fitting or program assignment is required for this check.

Keep the screen open and phone unlocked near your normal listening position.
The check captures ten seconds and then processes the sound. It stops on leaving
the screen, locking, tapping On to turn it Off, or its two-minute timeout. It
stops other listening/Automatic sessions before starting. It does not restart
them afterward. It has no hearing-aid command dependency.

The result shows two different noise estimates so their disagreement is visible:

- **Current detector**: the existing masking classifier with neural corroboration.
- **Noise-reduction estimate**: the existing retained/residual bands applied
  independently, including when the original classifier abstains.

Each uses three disjoint three-second sections, seconds 0–9. Two matching
sections produce the displayed label; otherwise it says Uncertain. These are
parts of one capture, not the live Automatic confirmation/cooldown sequence.
The entire ten seconds supplies nineteen overlapping TV/event model frames.
TV scores are raw model outputs, not calibrated probabilities. This check does
not claim that a TV has been identified or select a TV program.

Technical details list the three section results, retained/removed levels,
speech score and ratio. Removed sound can contain wanted speech. These are
relative digital measurements, not room SPL, verified target-speaker SNR or a
measurement of the wearer's understanding. The existing fixed model and bands
are unchanged. Phone Automatic and legacy assignments retain their behavior.

All PCM and intermediate copies are memory-only and erased after the capture or
on error/cancellation. The visible result is memory-only and is cleared on
leaving the page; no audio, transcript or history is saved or uploaded. A
developer may explicitly record scalar results for a requested field test;
those results are separate from automatic app history.

For the first field sequence, measure comfortable TV dialogue, then TV off with
quiet room tone, then ordinary live conversation with TV off. Record which
scene was actually present, the displayed results and phone position. Do not
create loud sounds for testing. A TV mixed with nearby conversation needs its
own check because wanted-source context changes the interpretation.

This is a measurement tool to resolve the observed failures, not completion of
automatic TV recognition or a new live switching policy. It adds no acceptance
lock. Broader source-independent TV validation and owner-observed switching
results remain required before claiming the feature works.

Device update procedure: use only a verified, pushed source revision and the
existing development signer. First stop the app and create a readable private
run-as tar backup; verify all members and saved preference hashes. Install with
`--no-incremental -r`, then compare preferences before launching. Never uninstall
or clear data to fix installation, and never send an aid command just to check
the update. A checksum snapshot alone is not a restorable backup.
