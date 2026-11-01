# Archived YAMNet source

`yamnet-original.tflite` is the Apache-2.0 Google/TensorFlow YAMNet classification-tflite v1 model, SHA-256 `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`. License and upstream attribution are in `audioAndroid/src/main/assets/environment/LICENSE` and `NOTICE`.

This archive is a developer input, not an APK asset. Existing event-only corpus probes use it to preserve their historical observations. The runtime asset exposes one additional existing embedding tensor without changing weights/operators. Reproduce it with `tools/environment/derive-scene-encoder.py`; see the scene-head corpus README for the complete pipeline.
