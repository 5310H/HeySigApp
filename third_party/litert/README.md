# Offline audio runtime

Both apps pin Google's `com.google.ai.edge.litert:litert:1.4.2` and its API AAR.
`integrity.json` records the downloaded artifact/native SHA-256 values; the
shared Gradle verification task checks both AARs before building. These are byte
pins, not an independent authenticity or vulnerability audit. Runtime code is
Apache-2.0; upstream: https://github.com/google-ai-edge/LiteRT.

The model, upstream Apache license and attribution are in
`audioAndroid/src/main/assets/environment`. No GPU, Task Audio support runtime,
Play-services model runtime or on-device download is added. CPU inference is
single-threaded. Validate all new JNI libraries for ELF and APK ZIP 16-KB
alignment using `tools/validate-native-apks.py`; reproduce release artifacts on
a supported standard build host before submission.
