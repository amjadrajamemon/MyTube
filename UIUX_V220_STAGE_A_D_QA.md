# MyTube v2.2.0 — Stage A–D UI/UX modernization and validation

**Scope:** UI, presentation, styling, theme preview, focus/hover, navigation persistence, safe-area consistency, motion budgets. No new streaming/downloading/playback features. Existing v2.1.0 Lights Out, Cinema, Night, Night Audio and blackout retained.

## Stage A: shared design system
- `DeckExperienceSystem.kt`: 13 explicit backdrop/style identities, bounded focus scale, hover feedback, animation duration and GPU-only atmosphere.
- App-wide `LocalDeckExperience` provider, respecting saved Low Resource Mode, device low RAM and Android's animation-disable setting.
- Keep existing `DeckThemeProfile` layouts, navigation placement and differentiated player chrome rather than flattening/replacing them.

## Stage B: theme differentiation
- `DeckVisualLayers.kt`: lightweight background atmosphere for Home, Settings and Downloads; artwork-derived upper Home halo only on eligible themes, using capped-size (360×210) thumbnail decode and faint overlay.
- Existing 13 structural Home layouts, navigation geometries and player-chrome styles remain intact. The theme picker displays architecture, theme description and player-style identity.
- Full-black background in VLC-like mode stays flat; desktop/minimal modes remain visually restrained. Backgrounds do not trap touch or D-pad focus.

## Stage C: navigation and presentation motion
- Shared `tvInputHighlight` now animates bounded scale/border/hover; no layout size changes for spatial D-pad search. Low RAM/reduced motion causes no animation or enlargement.
- Player show/hide transitions use the same render budget as the Home/nav UI.
- Save Home vertical LazyColumn position; existing feed/item focused-ID recovery and TV focus policies retained.
- Navigation selection animation obeys device motion policy, and labels remain single-line with ellipsis.

## Stage D: automatic checks
- JUnit: `DeckExperienceCatalogTest` tests all 13 unique styles, low-resource disable, reduced motion, conservative scaling and bounded timing.
- Existing `ThemeProfilesTest`, `HomeLayoutPolicyTest`, `TvRemoteNavigationPolicyTest`, signing/ABI checks and both product APK unit-test variants remain required.
- `ci/uiux-v220-guard.sh` performs static architecture/inset/headings/playback-retention checks.
- Android lint runs as an audit artifact; review any findings (the existing lint job is non-blocking).
- CI assembles ARM64 and Universal debug-signed APKs, verifies ZIP/ABI/signatures and produces SHA256 checksums.

## Physical QA to perform before marking release stable

| Device | Theme/sample | Visual and input acceptance |
|---|---|---|
| Android TV 1080p and 4K | ATV, SmartTube Pro, Plex Cinema | All four D-pad directions, center, Back, Home-to-player-to-Home focus, artwork atmosphere, shelf scroll |
| AOSP TV box (2 GB RAM) | Cyberpunk, Xtreamity, Kodi Arctic | Low-resource mode degrades gracefully; no stuck focus; no needless bitmap churn |
| Android phone portrait | YouTube Standard, Material 3, Apple TV Inspired | Notch/cutout, top/search placement, swipe Home panels, card/list toggling |
| Android phone landscape | MX Player, VLC Classic, Plex Cinema | Spinner aligned with play, immersive bars, black screen control, animation/focus |
| Android car / XY AUTO | Linux Desktop, VLC Classic | Non-distracting focus, safe controls, no unnecessary image animation |
| USB keyboard, air mouse, D-pad remote | All 13 | Pointer hover and keyboard focus, navigation to/from Settings/Downloads, no double Back actions |
| Light theme/large font | Material 3, Apple TV Inspired | Text contrast, ellipsis, no clipped headings even at 135% text setting |

**Important:** These physical-device and screenshot checks are a documented acceptance matrix, not claimed to have been executed by CI. Unit tests, lint, builds and package verification do **not** prove real-device visual quality. Treat GitHub Release as a prerelease until physical QA passes.
