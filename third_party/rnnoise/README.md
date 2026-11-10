# RNNoise speech/residual analysis

Upstream: https://github.com/xiph/rnnoise at
`70f1d256acd4b34a572f999a05c87bf00b67730d`.
Model archive: https://media.xiph.org/rnnoise/models/rnnoise_data-0a8755f8e2d834eff6a54714ecc7d75f9932e845df35f8b59bc52a7cfe6e8b37.tar.gz
SHA-256: `0a8755f8e2d834eff6a54714ecc7d75f9932e845df35f8b59bc52a7cfe6e8b37`.
Only the smaller `rnnoise_data_little.c/.h` pair is compiled, using the upstream
library's BSD-3-Clause terms retained in COPYING. Source/generated-weight hashes
and compiler details are emitted by `tools/masking/build-rnnoise.py`.

No audio or transcript is part of this dependency. The pretrained speech denoiser
is not a target-speaker separator, calibrated acoustic SNR meter or medical
intelligibility model. Developer source disjointness does not establish absence
from RNNoise's pretraining data. The removed residual can contain wanted speech.

The HeySig adapter resets both neural and matched-reference states per three-second
capture. It applies the identical high-pass/FFT/delay path to the reference,
flushes/crops the fixed 960-sample delay, and uses a second neural pass to check
speech-like activity in the residual. Bounded digital gain is not wearer learning.
Owned capture/state buffers are erased after processing. No new network inference,
permission, transcription, audio history or playback path is introduced.

Host and Android build commands are explicit development tooling. Downloads require
`--download`, verified source revision and model hash. Android uses official NDK
27.2.12479018, API 29, four ABIs and explicit 16KB ELF alignment. The manual GitHub
workflow builds only native artifacts, with read-only repository permission and
no signing material. Device runtime/battery and release review remain separate.
