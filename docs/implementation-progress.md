# Implementation order — October 17, 2026

The owner requested work in the order recommended by the project review.
Preserve existing fitting/settings, acceptance evidence and debug identities.
Do not use real aid changes merely to check installation or UI behavior.

1. **Saved settings protection:** explicit Save is the only ordinary command
   path that replaces the restoration snapshot. Preserve snapshot bytes across
   manual/automatic/battery commands, partial restore and restart; isolate
   damaged snapshots from valid fitting. Shared planning, phone/watch/hands-free
   Save and voice review use common validation. Implemented and validated with
   shared/build/isolated Android checks; updated Pixel debug installation
   preserves existing fitting and snapshot bytes. See
   [acceptance record](acceptance/saved-snapshot-protection-2026-10-17.md).
2. **Sleep operation and automation priority:** owner selected notifications
   requiring a tap rather than automatic locked-phone switching. Sleep now/Wake
   now, durable mode/override/recovery and shared manual/sleep/noise priority are
   implemented. Added explicit inexact reminder enable/disable, stale/duplicate
   handling and future-only boot/update/clock/timezone recovery. See
   [design](sleep-management-design.md) and [validation record](acceptance/sleep-management-2026-10-17.md).
   Shared checks, phone/Wear debug APKs and release Kotlin compilations passed.
   Device validation/install were not completed after tool approval was declined;
   physical reminder and aid acceptance remain pending. Noise Automatic remains locked.
3. **Voice/configuration:** relative Balance/tinnitus controls, guided fitting
   drafts and aliases, Help/cancel/retry, reviewed fitting import and explicit
   conversational personal phrases with reviewed import/export are implemented.
   Shared tests, phone/Wear builds and release Kotlin compilation passed; isolated
   Android archive/import/snapshot tests passed on Pixel. Physical speech/watch
   acceptance and controlled recognition measurements remain pending. See
   [guided setup evidence](acceptance/guided-setup-and-phrases-2026-10-18.md) and
   [recognition trial plan](voice-evaluation.md).
4. **Publication preparation:** resolve implementation/rights/disclosure gaps,
   prepare concrete release artifacts and listing drafts. Verified owner facts,
   public policy publication, medical assessment, account declarations and Play
   review remain external/owner prerequisites. Never invent or submit them.

No item is marked physically accepted by parser, fake-transport or build checks.

October 18 owner direction: keep release/account/privacy/medical prerequisites pending; finish app work before device updates. In-place development updates preserve settings. Physical acceptance is never inferred from builds or installation.
