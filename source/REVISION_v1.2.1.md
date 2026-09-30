# MyTube r6.5 LiveDub v1.2.1 consolidated revision

This package is a single Android Studio source tree based on v1.2.0.

## Integrated in this build
- Keeps the v1.2.0 player stability, single-search, portrait sizing, comments, navigation and full quality-selection refinements.
- Adds 10 selectable local TTS voice profiles: Natural, Clear, Warm, Bright, Deep, Fast, Calm, Narrator, News and Compact.
- Voice profiles tune locally installed Android TTS voices without paid cloud voice APIs.
- Retains ordered sentence buffering, rolling-fragment de-duplication, seek/pause queue resets and adaptive catch-up speech rate.
- Keeps the 10 target languages already exposed by LiveDub and ML Kit on-device translation.

## Audio ASR boundary
The project does not falsely bundle Whisper weights. True speech recognition from decoded video PCM requires a native ASR runtime plus model weights; neither existed in the supplied baseline and model binaries cannot be manufactured from source code. The current package therefore retains caption-derived source text as the operational fallback while the dubbing/sentence/translation/TTS side is consolidated and voice-selectable.

No paid API key is required by the included LiveDub implementation.
