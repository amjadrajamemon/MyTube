# MyTube r6.5 LiveDub v1.2.2 — Native Whisper Audio ASR

- Added `dev.ffmpegkit-maintained:whisper-android:1.0.0` (prebuilt whisper.cpp Android AAR).
- Added Media3 PCM tap (`PlaybackPcmTapProcessor`) so ASR consumes decoded video audio, not microphone audio or captions.
- Added `WhisperAudioAsr`: lightweight energy VAD, utterance chunking, WAV creation, local Whisper inference and sentence callbacks.
- Multilingual `ggml-tiny.bin` is the default model for responsive mobile inference.
- `compile_apk.bat` automatically downloads the official model once and embeds it in APK assets.
- Runtime verifies the official SHA-1 before loading the model; if a manually-built APK omitted the asset, it can provision the same verified model on first LiveDub use.
- LiveDub source languages are selectable even when a video has no captions; Auto uses Whisper language detection where exposed by the runtime.
- Existing captions remain a fallback/helper, not the primary speech source.
- Existing on-device ML Kit translation, sentence de-duplication and ten TTS voice profiles are retained.

Compatibility note: the selected free Whisper Android AAR currently publishes arm64-v8a native binaries (API 24+). MyTube minSdk remains 26.
