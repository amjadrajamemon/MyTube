#!/usr/bin/env bash
set -euo pipefail

# Static complements to Android/Kotlin unit tests. Physical emulator/device
# screenshot and focus-path validation is documented separately, not faked here.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
THEMES=source/app/src/main/java/com/drivedeck/tubedeck/ui/theme/ThemeProfiles.kt
HOME=source/app/src/main/java/com/drivedeck/tubedeck/ui/screens/DriveDeckScreen.kt
PLAYER=source/app/src/main/java/com/drivedeck/tubedeck/ui/screens/PlayerScreen.kt
FOCUS=source/app/src/main/java/com/drivedeck/tubedeck/ui/components/TvInputSupport.kt
NAV=source/app/src/main/java/com/drivedeck/tubedeck/ui/components/TubeDeckBottomBar.kt
SETTINGS=source/app/src/main/java/com/drivedeck/tubedeck/ui/screens/SettingsScreen.kt
DL=source/app/src/main/java/com/drivedeck/tubedeck/ui/screens/DownloadsScreen.kt
TOP=source/app/src/main/java/com/drivedeck/tubedeck/ui/components/TopNavBar.kt
EXPERIENCE=source/app/src/main/java/com/drivedeck/tubedeck/ui/theme/DeckExperienceSystem.kt

test "$(grep -c 'DeckThemeProfile("' "$THEMES")" -eq 19
grep -Fq 'versionCode = 114' source/app/build.gradle.kts
grep -Fq 'versionName = "2.3.4"' source/app/build.gradle.kts

grep -Fq 'DeckExperienceCatalog.tokens' source/app/src/main/java/com/drivedeck/tubedeck/ui/theme/Theme.kt
grep -Fq 'deckScreenBackground()' "$HOME"
grep -Fq 'DeckArtworkHalo(' "$HOME"
grep -Fq 'deckScreenBackground()' "$SETTINGS"
grep -Fq 'deckScreenBackground()' "$DL"

grep -Fq 'rememberLazyListState()' "$HOME"
grep -Fq 'maxLines = 1' "$HOME"
grep -Fq 'softWrap = false' "$HOME"
grep -Fq 'tvInputHighlight' "$FOCUS"
grep -Fq 'LocalDeckExperience.current' "$FOCUS"
grep -Fq 'LocalDeckExperience.current.motionDurationMs' "$PLAYER"
grep -Fq 'LocalDeckExperience.current.motionDurationMs' "$NAV"

for file in "$HOME" "$SETTINGS" "$DL" "$TOP" "$PLAYER"; do
  grep -Fq 'WindowInsets.safeDrawing' "$file"
done

grep -Fq 'lowResourceMode = settings.lowResourceMode' source/app/src/main/java/com/drivedeck/tubedeck/MainActivity.kt
grep -Fq 'nightAudioEnabled = settings.nightAudioEnabled' source/app/src/main/java/com/drivedeck/tubedeck/MainActivity.kt
grep -Fq 'enableScreenBlackout()' "$PLAYER"

# On-demand search in all orientations. An editable field or history list
# must not remain mounted inside Home/browser headers on application launch.
SEARCH=source/app/src/main/java/com/drivedeck/tubedeck/ui/components/CompactSearchPanel.kt
POLICY=source/app/src/main/java/com/drivedeck/tubedeck/ui/components/SearchCompactPolicy.kt
grep -Fq 'CompactSearchPanel(' "$HOME"
grep -Fq 'CompactSearchPanel(' "$TOP"
grep -Fq 'searchDialogMode = "search"' "$HOME"
grep -Fq 'searchDialogMode = "history"' "$HOME"
grep -Fq 'searchDialogMode = null' "$HOME"
grep -Fq 'WindowInsets.ime.getBottom(density)' "$SEARCH"
grep -Fq 'if (startWithKeyboard)' "$SEARCH"
grep -Fq 'LaunchedEffect(configuration.orientation)' "$TOP"
! sed -n '/fun DriveDeckTopHeader(/,$p' "$HOME" | grep -Fq 'SearchAssistField('
! grep -Fq 'SearchAssistField(' "$TOP"
test -f "$POLICY"
test -f source/app/src/test/java/com/drivedeck/tubedeck/ui/components/SearchCompactPolicyTest.kt
test -f source/app/src/test/java/com/drivedeck/tubedeck/ui/components/CompactSearchOverlayPolicyTest.kt

# Titles and stream resilience share testable policies; no tiny, hardcoded
# 12sp Home video titles and no resource-mode suppression of downshifts.
TITLE=source/app/src/main/java/com/drivedeck/tubedeck/ui/components/VideoTitleTypography.kt
ADAPTIVE=source/app/src/main/java/com/drivedeck/tubedeck/player/AdaptiveQualityPolicy.kt
test -f "$TITLE"
test -f "$ADAPTIVE"
grep -Fq 'ReadableVideoTitle(video.title)' "$HOME"
grep -Fq 'ReadableVideoMetadata(video.channelTitle' "$HOME"
grep -Fq 'videoTitleScale' "$SETTINGS"
grep -Fq 'AdaptiveQualityPolicy.decide(' "$PLAYER"
grep -Fq 'allowUpgrade = resourceLimits.allowAdaptiveUpgrade' "$PLAYER"
test -f source/app/src/test/java/com/drivedeck/tubedeck/ui/components/VideoTitleTypographyPolicyTest.kt
test -f source/app/src/test/java/com/drivedeck/tubedeck/player/AdaptiveQualityPolicyTest.kt

echo "PASS: v2.3.4, on-demand search, no startup keyboard, 19 themes, readable titles, adaptive quality"
