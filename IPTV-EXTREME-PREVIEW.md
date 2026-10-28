# MyTube IPTV Extreme — integrated preview

This branch introduces an opt-in IPTV screen inside the existing MyTube launcher.
The home-screen **IPTV Extreme** shortcut opens an independent activity; existing
YouTube bookmarks, preferences, search, native playback and PiP routes remain
unchanged.

## Working preview capabilities

- Import a user-supplied M3U/M3U8 playlist from a URL or local document.
- Connect to a user-authorized Xtream provider's live stream list (not a bundled
  service or subscription); credentials are held in memory, not saved.
- Search and filter channels by group, see channel logos, and play streams using
  the existing Media3 stack plus DASH/RTSP add-ons.
- One-at-a-time player takeover, Android PiP when supported, playback error text.
- Ten **original** one-tap visual presets: Ocean Glass, Midnight Cinema,
  Aurora Borealis, Ruby Theatre, Royal Sapphire, Golden Lounge, Forest Retreat,
  Arctic Frost, Cyber Neon and Sunset Ember.
- Generated Waves, Aurora, Glow, Grid and Spotlight wallpaper styles, an
  optional personal image from the Android document picker, wallpaper dimming
  and text-size sliders. Settings persist independently from MyTube.
- Phone and widescreen/TV layouts; TV remote focus surfaces.

## Security, source integrity and current limitations

No channels or copyrighted streams are bundled. Use only playlists you own or
are authorized to access. HTTP sources are allowed for legacy IPTV servers,
but traffic and tokens on HTTP are unencrypted; prefer HTTPS providers.

This preview is **not a complete Xtream VOD/series/EPG/recording implementation**.
The initial Xtream path loads live channels only. The playlist cache is stored
as a local app-private file and can include credential-bearing channel URLs,
so use HTTPS and protect the device. Native APK tests do not establish
compatibility with every service, encoder, IPTV box or OEM firmware.
