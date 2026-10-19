# Fitting-only migration review — October 18, 2026

The owner agreed to preparing the existing development fitting for review in the
separate direct-watch phone candidate. This does not authorize a hearing-aid
playback test or imply migration of all settings.

## Import UI correction

Physical use of the existing import flow exposed two problems: the release
connection's resume notice replaced the import review message after returning
from the document picker, and the setup form still held its pre-import values.
The imported fitting existed only in the shared workflow draft. Its separate
voice confirmation was easy to confuse with the unchanged form's Save button.

The fitting importer now shows a scrollable review dialog with explicit Save
reviewed fitting and Cancel actions. Save goes through the existing reviewed,
origin-bound, expiring shared fitting workflow, then rebuilds the form from the
saved fitting. Cancel discards the draft. Returning to the foreground refreshes
the watch connection card without overwriting the foreground feature's result.
No command playback or automatic retry is added.

## Data handling

A read-only ADB extraction from the original development app prepared a minimal
local export containing only fitting addresses, program names and tinnitus
capabilities. Requested control values, saved restoration record, per-program
targets, calibration, phrases, history and relay secrets were excluded. A private
host copy has owner-only permissions. The temporary phone copy was placed in
local Downloads and selected through Android's document picker, without choosing
a cloud provider. The temporary phone copy was removed after owner review/save.

The original app's entire core preferences matched a before/after SHA-256 check;
no private values or digests are included here. Original data must remain in its
existing installation. The new candidate must not claim an active requested
program/level merely because its fitting is imported.

## Validation

Phone development/release APK builds and shared regression task passed. The
separate phone candidate updated in place successfully. Selecting the prepared
local file through Downloads now shows the scrollable Review imported fitting
dialog with explicit Save reviewed fitting and Cancel controls; returning from
the picker no longer overwrites the review. Owner reviewed and authorized saving;
fresh Home inspection confirmed “Aid setup saved. Nothing sent.” An attempted
tap after the screen changed opened the optional speech disclosure; it was
dismissed without recognition or access. No aid or microphone command has been
used for this migration. See [watch completion checks](watch-completion-2026-10-18.md)
for current candidate fingerprints and the remaining physical gates.
