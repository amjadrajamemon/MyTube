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
grep -Fq 'versionCode = 121' source/app/build.gradle.kts
grep -Fq 'versionName = "2.3.11"' source/app/build.gradle.kts

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
# Compact recent-search row is horizontally scrollable and every term submits
# immediately via the same handler as tapping the keyboard Search action.
grep -Fq 'LazyRow(' "$SEARCH"
grep -Fq 'recentSearches.distinct().take(12)' "$SEARCH"
grep -Fq 'onClick = { submit(term) }' "$SEARCH"
grep -Fq 'onSubmit(chosen)' "$SEARCH"
grep -Fq 'onDismiss()' "$SEARCH"
! grep -Fq 'itemsIndexed(recentSearches' "$SEARCH"
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

# Prevent two regressions: unrelated video resume popups, and intermittent
# playback repeatedly trying the same incompatible high-res URL.
RESUME=source/app/src/main/java/com/drivedeck/tubedeck/player/VideoResumePolicy.kt
STORE=source/app/src/main/java/com/drivedeck/tubedeck/player/VideoResumeStore.kt
OPTIONS=source/app/src/main/java/com/drivedeck/tubedeck/player/AutoPlayableOptionsPolicy.kt
grep -Fq 'const val DEFAULT_PROMPT_SECONDS = 3' "$RESUME"
grep -Fq 'fun shouldOfferPrompt(' "$RESUME"
grep -Fq 'VideoResumePolicy.shouldOfferPrompt(' "$STORE"
grep -Fq 'resumeDecisionSeconds = settings.resumeDecisionSeconds' source/app/src/main/java/com/drivedeck/tubedeck/MainActivity.kt
grep -Fq 'resumeCountdownSeconds' "$PLAYER"
grep -Fq 'autoDownshifted = true' "$PLAYER"
grep -Fq 'source.downloadOptions' "$PLAYER"
grep -Fq 'AutoPlayableOptionsPolicy.choices(' "$PLAYER"
grep -Fq 'downloadOptions' "$OPTIONS"
test -f source/app/src/test/java/com/drivedeck/tubedeck/player/AutoPlayableOptionsPolicyTest.kt
test -f source/app/src/test/java/com/drivedeck/tubedeck/player/VideoResumePolicyTest.kt

# Installability is more than an APK ZIP check; force real ARMv7 support
# and preserve legacy-compatible signature configurations.
GRADLE=source/app/build.gradle.kts
grep -Fq 'create("armv7")' "$GRADLE"
grep -Fq 'enableV1Signing = true' "$GRADLE"
grep -Fq 'enableV2Signing = true' "$GRADLE"
grep -Fq 'enableV3Signing = true' "$GRADLE"
echo "PASS: v2.3.11, 3s resume, Auto downshift, compact search, ARMv7 and legacy-friendly signing"
