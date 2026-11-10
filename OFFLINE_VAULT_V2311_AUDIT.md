# TubeDeck v2.3.11 — Coordinated Offline Vault Upgrade

## Scope delivered in this branch

### 1. Robust downloads, storage and recovery
- Existing persisted download queue, retry/backoff and foreground service remain in place.
- Smart Replay tasks now record their own Wi-Fi/Ethernet-only preference in queue JSON. Changes in default network automatically pause/requeue these transfers, retaining partial files and avoiding a silent cellular-data fallback.
- The device storage preflight now checks the actual staging filesystem, not a different internal directory. Safety reserve is **192 MiB**, not a 2 GB library limit.
- The offline library verifies readability and recorded size for local files and available SAF files. Unplugged SD/USB entries stay in the catalog, are shown as missing and are not selected for offline-first replay.
- No automatic deletion of completed videos, retention count, or total offline library capacity limit.

### 2. Eligible, authorized cache reuse
- Optional Media3 persistent cache for **direct progressive MP4/WebM/M4V URLs** only.
- User must explicitly enable direct-file caching and ensure they have offline retention permission.
- YouTube and Googlevideo URLs, WebView, HLS/DASH and protected streams are excluded from this cache.
- The same persistent cache is used during playback and post-completion promotion. Promotion **requires exact known content length and 100% byte coverage**, checks free space and commits the verified file.
- If a SAF/USB destination was explicitly selected, promotion verifies the external target before deleting its cached source. Failed promotions keep the original cache.
- The existing YouTube download engine still uses its own post-watch download queue; it does **not** reuse YouTube streamed bytes. No bypass of DRM, offline rights or provider restrictions is implemented.
- Temporary partial direct-stream cache can be cleared explicitly. The app does not silently evict saved completed files.

### 3. Offline-first UX
- A saved video is chosen automatically for standalone YouTube URLs where file availability is verified. Playlist/Mix context keeps the online URL to avoid breaking Next/Previous queue logic.
- Offline Vault gains real search, sorting, pin metadata, user collections, missing-file filtering, size/space statistics, and fully wrapped titles.
- Files in app-private storage are shareable securely using a non-exported FileProvider and Android Sharesheet; content-URI files keep their existing scoped permissions. Compatible local transfer apps may appear in the chooser, but TubeDeck does **not** automatically install or connect to Local-Beam.
- Pinned state and collections survive app/process restarts, copies and moves.

## Important known boundaries
- Automatic cache promotion covers progressive direct streams only, requires full known byte coverage and adequate destination space; not every completed stream can be promoted.
- Offline status for SAF documents can be limited by provider metadata; actual opening still checks permissions and readiness.
- No completed download's metadata is silently invented. Download quality and file format depend on the source extractor.
- The download backend does not yet include Media3 DownloadService/DownloadManager for YouTube. Replacing a working yt-dlp+FFmpeg backend with Media3 would break adaptive track merging.
- Local-Beam interoperability uses Android's standard ACTION_SEND chooser; automatic LAN target discovery requires Local-Beam-side support.
- Resume countdown and player controls are retained from existing TubeDeck. Subtitles embedded into already downloaded YouTube files have not been independently verified.
- Physical Android TV remote-focus, performance, install-and-playback and actual airplane-mode validation must be performed on hardware. CI signing and emulator checks do not prove source online availability or content rights.

## Quality gates
- `OfflineVaultPolicyTest`: filters, pin order, title/collection search, no fixed GB quota, protected-source exclusion.
- Existing `SmartOfflineReplayPolicyTest`: no seeking, pause or resumed-watch eligibility.
- Full Java/Kotlin unit tests, Android lint, ARM64/ARMv7/Universal APK compilation, stable signing identity, APK ZIP integrity and ADB emulator installation.
- Manual acceptance: Wi-Fi→cellular mid-transfer, network restoration, SD/USB removal and reconnection, interrupted partial-file resumes, permitted complete cache promotion, airplane-mode playback, missing-file details, list/tile D-pad, Android share to Local-Beam receiver and uninstall/update behavior.
