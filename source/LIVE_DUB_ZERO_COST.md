# MyTube Live Dub — Zero-Cost On-Device Mode

MyTube v1.1.1 adds caption-driven Live Dub for the native Media3/ExoPlayer player.

## Design

- No paid API key, subscription, server, or per-minute fee.
- The video subtitle/caption cue is the speech source. Audio is never uploaded for dubbing.
- Translation uses `com.google.mlkit:translate:17.0.3` on-device models. Required models are downloaded once by the app over Wi-Fi and then reused locally/offline.
- Speech output uses Android TextToSpeech and an installed local voice.
- The original video audio is ducked while Live Dub is enabled and restored immediately when disabled.
- If a video exposes no compatible subtitle/caption track, Live Dub reports that it is unavailable instead of falling back to a cloud service.

## Built-in 10-language matrix

English (en), Urdu (ur), Hindi (hi), Arabic (ar), Spanish (es), French (fr), German (de), Portuguese (pt), Russian (ru), and Chinese (zh). Any available source caption language in this set can be selected against any target language in the set.

## Player controls

The fading native-player control row contains a `Dub` action. Player Settings contains Live Dub on/off, Auto/source caption selection, target language, original-audio level, and model/voice readiness status.

## Important limitation

Live Dub depends on a subtitle/caption track supplied by the video/extractor and on an installed Android TTS voice for the selected target language. This keeps the feature lightweight and free, but a video with no usable captions cannot be dubbed by this zero-cost mode.
