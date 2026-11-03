# MyTube / TubeDeck — Competitive TV Feature Audit and Roadmap
_Date: 2026-11-03. Audited branch: `feature/fix-next-autoplay-blackout-home-20261102` (PR #32)._

## What "floating cards" means — precise feature inventory

Do **not** equate three different kinds of video cards:

1. **Contextual related/episode suggestions**, displayed as thumbnail cards in the existing native player's floating `PlaybackContinuationCards`. **Already present**: the panel renders up to 12 relevant videos and up to 12 discovered episodes, with thumbnail, title, channel/duration and click-to-play. It is opened through the bottom-of-player upward swipe. The player also has a portrait `Up next` companion feed when configured.
2. **Preview-on-focus cards** (brief animated previews, optionally muted/audio), such as SmartTube's `VideoCardPresenter` / `PreviewCardView`. **Not present in the same way**: MyTube's `RelatedVideoCard` draws an `AsyncImage` static thumbnail, not a focus-activated video preview. Never start a second extractor/player/decoder for every focused card.
3. **Creator-authored YouTube timed 'i' cards and end-screen elements** (VIDEO, CHANNEL, PLAYLIST with timestamps and targeted navigation). **Not confirmed supported**: MyTube's `RelatedVideo` model and floating panel implement algorithmic recommendations, not YouTube's authored timed overlay metadata. A video may have no cards/end screen at all; do not invent them. SmartTube's GitHub issue #4935 (open as of 2026-11-03) also requests clickable authored end-screen/'i' cards; #5647 was marked completed but did not by itself establish working current behavior.

**Coverage caveats:** The existing suggestion panel is available only in the native player and when related-video data or episode discovery returns matches. `autoLoadUpNext` defaults to false to reduce CPU/network cost; `showRelevantShelf` and `showEpisodesShelf` default to true. Web-only embedded video may rely on its own player UI; it is not equivalent to native card rendering. Watch-Next fallback currently serves Next/Autoplay decisions, but it is not automatically merged into the floating recommendations shelf when `resolved.relatedVideos` is empty.

## Proposed order of implementation

### P1 — Safe, small, measurable improvements

- **Always-accessible TV recommendation tray:** a focusable 'More videos' action in the player controls, in addition to swipe-up, so basic D-pad-only remotes can open/close the existing floating cards. Honor previous focus and ensure Back returns to the same control. Lazy-render visible cards only. Acceptance: all cards reachable using D-pad, Enter, Back, mouse click and keyboard; no auto-open surprise.
- **On-demand Watch Next fallback for an empty tray:** call `YoutubeWatchNextClient.resolve` only when the user requests recommendations and extractor related items are absent; cap the response, deduplicate video IDs and exclude recently watched IDs. Cancel requests when the current media changes; display understandable empty/error states. Acceptance: no extra network calls when the tray is closed.
- **Configurable card behavior:** Off / Manual / Last 15 seconds, with Manual default. Never cover subtitle-safe region, Play/Pause, or seek controls. After the user dismisses the tray, do not reopen it for that video.
- **Session-resume safety:** save last visited screen, focused tile and time position in an atomic store; only restore if content still exists and the stored video ID is valid.

### P1 — Features many alternatives underdeliver

- **True clickable YouTube information and end-screen cards:** if and only if authenticated/officially accessible metadata provides element type, target ID, display geometry and start/end timestamps, render an optional, remote-focusable overlay inside the video safe area. Do not fabricate creator cards from recommendations. Provide an accessible list fallback for TV remotes and unlisted-video links. Seek and countdown must update visibility deterministically.
- **TV input diagnostics and learning mode:** display key codes, input device capabilities and focus transitions, with an exportable, consent-based diagnostic report. Offer reversible per-remote mappings for OK, Menu, Back, Rewind, FF, Channel and color keys. No accessibility service or broad device permissions.
- **Network degradation assistant:** short nontechnical playback diagnostics ('network unavailable', 'stream link expired', 'decoder rejected format'), recovery actions (retry / next stream format / audio-only), and retry caps. Avoid continuous background probes.
- **Bookmark moments with local notes:** jump to marked timestamps and optionally share a video+time link using QR; export/import as plain JSON. Store locally by default with simple deletion/privacy controls.
- **Resume after interruptions:** restore player/queue after a force close, power loss or car ignition interruption, with user confirmation and no duplicate MediaSession/audio instance.

### P2 — Optional competitive features (measure before enabling)

- **Focus video preview:** one *muted* preview at a time after a deliberate focus delay (e.g. 1.2 seconds), cancelled on focus loss; disabled on low-RAM/thermal-constrained TV boxes, when bandwidth is metered, while in blackout or audio-only, and while another decoder is active. Avoid added native binaries; enforce one-player memory budget and no unnecessary fetches. Disable by default.
- **Audio/subtitle calibration per device:** saved subtitle offset/font/safe margin; optional audio sync offset and dialog leveling if supported by current DSP; do not force new audio processors on old TV/car hardware.
- **Low Resource Mode:** tune decoder selection, image cache/thumbnail resolution, companion prefetch, optional dubbing components, animations and download concurrency based on RAM and thermal class, with transparent manual override.
- **Guest/private profile:** temporary watch history and no persistent search queries with clearly scoped deletion; optional child/family profile only with explicit safeguards and configuration.
- **Cross-device playback handoff:** local-network, permissionless-to-read-only QR handoff for video URL plus time, optional authentication for remote commands; avoid cloud account dependency.
- **Smart recommendations controls:** 'Not interested', 'Hide watched', 'Don't suggest this channel', and explicit Recently Seen exclusion on every suggestion surface, without pretending to retrain YouTube's algorithm.

## Features already in MyTube — avoid adding duplicates

- Native related/episode floating shelf, portrait Up Next companion panel, seek-preview thumbnails.
- Full-screen player controls, quality/subtitle/speed, dedicated TV remote/air-mouse/keyboard policy.
- Manual Next/Autoplay, background audio, PiP, blackout with single-tap wake, SponsorBlock.
- Download governor, offline media, multi-ABI APK publishing, optional local Live Dub.

## Engineering principles / acceptance

- Progressive disclosure; no permanent clutter beyond the chosen History | Relevant | Trending Home.
- No auto-previews on initial load; cancel work on tab changes, Back, media switch or app backgrounding.
- No hidden timers while paused; no unbounded caches or queues.
- Test low-end Android TV devices, 32-bit Universal, 64-bit ARM64, car head units, keyboard, air mouse, old SDKs, and accessibility readers.
- Treat extractor/YouTube metadata as untrusted: bound list sizes, validate target URLs/IDs and timestamps.
- Keep release signing identity stable; build code 102 v1.9.7 stays dedicated to stability unless a specific feature's tests are ready.
- Never mark features 'available on every video': source metadata, permissions and creator-specific authorship can be absent.

## External reference links

- SmartTube card presenter: https://github.com/yuliskov/SmartTube/blob/master/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/presenter/VideoCardPresenter.java
- SmartTube clickable creator-authored cards request: https://github.com/yuliskov/SmartTube/issues/4935
- SmartTube separate end-screen request: https://github.com/yuliskov/SmartTube/issues/5647
- MyTube current player implementation: `source/app/src/main/java/com/drivedeck/tubedeck/ui/screens/PlayerScreen.kt`
