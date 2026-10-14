# Phone reliability checks: October 14, 2026

## Full reboot persistence: passed

The Pixel 9a was unplugged at 100% before testing. A read-only Tasker helper (`python3 tools/build_sig3_reliability_read.py`) captured durable noise/voice/sleep preferences, relevant globals and the SHA-256 of the private settings record. After an actual phone reboot, manual unlock and reconnection on the new wireless-debugging port, the same helper captured them again.

All captured values matched. Noise configuration retained the measured −54.5 / −43 boundaries, 1.5 margin, three confirmations and 60-second cooldown. Noise sampling, diagnostic logging, sleep automation and continuous wake listening stayed off. Spoken results and prompts remained on. The private settings hash remained `a604e86648ff071b79e3e800a660b2479783287b99e82c3b85772053b4cf9f89`. The empty sleep preference store remained empty; this does not accept persistence of a newly configured sleep schedule. Uptime fell from 786,102,936 ms to 210,814 ms, confirming a phone reboot rather than an application restart.

This accepts saved-value persistence; it does not claim automatic startup, post-reboot wake recognition or an unattended sleep transition.

## Eight-hour observation trial: running; result pending

The user selected eight hours unplugged and mostly idle. The trial samples in **observation mode only**. Continuous Hey Sig listening stays off to avoid microphone contention. The test uses the production polling task with every production action retained; only its optional numeric logger adds a test-only aggregate summary and an automatic stop at the first polling tick after the deadline. No boot profile is installed.

Build the temporary exports with `python3 tools/build_sig3_endurance_trial.py`. Import the instrumented `SIG3_NoisePoll` and the three temporary start/stop/smoke tasks. Run `00_SIG3_EnduranceSmoke` first; it invokes the same start task with a 60-second duration. The on-phone smoke run passed five fresh samples with no failures, a maximum 15.917-second sample gap and an automatic stop 9.212 seconds after the requested one-minute deadline. After the smoke run completes, run `00_SIG3_EnduranceStart` without a parameter for eight hours. The recorder uses elapsed uptime for the stop deadline, so a wall-clock adjustment cannot shorten the run. Android suspension can delay the next tick; maximum tick gap and stop delay are recorded rather than assuming exact scheduling. A summary recording failure stops sampling and diagnostics. Explicit early stopping is available through `00_SIG3_EnduranceStop` or the public `SIG3_NoiseStop`.

The bounded summary is `/sdcard/Tasker/sig3-endurance-summary.json`. It retains first/last battery readings, elapsed uptime, fresh/error/skip counts, locked-screen counts, charging counts and the largest gap between fresh sample timestamps. The separate numeric log can roll over without losing aggregate counts. No audio is recorded. The previous summary is retained as one `.previous` file when another run starts.

After completion, inspect status, elapsed duration, sample gaps and failed/skipped counts before accepting endurance. Charging invalidates an unplugged battery comparison. Battery percentage and charge-counter changes describe total phone use during this instrumented run, including Android, wireless debugging and diagnostics; they do not isolate the cost of noise sampling. A separate comparable idle period is needed for that attribution. Unavailable charge counters must not be interpreted as measured capacity.

Collect the summary and reimport the release `tasks/SIG3_NoisePoll.tsk.xml` to remove the temporary instrumentation. Remove the temporary tasks after final evidence collection. Leave sampling and diagnostics off afterward. Do not overwrite the instrumented polling task during an active trial, because that removes its automatic stop.

Endurance and battery results remain pending until the full timed run is collected.

The full trial started October 14 at 7:21 p.m. EDT, with its deadline October 15 at 3:21 a.m. EDT (automatic stopping occurs at the next available polling tick). Initial screen-off samples passed, with no capture errors or charging detected. Final acceptance requires collecting the completed summary after that deadline. The temporary diagnostic polling task remains installed until collection and release-export restoration.
