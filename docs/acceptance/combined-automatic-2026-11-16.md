# Combined Automatic development record — November 16, 2026

Owner request: finish one integrated Normal/Noisy/Very Noisy/TV flow, detect background-only noise, return after noise ends, and remember reviewed experiences. Built-in TV speakers are the first physical target. Live enablement remains authorized, not accepted accuracy.

Implementation: ten-second bounded capture; full-window fixed TV head; three separate three-second speech/neural sections; independent recognized background-noise fallback (including white/pink noise, static and hiss) using saved thresholds; strong noise priority; target-specific confirmations and actual quiet evidence. Memory is explicit review/Remember, cap24, 40 scalars, source/model/fitting scoped, disable/forget, no saved waveform or transcript. Existing program assignments, fitting, shared executor, manual/Therapy priority and cooldown retained. See [guide](../how-automatic-noise-works.md) and service amendment.

## TV experiment and limitations

Pinned encoder SHA256 b2fc8fc3ff9c960bde60b5fea71335d4cb2a35c4d1b011c3a80927fd64d8a2e4. Expanded head SHA256 7e9d09bb8726db136e038e974e085847e454327af0513853f8b252a3223b779e. Reproduction: tools/environment/train-television.py with tv-sources.json and licensed cached source files. Sources, authors, groups, partitions, hashes and licenses are in data/combined-automatic-2026-11-16/. Windows use two correlated gains; counts are not independent rooms.

Initial fit: 150 windows, 94 development and 56 validation. Validation 48 correct, seven missed TV and one uncertain, all misses/uncertainty in distant source475477. That source was explicitly moved into development for the expanded fit; it cannot count as unseen validation afterward. Added distant-TV development author LG; new validation author Annerie frozen before the expanded fit. No validation-source coefficients were fitted. Most other validation sources had already been inspected and are regression evidence.

Expanded fit: 168 windows from 32 recordings; 116 development, 52 validation. Development fit116 correct (not accuracy evidence). Validation48 correct, two false-TV windows (two gains of one conversation recording fsd-418989), two uncertain piano windows (source687036), no missed-TV windows. This remaining conversation false positive prevents a claim of reliable TV/conversation separation. Group cross-validation selected C10 and thresholds absent<=0.1, present>=0.7; its seven TV misses and 37 uncertain development predictions also show source generalization limits. Raw counts/report retained, including initial failures. No wearer audio was collected to fit this model.

## Verification and device evidence

Shared policy tests cover background-only digital levels, clipping/missing evidence, noise-before-TV, repeated TV/noise/normal transitions, distinct targets, preserved manual/Therapy and cooldown behavior. Memory checks cover vector bounds, near-match/scoped recall, conflict rejection, cap24 and TV head validation. Native instrumentation uses only a bundled licensed fixture and isolated preferences: packaged pins, 19-frame/three-section combined inference, cancellation, unchanged PCM, restart/disable/source-isolation/corrupt-byte behavior. No microphone or aid command is needed for these tests.

Final build/lint/emulator and phone installation results will be recorded after execution. Physical room accuracy, sustained battery behavior, watch wrist-position inference age, aid reception and actual listening benefit remain unaccepted until measured. No Play submission/approval is claimed.

A fresh phone backup on November16 verified eight private files/seven preference XMLs against device checksums before installation. The private archive is excluded from Git; no personal settings are published.
