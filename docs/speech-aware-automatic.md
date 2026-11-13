# Speech-aware Automatic

The default uses the phone microphone. For local watch-microphone analysis, see
[Watch Automatic setup](watch-automatic.md); your saved program roles are reused.

1. On Home, choose your usual daytime program.
2. Open **Noise → Choose programs**. Choose your own program for:
   - **Universal** — quiet or speech that stands out.
   - **Noisy** — speech partly masked by other sound.
   - **Very Noisy** — speech heavily masked by other sound.
3. Tap **Save speech program choices**, then tap the red **Off** button to turn Automatic on. Read the microphone
   explanation and allow the requested microphone/notification access.
4. Keep the phone where the hearing aids can hear its program-change tones.
   You can lock the screen or use another app.

You can also save by turning Automatic on from the program-choice screen. There are no setup recordings,
calibration steps or waiting for learning. A choice left at **Keep current
program** never sends a change for that situation. Existing six sound-level
assignments and fitting are retained. Initial speech choices reuse levels 1, 4
and 6, but can be changed independently.

HeySig waits for matching fresh checks before changing either way. Your saved
cooldown also applies before returning to Universal. During this wait the screen
shows what is happening. It does not change programs just because a place sounds
like a restaurant or a crowd.

Automatic also uses a local neural noise-reduction analysis to help judge speech against the
background. It compares the original sound with an internal estimate of what
remains after background reduction. Both speech estimates must agree before
Automatic can change. If they disagree, it keeps the current program. This helps
choose a program; it does not
play cleaned sound through the phone or add noise cancellation to your aids.
There is no noise-profile setup or learning period.

Tap the green **On** button to stop, or swipe down and tap **Off** in the HeySig
notification. Choosing a program yourself stops Automatic. Therapy remains under
your control. Sessions end after eight hours and do not restart by themselves.

This is an experimental masking detector. It can confuse partly and heavily
masked speech. It cannot know which person you want to hear, distinguish all TV
voices from nearby voices, or measure how well you understand words. Uncertain
results keep the current program. Development evaluation is recorded separately;
live enablement is not evidence of accuracy or hearing-aid reception.

Audio runs through the existing phone microphone and local model in short
three-second captures, followed by a pause of at least ten seconds. Captured
audio is discarded after processing. No listening recordings, transcripts or
sound history are saved or uploaded. Choices and consent are saved locally.
The graph keeps at most 120 sound-level points in memory for the current session.
Voice-command speech recognition is separate and retains its own disclosure.

## Watching TV

For a TV session, open **Noise → Choose programs**, assign **TV** to your existing
hearing-aid TV program, turn **Watching TV** on and save. The owner's confirmed
TV program is slot 5; other fittings must use their actual TV slot. No TV slot
is guessed or assigned automatically.

With Watching TV selected, repeated clear, partly masked or heavily masked
speech evidence selects TV instead of Universal, Noisy or Very Noisy. Repeated
quiet evidence returns to your saved Universal choice. Clear speech does not
trigger a return during this TV session. Uncertain checks—including music-only
passages—hold the current program, so TV selection may wait for speech evidence.
The existing confirmation count and cooldown apply in both directions. Leaving
Universal unassigned holds TV during quiet instead of sending a return.

This is your explicit listening choice, **not automatic television recognition**.
While Watching TV remains selected, speech after quiet can select TV again,
including nearby conversation after the television has been turned off. To resume
normal speech-aware choices, turn Automatic Off, turn Watching TV off, save, then
turn Automatic On again. Your normal three role choices remain saved. Manual
program controls, Therapy, notification Off and all existing session stops retain
priority. The same behavior applies when the selected watch supplies estimates;
there is no new capture, inference model or audio transfer.
