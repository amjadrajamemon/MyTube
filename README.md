# MyTube

## v1.8.9 — single-session player and blackout mode

New media preempts previous native/PiP ownership and browser media is paused at player switches. The app launches to its Home feed without focusing Search. A black-screen action on the bottom of native and embedded web playback keeps audio playing; tap the black panel to restore video. These are source-level changes pending CI validation and physical car-unit verification.

Standalone Android application, package `com.aistudio.tubedeck.kxmvd`.

## v1.8.8 — XY AUTO native playback correction

This version adds a compatibility profile for XY AUTO car Android units and prevents browser callbacks from repeatedly reopening the same native video after returning to search.

In MyTube Settings, enable **XY AUTO compatibility**, save, then reopen a video with the Native player selected. Recognized car units enable this profile by default. Firmware with generic device identifiers may need the manual switch. The profile prefers software decoding, synchronous MediaCodec operation, a TextureView surface, H.264/AAC streams, a 480p target, and a smaller buffer. Software decoding can increase CPU usage; the setting can be disabled on devices where normal playback works better.

Playback errors remain visible in the native player. A consumed browser video cannot reopen from passive or stale callbacks without another user gesture. Player activities also handle UI-mode, density, and font-scale changes without recreation.

**Copy playback report** in Settings copies local decoder, surface, playback-error, lifecycle, and supported Android process-exit details. If playback still returns to search on the car unit, reproduce the problem and copy this report after reopening MyTube. Reports are not automatically uploaded. Device reports are needed to distinguish a codec/firmware crash, process termination, source failure, or an Activity launch failure; the specific physical-unit cause is not established by an emulator run.

## Build and verification

Open `source/` in Android Studio. The project uses Android SDK 36, JDK 21 to run Gradle, Gradle 8.11.1, and a JVM 17 compilation target. CI builds ARM64 and Universal APKs, runs unit tests and Android lint for both editions, and checks package/version, ABI coverage, alignment, and the pinned standalone signing identity. A generated H.264/AAC fixture tests both playback profiles in an Android 14 emulator, including actual decoded video/audio, surface/decoder selection, retained player ownership, and repeated online launches. This checks the app path; physical XY AUTO firmware and YouTube delivery still require device testing.

The build workflow provisions the multilingual offline Whisper model from:

`https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin`

It verifies SHA-1 `2827a03e495b1ed3048ef28a6a4620537db4ee51`. Release source ZIPs include that model, the editable project, CI workflow, and regression fixture. GitHub source archives omit the model; provision it before building those archives.

CI preserves the established signing certificate pinned in `.github/mytube-signing-sha1.txt` and refuses to publish if its key is missing or differs. Private signing keys are excluded from source archives. Local builds need your own signing configuration; updating an installed CI build requires its existing signing identity. Optional production signing remains configured through the existing secrets.

Use the Universal APK when the head unit's supported ABI is unknown; it includes ARMv7, ARM64, x86, and x86_64. Use ARM64 on units with a 64-bit Android userspace.
