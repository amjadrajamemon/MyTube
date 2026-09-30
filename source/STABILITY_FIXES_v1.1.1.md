# MyTube v1.1.1 Stability Fixes

This revision addresses the player issues reported after v1.1.0.

## Portrait comments
- One Comments control/section below the native video.
- Comments are extracted into a native Compose list using the already-bundled NewPipe extractor.
- No comments WebView and therefore no duplicate embedded video.

## Portrait seek bar
- Auxiliary controls render above the time line.
- Current/duration labels and the seek slider are the final controls at the bottom edge of the video overlay.

## Live Dub continuity
- TTS uses `TextToSpeech.QUEUE_ADD` instead of flushing every cue.
- Translation is serialized in caption presentation order.
- Rolling auto-caption updates are de-duplicated so already spoken prefixes are not repeated.
- Small consecutive fragments are merged to reduce choppy speech.
- A modest dynamic speech rate catches up when the queue grows.
- Seek/skip and actual playback pause clear stale queued speech.
- Translation remains on-device ML Kit and speech output is restricted to offline Android TTS voices: no paid API or per-minute service.

## Back/navigation stability
- Player return screen is tracked separately from Downloads/Offline navigation history.
- Browser state is returned to the pre-video URL before native playback opens.
- Rapid duplicate native-player launches of the same video are throttled.
- Native player close is idempotent and disables auto-PiP during explicit close.
