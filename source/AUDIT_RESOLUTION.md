# r6.5 audit resolution — source status

The initial findings are in `DriveDeck_r6.4_Project_Audit.md`. “Addressed” means code was changed, not that an APK or device test passed.

| Audit | Source status | Detail |
|---|---|---|
| F01 simulated downloads | Addressed, runtime unverified | Real HTTPS transfers, byte progress and files; compatible adaptive video/audio pairs muxed to MP4/WebM, plus M4A/WebM audio. Chosen document-tree folder supported. |
| F02 fabricated playlists | Addressed, runtime unverified | Actual paginated playlist metadata, thumbnail/title selector and batch queue; no arbitrary 500-item cutoff. |
| F03 queue race | Addressed in coordinator | Synchronous reservations, one active job per task; user-set parallelism 1–32. |
| F04 process/lifecycle | Partially addressed | Application singleton, persisted queue and retry on launch; no persistent foreground worker. |
| F05 PiP setting | Addressed | Native auto PiP checks the setting. |
| F06 bridge/cleartext | Addressed | Removed JavaScript bridge; HTTPS browser and cleartext disabled. |
| F07 toggles/metrics | Addressed in UI/rules | Live settings and request counter; blocking remains limited. |
| F08 playback/quality | Improved, runtime unverified | Native gestures restored; high-resolution compatible audio/video streams supported; full quality menu, visible download actions and web fallback. |
| F09 sponsor race | Addressed | Cancels old requests and checks the active video ID. |
| F10 app links | Addressed | Valid incoming video/playlist URLs on create/new intent. |
| F11 browser state/navigation | Improved, device test pending | One handoff per video navigation, no repeating toast; releases WebView/player views; distinct media sessions. Exact scroll/SPA state is not preserved. |
| F12 tab selection | Addressed | Downloads/Vault composable keyed by destination. |
| F13 insets | Source fix, device test pending | Consumes Scaffold padding before nested inset modifiers. |
| F14 draggable buttons | Addressed | Clamped to measured area, positions saved, reset in Settings. |
| F15 build versions | Addressed in configuration, build pending | AGP 8.10.1/API 36, Gradle 8.11.1; Java and Kotlin target 17 on JDK 21; corrected extractor coordinates. |
| F16 portable dependencies | Not bundled | JDK, SDK, Gradle distribution and Maven dependencies are required. |
| F17 docs/branding | Addressed | Current README and version 1.0.7, DriveDeck Pro label, retained application ID. |
| F18 tests | Partially addressed | URL validation unit test exists. Build, instrumentation and Android device tests remain pending. |

## Verification still needed for release

1. On Windows with Android Studio/SDK 36, run `compile_apk.bat` and `gradlew.bat :app:testDebugUnitTest`.
2. On Android API 26 and 36, navigate rapidly among videos/menus and test Back, fullscreen, PiP, gestures and downloads from the player.
3. Test a progressive video, adaptive MP4 and WebM tracks, audio, chosen folder, pause/resume/cancel, and a playlist spanning multiple pages. Test with network interruption and after process restart.
4. Check layouts on small devices, large font sizes, portrait/landscape, 3-button/gesture navigation and cutouts. Review signing/licensing before distributing.
