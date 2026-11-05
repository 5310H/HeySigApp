# Automatic noise-level program switching

Open **Noise → 1. Choose programs**, assign existing programs from level 1
(quietest) through level 6 (loudest), review the five thresholds under
**Advanced measurement settings**, and Save. Several levels may share a program;
unused levels send nothing. Existing saved assignments and fitting are retained.
Calibration is optional. Thresholds measure relative microphone dBFS, not sound
pressure or a clinical hearing setting. Speech and music also affect the level.

Say **Automatic on** or tap the matching Noise button. On starts repeated bounded
phone microphone captures and requests the assigned program once the level has
qualified. It no longer changes an unrelated environment-classifier preference.
The main screen has no separate Sample or environment-monitoring controls.

Say **Automatic off** or tap Off to stop capture and future automatic requests.
The watch provides the same commands through **More → Automatic noise**, using
the phone microphone. Review microphone use and grant access on the phone first.
Starting noise capture turns hands-free listening off; the visible Off button
remains available when speech is unavailable.

Keep HeySig visible and unlocked. Leaving/locking, microphone interruption,
manual commands, playback failure and Stop end the loop; restart explicitly.
On explicitly resumes noise control after a manual waking program selection.
Therapy remains protected: Automatic neither enters nor leaves the protected
Therapy program. Other schedules must be Off before starting noise control.

Default timing uses five-second captures after at least ten seconds of waiting,
three consecutive confirmations and sixty-second cooldown. The initial cooldown
means the first eligible change can take about ninety seconds. Large jumps can
go directly to the qualifying level. Hysteresis reduces boundary chatter;
rejected/stale samples clear confirmation. Repeated targets send no command.
Successful playback starts cooldown and at least ten seconds of microphone
settling; a failed result halts switching. Off cannot undo tones already emitted.

There is **no acceptance lock or trial mode**, and no fifteen-minute trial expiry.
Calibration proof and physical acceptance are not runtime requirements. Enabling
this behavior does not establish classification accuracy, aid reception or Play
approval. Settings never imply electronic aid readback.

Optional **Learn sound levels** records three explicitly requested scalar samples
at each of six increasing everyday levels. Use comfortable naturally occurring
sounds. Do not generate unsafe noise. Review/apply measured thresholds and Save;
Save leaves Automatic Off. Audio stays in memory and is discarded by this feature;
calibration saves only scalar measurements and capture configuration. Speech
recognition is separate and its provider may use a network.

**Preview suggested changes** exercises the same decisions without aid commands;
it uses saved thresholds, with optional calibration. Simulated cooldown and
baseline do not change remembered aid settings.

Voice setup commands remain available: “configure noise”, “read noise settings”,
“assign program three to noise level five”, “leave noise level four unused”,
“set noise threshold one to minus sixty”, “set noise confirmations to three”,
“set noise cooldown to sixty”, “save noise settings”, “discard noise changes”,
“preview automatic noise”, and “noise status”. Calibration voice commands remain
available for start/confirm, capture, status, review/apply, and cancel.

## Automatic threshold suggestions

Automatic now learns from its existing valid level measurements without extra
capture. Saved thresholds remain active until you choose **Review learned
thresholds → Approve & save**. A ready review stops Automatic, shows old/new
boundaries and sample counts, and expires after one minute. Each boundary needs
ten samples in both adjacent levels, at least five minutes of overall coverage,
and separated central ranges; changes are capped at 2 dBFS. Uncovered boundaries
stay unchanged. Temporary learning holds up to 240 scalar numbers in memory,
not audio or transcripts, and can be discarded. It is not uploaded or stored as
a history. Approval leaves Automatic Off. See
[the explanation](how-automatic-noise-works.md#automatic-learning-fewer-manual-measurements).
