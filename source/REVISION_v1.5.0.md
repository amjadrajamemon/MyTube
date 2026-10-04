# MyTube v1.5.0

## Playback navigation
- A slow isolated Back while PiP is active no longer expands/maximizes the video.
- Quick second Back opens the three playback choices.
- Rapid triple Back remains the explicit pause gesture.

## Player feedback and gestures
- Centered animated circular loading indicator covers initial resolve/prepare, rebuffering and recovery.
- Edge double-tap keeps backward/forward seek.
- Center double-tap toggles fullscreen.
- Center swipe up enters fullscreen.
- Center swipe down exits fullscreen to the in-app mini player; another downward swipe can enter PiP.
- Left/right vertical gestures remain brightness/volume.

## Suggested feed
- Recommendations are blended from the three most recent watched interests.
- YouTube Watch Next is preferred, with the parsed related shelf as an independent fallback.
- Empty failures are retried after a short backoff instead of being cached as a blank shelf for 30 minutes.
