# Direct volume: user acceptance and limits

October 16, 2026. The user reported “volume worked” while moving to balance/tinnitus testing. Retain this as user acceptance, distinct from automatic aid readback.

The implemented standard-profile mapping is `0x1f - displayed level`, with explicit range 0–15. Captured Signia level 9 decoded as 0x16/address 12; restored level 8 decoded as 0x17/address 12. Kotlin levels 4, 8 and 9 matched original encoder PCM in the [waveform checks](../../research/acoustic/fixtures/volume-waveform-validation.json).

HeySig sent level 4/address 12 in Universal; [captured output](../../research/acoustic/fixtures/decoded-heysig-volume4-2026-10-15.json) decoded as 0x1b. The user initially found the listening difference hard to judge, then requested an 8 → 15 comparison. That comparison was not completed by the agent before the user subsequently confirmed volume worked and requested balance/tinnitus. Do not describe 8 → 15 → 8 as a completed controlled test.

Fresh October 16 Signia inspection showed Universal and its remembered volume 8. This is saved app state, not independent evidence of the physical aid's volume. No direct volume query exists in the user's acoustic setup. Endpoint/mute variants and exhaustive per-program/range checks remain outside this acceptance.

During the new prototype session, its persisted last completed volume request was observed as 15. This is consistent with the user's own testing; it does not retroactively establish an agent-observed 8 → 15 → 8 sequence. Before balance/tinnitus live tests the agent successfully sent volume 8, and the app recorded that completed request.
