# MyTube r6.5 LiveDub v1.2.3 — Stability + YouTube-style Feed

## Stability
- Hardened native Whisper lifecycle so a model can never be released while `whisper_full()` is still running.
- Bounded PCM queue, single-flight JNI inference, one native worker thread, 16 kHz resampling and shorter VAD clips.
- Switched to multilingual `ggml-tiny-q5_1.bin` (~31 MiB) to reduce RAM/CPU pressure while preserving multilingual recognition.
- Media3 PCM tap is now a true pass-through and never rejects non-PCM16 playback formats.
- ExoPlayer uses conservative large buffers, HTTP timeouts, transient network recovery and `keepContentOnPlayerReset`.
- Portrait native player defaults to Zoom and a taller 16:11 viewport.

## Player controls
- Caption language is selectable from a drop-down list.
- Dubbing method is selectable: Auto (prefer captions), Captions, or Audio (Whisper).
- Caption dubbing and audio dubbing no longer run simultaneously, avoiding repeated/duplicate speech.
- Press-and-hold on the right half continuously fast-forwards; press-and-hold on the left half continuously rewinds.
- Existing double-tap seek, pinch seek, brightness and volume gestures remain.

## YouTube-style home/player feed
- Home now has topic chips, watch History and a Recommended feed.
- Watch history is persisted locally.
- Recommendations are seeded from real related-video metadata from the most recently watched YouTube item.
- When comments are hidden, the native portrait player shows an Up next / related-video list.
