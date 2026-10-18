# Relative volume — October 17, 2026

Added explicit one-step volume up/down intents. Accepted forms include turn the
volume up/down, increase/decrease volume and raise/lower the volume, with the
existing polite wrappers. Configured program names retain precedence. Negation,
compound commands and multi-step amounts are not accepted as relative commands.

Shared VolumeSteps resolves the absolute target at dispatch using the current
program's known volume and existing validation. Unknown baseline, invalid program
or a target beyond 0–15 rejects without playback. Program changes and interrupted
level commands retain their existing baseline invalidation. No clamping, inferred
baseline, automatic repetition or saved-snapshot replacement is introduced.

Phone/foreground watch and the existing hands-free route pass the resolved request
to the single executor; relative actions retain manual-over-sleep priority. Phrase
learning remains restricted to its existing absolute/read targets.

Shared checks exercise both directions for every baseline 0–15, unknown/invalid
contexts, unsupported step sizes and ambiguous language. Isolated Android snapshot
acceptance additionally executes both resolved directions through fake transport
and verifies the saved snapshot remains unchanged. Results follow after execution.
