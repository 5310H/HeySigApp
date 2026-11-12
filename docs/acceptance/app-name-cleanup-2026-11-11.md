# HeySig app-name cleanup — November 11, 2026

Owner requested renaming HeySig Dev to HeySig and removing the other copy.
Pushed app commit `4a25d019d6bd0b48ecb70f754700f7db2ac5b278` changes only the
two debug application labels. Application IDs, signing identity, permissions,
pairing and control behavior are unchanged. This remains a development artifact.

[Cloud run 43126232539](https://github.com/5310H/HeySigApp/actions/runs/43126232539)
passed shared checks, both builds/lint, native artifact validation and silent
API 36 emulator smoke. Matching APKs were signed using the existing local
development key, then installed in place on Pixel 9a and Pixel Watch 3.
All 13 phone and one watch user-preference files matched before/after hashes.
No microphone capture or aid command was used for this cleanup.

Removed older installed packages `com.heysig.app` on phone and watch, and
`com.heysig.watchdev` on watch, using keep-data removal for user 0. Stored data
was retained; rollback APK copies were saved locally under
`/tmp/heysig-rename-20261111/older-apps/` (temporary, not committed).
The current paired package remains `com.heysig.batteryprototype`.

Installed label/hash and package-inventory evidence accompanies this report.
Earlier behavioral acceptance remains versioned in
[the Automatic completion report](automatic-completion-2026-11-11.md).
