# MyTube v1.5.1

## PiP controls outside MyTube
- The Android PiP menu exposes **Keep PiP**, **Audio**, and **Close** while MyTube is backgrounded.
- Actions are delivered through an internal broadcast receiver, so using them does not require reopening the app UI.
- Android 13+ uses a custom PiP close action for the system X.

## Hard close
- PiP Close/X immediately stops any background-audio MediaSession/service.
- Native ExoPlayer playback is terminated through the existing native PiP session bridge.
- Web playback is terminated through a process-local MainActivity PiP command bus.
- Older Android versions retain the lifecycle-based PiP-dismiss fallback.

## ExoPlayer loading indicator
- The circular loading indicator remains visible through stream resolution and Media3 READY.
- It is hidden only after Media3 reports the first rendered video frame.
- Stream/quality replacement resets first-frame state, so the indicator returns correctly.
- Rebuffer and recovery also show the spinner.
- The spinner is larger, higher contrast, and explicitly raised above the player surface.
