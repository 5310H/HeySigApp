# Selectable noise settings: October 14 acceptance

Implemented selectable Quiet/Noisy/Very noisy roles, editable noise-index boundaries, hysteresis, consecutive confirmation, cooldown, observation mode, guarded polling and manual-program priority. The [setup guide](https://github.com/5310H/HeySig/blob/HeySid3/docs/HeySig3-noise-settings.md) describes the controls.

## Phone checks that passed

- The Pixel 9a opened the settings dialog with all three program selectors and tuning fields.
- Temporary role selections and boundaries saved successfully. Direction confirmation and a 60-second cooldown were stored; Save left switching disabled.
- A timed-out earlier dialog left saved configuration empty.
- Observation-only processing proposed the configured Very noisy role after three synthetic samples, without dispatching a program selection.
- Restoration cleared the temporary configuration and restored all previous noise settings. Fresh Program/Volume/Balance/Tinnitus reads matched the starting Universal / 10 / 0 / 0. The exact private saved-settings record matched its starting SHA-256.

The boundaries −60/−40 in the controlled harness were temporary synthetic-test choices; the later measured choices below supersede them.

## Repository checks

The actual BeanShell decision and persistence helpers passed hysteresis, direct role jumps, invalid/gapped/source-changed samples, override/cancel/sleep suppression, cooldown, successful/no-op/failed completion, atomic save, restart reload and changed program mapping. All four helpers parsed with BeanShell.

All 27 regression scripts passed after the final logging, locked-state guard and status-message changes. XML validation checked 101 files and 7,115 actions, with zero failures.

## Controlled automatic selection and measured environment checks

A corrected, isolated phone harness passed seven phases: configuration, observation without dispatch, foreground deferral with pending-state reset, Universal → Noisy, manual-override suppression, resumed Noisy → Very Noisy, and manual selection back to Noisy with override enabled. All transitions used successful command results and fresh Signia readback. The earlier incomplete harness result above is superseded by this complete run.

Fixed-position real polling collected seven quiet readings (−64 to −62; median −63), thirteen normal TV/music readings (−54 to −44; median −46), and nine louder TV/music readings (−41 to −38; median −40). Each selected snapshot precedes the request to change sound; the first fifteen seconds after a stage marker are excluded. The resulting phone-index boundaries are **−54.5 / −43**, with **1.5** hysteresis, **three** confirmations and **60 seconds** cooldown. These are uncalibrated index values, not sound-pressure dB.

Tasker-hidden observation delivered 47 launcher records over **732.095 seconds**, with no command errors. Two fresh locked-screen observation records also passed. This is bounded endurance evidence; overnight operation, full reboot, battery impact and real simultaneous microphone use remain unverified. The automatic guard now explicitly denies changes while the phone is locked, even if foreground deferral is disabled; executable locked-state cases pass.

The measured-setting full automatic polling trial passed: readings −46/−46/−49 selected Noisy, −39/−35/−35 selected Very Noisy, −48/−49/−47 returned to Noisy, and −65/−65/−67 returned to Universal. A repeated Very Noisy selection was a no-op. The stage label remained unchanged during some transitions; acceptance uses the numeric readings and successful program readback, rather than that label.

Four polling iterations skipped capture while a temporary voice-busy flag was set; clearing it resumed a fresh sample and restarted confirmation after the gap. This checks the actual polling gate, not concurrent live speech recognition.

Automation and diagnostics were stopped. Restoration passed for Universal / volume 10 / balance 0 / tinnitus 0 and the exact original private saved-settings record. The new measured noise choices are intentionally retained with switching off.

[Evidence JSON](evidence/HeySig3-noise-settings-2026-10-14.json) records selected phone results without the private saved record.
