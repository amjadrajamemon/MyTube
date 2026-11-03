# MyTube v1.9.7 — Stability, Resource and Compatibility Audit

Date: 2026-11-03  
Branch: `feature/fix-next-autoplay-blackout-home-20261102` / draft PR #32  
Scope: Android client, native player, background audio, YouTube extractor, native TV navigation, downloads, WebView and APK CI.

## Executive assessment

MyTube is a capable multi-surface Android YouTube client (TV remote, keyboard, air mouse, car head unit and touch). Its main risks are **continuation-state consistency**, **large shared components**, **OEM-specific behavior**, and **heavy optional media dependencies**. Kotlin compilation and unit tests demonstrate source integrity, not device-specific stability or SmartTube parity. Do not call the app "bug free" without Android TV, car and phone acceptance testing.

The published v1.9.6 compressed APKs were approximately **116.9 MB ARM64** and **284.1 MB Universal**. Universal contains multiple CPU architectures. Size is dominated by the available native/offline features; removing them by default would violate current user requirements. A future opt-in Lite flavor is possible only if feature trade-offs are approved.

## Verified code defects corrected for v1.9.7

| Priority | Issue | Evidence | Correction |
|---|---|---|---|
| P1 | Audio-only Next could oscillate between videos A and B | BackgroundAudioPlaybackService previously excluded only the current video | Shared session policy tracks up to 80 played IDs, skips repeats across cached/Watch Next/search results; unit tests for reset/eviction/no repeats |
| P1 | Audio-only playback could reach end of stream without advancing or stopping | Background media player previously lacked STATE_ENDED handling | AUTO_NEXT resolves next, LOOP_CURRENT restarts and EPISODES ends cleanly; unavailable continuation stops idle service |
| P2 | Audio-only service woke the device every 500 ms while paused | Unconditional position polling in BackgroundAudioPlaybackService | Event-driven, cancellable position snapshot job; periodic updates only during active playback (2 s) |
| P1 | Network request exceptions could leave NewPipe HTTP connections open | Connection disconnected only around Response construction | Cover all request output, response access and body-read paths with a single try/finally disconnect |
| P2 | Missing/buggy TV ROM system service could crash download scheduling | DownloadResourceGovernor assumed Activity/Power/ConnectivityManager non-null | Graceful conservative one-worker fallback; preserve resource-based admission control when services work |
| P2 | Blackout icon always occupied bottom-right of a normal video after controls faded | Floating player IconButton duplicated lower toolbar control | Remove permanent native-player shortcut; retain fading toolbar eye. A dim restore action exists only in the opaque blackout mask; tapping anywhere still restores |

## Existing useful safeguards to retain

- Limited concurrent downloads with thermal, memory, network and battery adaptations; resumable downloads and retry caps.
- Bounded extractor/media-resolution caches and shared in-flight resolution.
- StateFlow-driven settings and queue management, persistent watch history, cancellation-aware download operations.
- Native ExoPlayer + WebView recovery after renderer exit; PiP menu with explicit close, foreground audio service.
- D-pad/keyboard player policy tests, three Home columns, selected item focus restoration, APK ABI and certificate checks.
- Live Dub lazily initializes TTS/ML Kit/Whisper; do **not** preload ASR on video open.
- Separate ARM64 and Universal APKs; versionCode increases preserve update path with pinned signing identity.

## Remaining work — ranked

1. **P0 device regression:** run real-device input/playback matrix on Android TV 9–16, AOSP TV boxes, phone and Android car units. Confirm center/left/right/Back, keyboard/air mouse, dialogs, rapid repeat, PiP Close/Expand, blackout and autoplay. CI cannot substitute for firmware behavior. Capture reproduction logs/Android version for failures.
2. **P1 reconnect/extractor robustness:** network offline/online, DNS/TLS errors, YouTube 429 or layout changes, signed URL expiration, live-stream discontinuities and no-results paths should show recoverable actions rather than blank playback. Add integration fixtures and response-size safety limits after measuring realistic YouTube response sizes.
3. **P1 cancellation/resource ownership:** isolate playback state from ~5,400-line PlayerScreen, ~2,000-line MainActivity and ~1,600-line repository; centralize audio/video continuation. Refactor only behind regression tests to avoid breaking TV and car variants.
4. **P1 data durability:** verify power-loss/process-death during queued downloads and external SAF/USB write stages, including providers that do not support rename. Ensure prior completed files survive failed replacement and temporary files are cleared.
5. **P2 idle performance:** profile actual CPU/RAM/network, decoder and WebView allocations (Android Studio profiler / adb dumpsys). Restrict browser JS polling and video companion prefetch only when measurement proves a benefit; preserve user-visible navigation responsiveness.
6. **P2 APK footprint:** produce a Gradle APK Analyzer report and dependency/ABI breakdown. Consider a separate Lite flavor without bundled offline dubbing/FFmpeg **only with explicit approval**; preserve the full-feature builds.
7. **P2 security/release:** periodically review dependencies, OAuth flows, WebView safe browsing and JS bridge exposure, Android platform target updates, release keystore management and third-party source integrity. Current feature APKs use the pinned update-compatible debug identity, not a new production signing key.
8. **P2 continuous quality:** run Android lint, screenshot/accessibility tests, emulator smoke tests for blackout/remote navigation and release asset checksum verification. Retain versioned, non-overwriting GitHub releases and draft review before merging.

## Definition of done for a professional TV-ready release

- Both architecture APKs compile and unit/lint checks are reviewed.
- Physical Android TV D-pad/air-mouse/keyboard paths pass Home, Search, full-screen video, seeking, Back, PiP and audio-only testing.
- 30-minute playback and repeated next-video sequences do not loop or leak foreground sessions.
- Storage transfers recover from interruption without corrupting previously completed media.
- Low-RAM TV boxes preserve acceptable scroll responsiveness and stop idle polling/decoding.
- External YouTube/Google endpoints fail gracefully when unavailable.
- No merge or release overwrite without checking CI and the existing signing identity.

This audit records risks and concrete code changes; it is **not** a certification of zero defects or measured performance.
