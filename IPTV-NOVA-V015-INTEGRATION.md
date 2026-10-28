# MyTube 1.9.2 — Nova IPTV v0.1.5 integrated

This edition upgrades the optional Nova IPTV Pro engine from v0.1.4 to the exact full-source v0.1.5 snapshot released by the user's [IPTV repository](https://github.com/amjadrajamemon/IPTV/releases/tag/v0.1.5-debug-preview). The full Java sources, unit tests, source archive and provenance remain editable and included. The independent MyTube IPTV Extreme themes/wallpaper screen also remains available.

## Features
- Live, Movies and Series tabs, Xtream-compatible portal import, M3U playlists (multiple account/provider support), XMLTV EPG, playlists management and ordered favourites
- VOD retrieval fallback using the *same authorized provider's* M3U when the provider refuses Xtream VOD API; movie and series categorization by groups and source URLs
- Responsive movies/series poster grid by default, manual List/Grid switching, TV D-pad and air-mouse UI, compact headers and improved empty-state and count labels
- Media3 live TS/HLS format retry, bounded network reconnect, playback diagnostics and compatible external-player option
- 16 Nova color themes, custom accent/background/text size, plus original MyTube IPTV Extreme 10 wallpaper/theme presets and photo wallpaper editor
- Cross-module `NovaPlaybackBridge` stops the prior Nova player when another player takes over; MyTube YouTube navigation and background media are preserved

No IPTV channels or provider credentials are bundled. User playback on a specific provider and real device must be tested before claiming provider compatibility. Recording and DRM bypass are not included.
