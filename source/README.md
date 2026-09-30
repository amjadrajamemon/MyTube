# MyTube r6.5 LiveDub v1.2.3 Stable

Native audio-first LiveDub now uses on-device whisper.cpp ASR. Run `compile_apk.bat`; it automatically obtains and bundles the multilingual Whisper Tiny Q5_1 model, then builds the APK. After installation, transcription is offline.

# MyTube r6.5 — Android Studio source project

This ZIP contains the complete editable Android Studio project, including the Gradle wrapper. It is source code, not a built APK. Use Android Studio with SDK Platform 36 and an internet connection to resolve Gradle/Maven dependencies.

## Changes in r6.5

- Native player handoff occurs once for a new video navigation. The repeating “Switching to native player” message has been removed. Returning from the player leaves the browser on its search/results page. The WebView and player view are detached and released when their screens close, and media sessions have distinct IDs to reduce rapid-navigation collisions.
- The native player has double tap seek (10 seconds left/right), pinch seek (5 seconds per 32 pixels of pinch movement), held seek buttons, volume/brightness gestures, and visible video and playlist download actions. Player settings show the complete extracted quality list. Back closes the player after leaving settings/fullscreen; PiP remains an explicit control and an optional Home behavior.
- The video dialog displays the actual compatible resolutions extracted for that video. High resolution video-only streams are paired with matching audio and combined into MP4 or WebM with Android's media muxer. “Best” selects the highest compatible stream. A requested playlist resolution acts as a ceiling for each item, so an unavailable resolution falls back to a lower compatible one.
- Playlist selection loads actual metadata pages, showing titles, thumbnails, duration, individual checkboxes, All and None. The former 500-item cap is gone; extraction continues until pagination ends or a page repeats. The player playlist action opens this same selector when the current URL includes a playlist, or lets you paste a playlist URL.
- Settings now accept a selected writable folder through Android's document-tree picker, with persisted access across restarts. App storage remains an option. Parallel download count can be set from 1 to 32, and resolution choices extend through 4320p where a compatible stream is available.
- Java and Kotlin bytecode both target JVM 17, including when Android Studio runs Gradle with JDK 21.

## Operational limits

- YouTube stream access and playlist extraction depend on the service and NewPipeExtractor. Some videos, playlists, codecs or geographic/account restrictions may prevent extraction or playback; native errors offer a web player fallback. Resolution is only offered when a compatible stream is reported. MP3 transcoding is not included.
- Separate media tracks are downloaded to app storage and muxed before the output is copied to a selected document-tree folder. High resolution downloads need temporary free space for both tracks and the completed file. Android's muxer may reject an unsupported codec/container pairing; that task reports a failure. The selected folder must remain writable.
- Transfers run while the app process is alive. Interrupted work is retried on next app launch. Partial HTTP transfers resume only when the server accepts Range; there is no foreground service or scheduled background worker.
- Files in app-specific storage may be removed when uninstalling the app; a document-tree folder is managed by the user. Blocking rules are limited. Browser restoration preserves the URL, not the exact scroll or single-page-app state.
- These edits have been reviewed as source, but the r6.5 project was **not compiled or tested on an Android device** in this workspace, which lacks the Android SDK and Gradle distribution. A device crash fix cannot be guaranteed without a stack trace and a build/device run. See `AUDIT_RESOLUTION.md` for the audit status.

## Build on Windows

1. Install Android Studio, Android SDK Platform 36, and platform tools. Use its bundled JDK or JDK 17/21.
2. Extract the whole ZIP to a short path such as `C:\MyTube\`.
3. Double-click `compile_apk.bat`. It finds the JDK/SDK, creates `local.properties`, and runs `gradlew.bat assembleDebug --stacktrace`. The first run downloads dependencies.
4. The debug APK will be at `app\build\outputs\apk\debug\app-debug.apk`, and `compile_apk.bat` also creates `MyTube-v1.1.1-debug.apk` in the project root after a successful build. To run the URL test, invoke `gradlew.bat :app:testDebugUnitTest`.

A signed release APK needs a signing key and release configuration. Keep the full build output and Android crash stack trace if either fails.

## Versions

Android Gradle Plugin 8.10.1; Gradle 8.11.1; Kotlin 2.1.10; `compileSdk` and `targetSdk` 36; `minSdk` 26; bytecode target 17. Package ID `com.aistudio.tubedeck.kxmvd` supports updates over the earlier source build; version code 12, name 1.1.1. Review `THIRD_PARTY_NOTICES.md` before distribution.

## Live Dub v1.1.1

Native ExoPlayer now includes zero-subscription, caption-driven Live Dub. It supports English, Urdu, Hindi, Arabic, Spanish, French, German, Portuguese, Russian and Chinese in both source/target directions when the selected source caption track is available. Translation models are downloaded free on first use and then run on-device; speech output is restricted to an installed offline Android TTS voice. See `LIVE_DUB_ZERO_COST.md`.


## v1.1.1 stability fixes

- Portrait native player now has one native comments panel only; no secondary YouTube WebView/player is created.
- Portrait timeline is anchored at the bottom of the video overlay below auxiliary controls.
- Live Dub uses an ordered caption/translation/TTS queue and `QUEUE_ADD`, de-duplicates rolling captions, merges short fragments, and clears stale speech on seek/pause.
- Native-player return navigation is isolated from ordinary screen navigation, duplicate native launches are throttled, and the underlying browser is moved off the video URL before native playback.
- Native player close is idempotent to avoid repeated activity/window transitions.
