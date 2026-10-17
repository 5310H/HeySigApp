# Six-level noise setup

Open **Noise**. Assign configured programs from **level 1 (quietest)** through
**level 6 (loudest)**. Several levels may use the same program. Unused levels
make no proposal and clear confirmation. Noise order is independent of program
slot numbers. Choose your **Protected Sleep program** if you have one (programs named Sleep
are also protected): automatic
preview never enters or leaves it. Ordinary Observe can still describe it.

## Guided calibration

The default thresholds are illustrative. Calibrate on the device that will do
the sampling; a watch requests measurements from the phone, not its own mic.

1. Review the assignments and capture settings, then choose **Start new calibration**.
2. Place this device consistently in a naturally occurring, comfortable quiet
   environment. Choose **Capture next calibration sample** three times.
3. Follow the displayed progress through levels 2–6, using progressively louder
   everyday environments. Every capture is explicitly requested, waits at least
   ten seconds for settling, and takes five seconds by default.
4. Choose **Review calibration thresholds**. Review the 18 relative index values
   and five suggested thresholds, then **Apply to draft**.
5. Review assignments and choose **Save noise settings**. Saving leaves noise Off.

Do not generate loud sounds or increase sound to unsafe levels to obtain six
levels. If their measured ranges cannot be separated by the hysteresis margin,
calibration refuses to create thresholds. Recheck placement/environments or use
Sample/Observe; do not force a successful calibration. This is microphone-index
calibration, not clinical fitting, hearing safety assessment or SPL calibration.

Each level requires three valid samples. Thresholds lie halfway between the
adjacent measured ranges; ranges must be separated by more than twice the
hysteresis margin. Full measured ranges, rather than just averages, must remain
apart. Cancel clears unfinished samples without changing saved settings.
Unfinished samples stay in memory and are lost if Android ends the process.
Apply changes only the draft; Discard restores the saved configuration and proof.

Saved calibration contains 18 numbers, microphone/capture configuration and no
audio. Changing the microphone/source, system build, capture duration, margin
or thresholds invalidates that proof. Assignment or fitting edits still require
review and Save. Older settings load without calibration and remain usable in
Observe. Missing, corrupt or mismatched proof cannot unlock automatic behavior.

## Sample, Observe and Automatic preview

**Sample now** first explains microphone/data use, then requests permission if
needed. After allowing it, select the action again. Calibration uses that same
explanation and bounded sampler. No capture begins just by opening Noise.

Measurements use this device's built-in microphone. RMS index is relative dBFS,
not sound-pressure dB or background noise alone. Clipped, all-zero, short or
strongly DC-offset samples are rejected. Speech and music affect measurements.
Audio is processed in memory, discarded and not recorded/uploaded by this feature.

After saving assignments, **Start Observe** shows level, target and consecutive
confirmations. A confirmed target says **Would select … No command sent**.
Large jumps go directly to the qualifying level; repeated assignments share
confirmation. Stale/rejected samples clear confirmation. Observe never plays
aid commands and does not require calibration proof.

After measured calibration is saved, **Preview Automatic** simulates the guarded
loop without sending commands or changing remembered aid settings. It checks
foreground/unlocked/audio priority, Sleep, consecutive confirmation, repeated
target suppression and cooldown. A simulated selection starts simulated cooldown;
new confirmations begin after cooldown. The initial simulated baseline is the
last requested program, which is not proof of actual aid state.

Stop, voice recognition, manual requests (including repeats), editing an active
draft, microphone errors and leaving the activity stop the session. Restart
explicitly. Permission revocation/capture interruption does not silently resume.
Installed speech and watch replies can affect nearby sound; avoid speaking near
the sampler. Background/locked-screen noise operation is not implemented.

## Automatic release lock

The automatic decision reducer and connection to the existing validated command
executor are implemented. The source-controlled `NoiseReleaseGate` remains false:
**real Automatic switching is unavailable pending physical acceptance**. A voice
request reports the lock without starting capture. There is no preference or
voice bypass. A successful preview or APK build is not acceptance.

The guarded path requires measured calibration and reviewed saved settings;
respects manual/Sleep priority; suppresses duplicate requests; waits for durable
playback results; clears confirmation on interruption; applies cooldown only
after success; and halts after failure. A real Automatic start also waits one initial cooldown;
restarts in the same session retain the last successful-switch time. It reuses the single existing executor,
never a separate player. It does not overwrite the manually saved restoration
record. After acoustic playback it waits at least ten seconds before sampling
again. Successful playback still does not establish aid reception.

## Existing voice commands

- “Configure noise” or “read noise settings.”
- “Assign program three to noise level five.”
- “Leave noise level four unused.”
- “Set noise threshold one to minus sixty.” Thresholds are numbered 1–5.
- “Set noise margin to two” or “set noise confirmations to three.”
- “Set noise capture to five,” “set noise wait to ten,” “set noise freshness to thirty,” or “set noise cooldown to sixty.”
- “Save noise settings” or “discard noise changes.”
- “Noise sample,” “observe noise,” “noise status,” or “stop noise.”

Calibration review, protected Sleep selection and Automatic preview use the phone
controls. Voice changes remain drafts until Save. The development relay allows
editor/Observe vocabulary while automatic aid playback remains blocked in
status-only mode. The release companion still requires foreground setup and
permission/disclosure on the phone.
