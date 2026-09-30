# DriveDeck Pro r6.5 — Native/Web Player UI Fixes (v1.0.8)

This revision addresses the player/download-control defects reported against the r6.5 source.

## Implemented

- Removed the Native ExoPlayer lifetime landscape lock; portrait and landscape are both supported.
- Added an explicit rotate control for switching portrait/landscape from the player.
- Added portrait companion layout with an on-demand YouTube comments panel.
- Added a true **Auto** playback-quality selection state for resolved YouTube streams. Auto uses Media3 bandwidth estimates plus current link capacity and can reselect an appropriate available stream while playing.
- Restored manual playback quality selection alongside Auto.
- Added working video resize/aspect choices: **Fit**, **Zoom**, and **Stretch**.
- Retained double-tap seek, pinch seek, volume, and brightness gestures.
- Reworked the bottom control row so quality/speed/aspect/caption controls can scroll instead of colliding on narrow screens.
- Moved native download actions into the player's own control layer instead of an independent floating overlay.
- Download actions auto-fade with native player controls and reappear on player interaction.
- Web-player download actions also auto-fade and reappear on WebView interaction.
- In portrait mode, download actions are placed below the video frame to prevent overlap with playback controls.
- Removed the visible source URL field when a known video/playlist URL is passed by an in-player/browser download button; the URL remains in backend state.

## Build verification note

A source-level Kotlin syntax pass was completed on the modified player/downloader files. Full Gradle compilation could not be run in the packaging environment because the Gradle distribution and Android SDK dependencies are not locally available and outbound Gradle download is blocked. The project remains configured for the supplied Android Studio / `compile_apk.bat` workflow.
