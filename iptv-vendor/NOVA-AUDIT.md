# Source package audit — initial handoff

**Audit level:** local source/static review and host-JVM parser validation. **NOT** a fully compiled Android application, instrumented test run or device certification. Those checks require downloading Android SDK/Gradle dependencies on GitHub Actions or Android Studio and then using real devices.

## Implemented and inspected

| Audit check | Status | Notes |
|---|---|---|
| Repository structure | Static check | GitHub workflow, Android manifest, source, unit tests, docs |
| Local secrets in Git | Static scan | No real Xtream credentials or working private playlists shipped |
| Source URL exposure in UI | Reviewed | Manage-source labels redact username/password query parameters; avoid sharing logs |
| Local credentials at rest | Implemented; not device-tested | Android Keystore AES-256-GCM and atomic encrypted local catalog file; backups disabled |
| M3U parser bounds | Implemented; host test | 30k entries, 100k lines, URL scheme filter, 12 MiB input cap |
| Xtream endpoint ingestion | Implemented; not provider-tested | Live, VOD, series/episodes, category lookup/fallback |
| XMLTV ingestion | Implemented; host test | SAX DTD rejection, guide memory bounded by 48 hours and 100 programmes/channel |
| Network protections | Implemented; not stress-tested | Timeouts, 12/16/32 MiB limits; HTTP opt-in is permitted for IPTV compatibility |
| Artwork memory | Implemented; not stress-tested | Max 512 KiB/asset, downsample, 8 MiB LRU cache |
| Single live player / release | Inspected | Replaces MediaItem; releases ExoPlayer at Activity destroy; hardware/device tests pending |
| TV input | Inspected | LEANBACK launcher, remote focus, channel/media keys, remote button layout |
| PiP | Inspected | Available where device OS supports; background audio service NOT implemented |
| Themes / typography | Inspected | 16 named palettes + custom colors, 5 scales, 3 densities |
| End-to-end APK compilation | **NOT PERFORMED here** | Run the supplied GitHub Actions workflow; do not infer success |
| Native SRT, UDP, LibVLC, DRM, recording | **NOT IMPLEMENTED** | Explicitly deferred to roadmap |

## Risks requiring validation

- Certain vendor portals send unexpected JSON or require `.ts` instead of `.m3u8` for live streams.
- Redirect, token-based, self-signed TLS or proprietary header policies vary by provider.
- Large playlists and excessive thumbnail URLs require memory/performance profiling on low-RAM hardware.
- System-level PiP/audio focus and aggressive background restrictions differ across Android/TV devices.
- EPG ID matching and timezone accuracy require real-world guide samples.
- User-defined colors can reduce contrast; a WCAG-guided contrast preview is planned.
- Android 15+ edge-to-edge differences and Android TV focus dispatch need device-specific checks.

**Release decision:** suitable as an honest source-code foundation for uploading and running the first CI build; not yet certified as a production-ready, competitor-leading player.
