# Offline wake detector provenance

Engine: sherpa-onnx 1.13.8, Apache-2.0, official release AAR:
https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8

ONNX Runtime is included by that AAR under MIT; see ONNXRUNTIME-LICENSE.
The Android model is `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20`,
using the chunk-16 int8 encoder/joiner, fp32 decoder and tokens from:
https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models

The model author's ModelScope repository explicitly declares Apache License 2.0:
https://modelscope.cn/models/pkufool/icefall-kws-zipformer-zh-en-3M-2025-12-20
The fetched model card is retained here. The author clarified the license on
October 8, 2026 in https://github.com/k2-fsa/sherpa-onnx/issues/3802
(the issue's final author comment is the evidence).
This records the author's declaration, not an independent legal certification
of training data. Retain the distribution-rights review in the release audit.

`integrity.json` pins original artifact, native-library and bundled asset bytes.
The build independently checks the engine AAR SHA-256. All four native libraries
on all four shipped ABIs have ELF PT_LOAD alignment of 16,384 bytes. APK ZIP
alignment and actual 16 KB-device acceptance are separate checks.

Hey Sig is a project-authored phonetic keyword (`HH EY1 S IH1 G`). No personal
voice recording or custom training account is used. Wake sensitivity and false
activations require real-world acceptance. These assets are not an LLM.

## Runtime telemetry review

The bundled arm64 `libonnxruntime.so` reports version 1.28.2. The pinned engine's
Android CMake recipe selects that same version:
https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/onnxruntime-android-aarch64.cmake
The version-specific privacy document states that its telemetry implementation
is Windows-only:
https://github.com/microsoft/onnxruntime/blob/v1.28.2/docs/Privacy.md
Binary inspection found no 1DS/uploader/ORT_DISABLE_TELEMETRY strings in the
pinned Android library. This is not a claim about newer ONNX Runtime releases:
current upstream documentation describes cross-platform telemetry. Any engine
upgrade requires a fresh data-flow and telemetry review before changing hashes.
