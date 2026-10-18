# Guided setup and reviewed phrases — October 18, 2026

Source: development working tree based on commit 43658e1. Tester: Codex on the
ARM development host. No physical device/OS acceptance is claimed by this record.

Shared FittingWorkflow provides configure/review/save/cancel aid setup and Help.
Addresses start unknown for a new fitting; existing settings are copied to a
draft. Address, name, slot and tinnitus edits require validation and a subsequent
review before saving. Drafts expire after five minutes, bind to their originating
conversation and reject changes to the underlying fitting. Other control input
while a draft is open does not execute. Global Stop cancels pending drafts.

Phone and forwarded watch commands share this workflow; the existing hands-free
route also handles it. Prompts and responses remain independently optional and
status remains visible. Fitting import reads only a bounded HeySig local export,
never exported commands or snapshots; all changes are staged for review.

Saving changed setup atomically archives the exact prior fitting/requested/saved
snapshot/per-program JSON strings before clearing active assumptions. Up to 20
archives are retained without overwriting prior evidence; further changes block
until the archive situation is reviewed. Archives are in local app-data export
and full local deletion. No automatic archive restoration is introduced.
Dependent noise/sleep choices and learned phrases require review after setup
changes. None of these operations plays aid commands.

Program aliases use the existing reviewed personal-phrase mechanism. The new
conversational form is `when I say movie time I mean program TV`, followed by
`remember that correction`. No inferred correction or automatic replay occurs.
Active phrases have a separate optional JSON export. Import validates every
mapping and displays all proposed mappings before explicit confirmation. Duplicate,
reserved, unsupported or excessive entries reject the whole draft. Import is
origin-bound, expires after five minutes and commits all mappings in one write.
General settings exports continue to exclude phrases. Disclosures now describe
archives, separate phrase exports and chosen-provider handling.

Validation:

- tools/check-manual.sh passed guided draft/review/fresh setup, origin, timeout,
  changed fitting, storage failure and conversational/import safety checks.
- Full Gradle shared tests (11 JUnit methods, including the embedded shared safety
  checks), phone/Wear debug APKs, phone instrumentation APK and both release Kotlin
  compilations passed. Phone DEX packaging check passed.
- Added FittingWorkflowAcceptance for isolated Android preferences, exact archive
  preservation, import validation and phrase-export roundtrip without audio.
  ProgramSettingsSmoke passed on Pixel 9a at 192.168.69.130:38471 after an in-place
  update, including the new workflow/import/archive checks and relative-control
  snapshot protection. Fitting/snapshot/phrase/sleep/noise preference file hashes
  were unchanged across update and tests. No real transport was used by these tests.
- Existing user settings, snapshots and acceptance records were not changed.

Pending physical acceptance: phone/watch recognition, speech switches, large text,
round-watch review, document-provider cancellation, and fitting changes using a
disposable installation. Do not change the user's real fitting merely to test UI.
These development checks do not establish Play readiness or aid reception.

Device update status:

- Pixel 9a: updated in place; app launch and ProgramSettingsSmoke passed; protected
  preference hashes unchanged. Device OS/version is recorded in the update evidence.
- Watch at 192.168.69.190:37001: updated in place; WatchActivity launched and process
  remained present; all watch shared-preference hashes unchanged. No spoken or aid
  command was used as an installation check.
- Chromebook (Android 13/API 33): in-place update, startup and ProgramSettingsSmoke
  passed. Read-only instrumentation fingerprints confirmed fitting/snapshot/phrase/
  sleep/noise preferences unchanged before/after update and isolated tests. Its
  kernel disables run-as; the fingerprint runner never edits settings or plays audio.

Full device model/OS details and final APK hashes: [update record](device-updates-2026-10-18.md). Pixel and watch both reported Android 17/API 37; this does not replace exact Android 16 acceptance.
