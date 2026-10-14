# Automatic saved-settings verification — October 11, 2026

Scope: persistent settings and Signia app readback. The user explicitly excluded physical hearing-aid responses and real charging from these checks.

The configured phone project was backed up, then 13 individual tasks were imported. Existing task IDs, program mappings, calibration and all 342 prior worker configuration/dispatch actions were preserved. Three temporary callers ran 12 checks and were removed afterward. The final configured export contains 92 tasks, retains every original task ID and contains no temporary callers. Stored individual XML exports match the installed tasks.

| Check | Saved program | Volume | Balance | Tinnitus | Result |
|---|---|---:|---:|---:|---|
| initial complete save | Universal | 9 | 0 | 0 | passed |
| automatic changed volume | Universal | 8 | 0 | 0 | passed |
| read preserves saved snapshot | Universal | 8 | 0 | 0 | passed |
| repeat target preserves saved snapshot | Universal | 8 | 0 | 0 | passed |
| numeric voice restores original volume | Universal | 9 | 0 | 0 | passed |
| restore voice after manual program change | Universal | 9 | 0 | 0 | passed |
| restored volume | Universal | 9 | 0 | 0 | passed |
| restored balance | Universal | 9 | 0 | 0 | passed |
| restored tinnitus | Universal | 9 | 0 | 0 | passed |
| program change automatically saves TV without tinnitus | TV | 8 | 0 | unavailable | passed |
| TV restore skips absent tinnitus | TV | 8 | 0 | unavailable | passed |
| return to original program automatically saves all controls | Universal | 9 | 0 | 0 | passed |

Setting volume 9→8 and the numeric voice route 8→9 updated only volume in the saved record. Read-only requests and a repeated target retained the exact record and timestamp. A direct Signia volume change 9→0 and manual TV selection simulated unsaved defaults; the `restore settings` voice route restored Universal/9/0/0 and retained its saved record. The route was supplied as text to the same voice router used by recognition.

Selecting TV through HeySig3 automatically established its complete 8/0/null record. Restoration skipped unavailable tinnitus without requesting its tab. Returning to Universal automatically established the final 9/0/0 record. Every request reported success with no save warning. Media volume remained 17/25.

Local verification passed: 33 actual BeanShell JSON/file cases; generated save/restore and automatic-update flows; 768 numeric transitions; worker coordination, reporting, 18 speech-formatting cases, parity, progress, retry and generation checks. Static validation: 86 XML files, 6325 task actions, zero failures.

See [saved-settings behavior](https://github.com/5310H/HeySig/blob/HeySid3/docs/HeySig3-saved-settings.md).
