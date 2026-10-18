# Recognition baseline and improvement acceptance

Status October 18, 2026: pending physical measurement. Parser regression tests
establish interpretation of text, not recognition accuracy. No transcript or audio
collection is enabled by this plan.

Before a trial, record candidate APK hashes, phone/watch models and OS, recognition
provider and version, TTS provider, language, microphone route and online/offline
condition. Use a disposable fitting and fake transport for command-recognition
trials; keep the user's fitting, aid settings and restoration snapshot unchanged.

Use the same predetermined prompts before and after a change. Include program
names/slots and aliases, exact and relative levels, negatives for Balance, tinnitus
capability failures, battery sides, current-value questions, Save/Restore/Refresh,
Stop, setup review/cancel, sleep and noise edits, and phrase learning/cancellation.
Include negation, multiple commands, conflicting recognition alternatives and
unavailable/denied/cancelled recognition. Repeat with phone/watch, quiet/everyday
noise, network unavailable and all prompts/replies switch combinations.

For each condition record counts of correct intended interpretation, explicit
clarification/rejection, wrong interpretation, wrong playback, cancellation and
provider failure, plus request-to-visible-result timing. Report denominators and
conditions separately; never merge provider failures into parser passes. Any
unintended playback blocks acceptance. Use aggregate counts by default. Retaining
recognized text examples requires a separate affirmative opt-in explaining scope,
retention and deletion. Do not retain audio or add background collection.

Compare baseline and candidate on the same prompt set and conditions. Require zero
unsafe executions and no regression in Stop, confirmation or unknown-baseline
handling before accepting recognition or personal-phrase improvements. Record
actual observed results in a dated acceptance record; do not invent percentages.
