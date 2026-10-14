# Slider acceptance — October 14, 2026

Slider work is complete for occasional use on the configured Pixel 9a. The user clarified that sliders are not heavily used; acceptance covers representative commands, known failing transitions, endpoints, repeated-target no-ops, and reliable restoration. Exhaustive real-device transitions, endurance, other layouts, and native accessibility are not certified by this run.

The final repository Current and Set helpers were imported while preserving their installed task IDs. All three families used the legacy-gesture backend with direct mode, target endpoints 695/2000, and measured thumb-start endpoints 748/1959. Each changed target used one Android gesture request containing a 100 ms activation tap, 500 ms gap, and one continuous 650 ms drag. Unchanged targets sent no gesture.

The captured starting program was Universal; no program switch occurred. All 28 public Tasker operations passed with unique operation IDs, CommandOK=1, empty Error, expected observed values, and independent visible Signia value/header agreement.

| Control | Captured start | Requested targets, in order | Restored |
| --- | ---: | --- | ---: |
| Volume | 10 | 5, 6, 5, 6, 0, 0, 1, 2, 15, 15 | 10 |
| Balance | −4 | −8, −8, −7, −6, 0, −1, 7, 7 | −4 |
| Tinnitus | 0 | 0, 0, 1, 2, 3, 15, 15 | 0 |

The six repeated endpoint requests returned Gesture empty and TouchStage not run. The original saved-settings record was restored atomically and independently read back byte-for-byte. Final program and all three control values matched the captured baseline. See [operation evidence](evidence/HeySig3-slider-acceptance-2026-10-14.json) and [restoration proof](evidence/HeySig3-slider-restoration-2026-10-14.json).

The relevant direct-drag, actual thumb-geometry, and numeric-control repository checks passed. Earlier checks cover invalid targets, unexpected readback, program/control changes, and stopping without an automatic retry. Existing unavailable-tinnitus phone evidence remains separate; this session tested an available program. App readback does not independently prove acoustic hearing-aid receipt, which the user excluded from slider acceptance.

Nine temporary trial/baseline/acceptance tasks were removed after verification. The cleaned phone project was exported to /sdcard/Tasker/projects/HeySig3.prj.xml; a pre-test backup and the tested export were also preserved locally. Canonical source and separate Pixel calibration exports remain in the repository. Sleep/noise automation and the rest of the broader checklist are unchanged by this slider closeout.

Phone export verification sorted actions by their numeric action index: Tasker writes them in lexical XML order. All six helpers retain the exact repository Java bodies. The three Current helpers have a reassigned AutoInput query action code, with the same plugin package, configuration activity, and parameter payload; Set action codes match exactly.
