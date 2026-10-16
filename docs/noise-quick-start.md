# Six-level noise setup — development build 0.9

Open **Noise** in the Chromebook Android app. Assign a configured program to
each noise level, from **1 (quietest)** to **6 (loudest)**. Noise level order is
independent of hearing-aid program numbers. Several levels may use the same
program. An **Unused** level makes no proposal and clears the pending candidate.

Review five increasing thresholds and the hysteresis margin. The displayed
initial thresholds are illustrative; they have not been measured for your
microphone. Each adjacent threshold gap must exceed twice the margin.
Confirmations require consecutive usable samples for the same target program.
Capture, wait and freshness are seconds; freshness must exceed capture + wait.
Cooldown is saved for future Automatic mode and has no effect in Observe.

Press **Save noise settings**. Saving leaves noise Off. **Sample now** first
explains microphone use and data handling, then requests microphone permission
if necessary; after allowing it, press Sample again.
Samples use this device's built-in microphone, including when this device is
a Chromebook. The watch sends commands to that sampler; it does not measure
environmental noise itself.

Capture begins after an initial settling wait of at least ten seconds. A sample
then takes five seconds by default. Its RMS index is a relative digital level
in dBFS, not sound-pressure dB or a measurement of background noise alone.
Clipped, all-zero, short or strongly DC-offset captures are rejected. Speech,
music and other sounds can all affect the measurement. No audio recordings
are stored.

After saving at least one program assignment, press **Start Observe**. Review
the measured index, classified noise level, target program and confirmation
count on Noise. A confirmed target says **Would select … No command sent**.
Large noise jumps go directly to the qualifying level without intermediate
program proposals. Repeated assignments share confirmation counts. Stale or
interrupted samples clear confirmation. Stop, voice recognition, manual
playback and leaving the activity stop observation; explicitly restart it.

Automatic switching and a guided calibration wizard are not implemented.
Observe does not change hearing-aid programs or remembered aid settings.
Program fitting changes require reviewing and saving the noise setup again.

## Voice commands

Phone/Chromebook and watch use the same noise commands:

- “Configure noise” or “read noise settings.”
- “Assign program three to noise level five.”
- “Leave noise level four unused.”
- “Set noise threshold one to minus sixty.” Thresholds are numbered one to five.
- “Set noise margin to two” or “set noise confirmations to three.”
- “Set noise capture to five,” “set noise wait to ten,” “set noise freshness to thirty,” or “set noise cooldown to sixty.”
- “Save noise settings” or “discard noise changes.”
- “Noise sample,” “observe noise,” “noise status,” or “stop noise.”

Assignments currently accept program slot numbers; use Setup to inspect their
names. Voice edits update a draft; Save validates and commits the whole draft.
The watch development relay allows this editor and Observe vocabulary while
keeping aid controls blocked in status-only mode. Keep the Chromebook app open
with Watch Wi-Fi enabled. Microphone permission must be allowed on Chromebook.
Live phone/watch microphone and speech acceptance remains separate from builds
and offline checks. Avoid speaking near the sampler during observation;
watch replies are audible environmental input and are not automatically
excluded after the initial settling wait.
