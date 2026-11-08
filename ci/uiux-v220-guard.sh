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
grep -Fq 'versionCode = 112' source/app/build.gradle.kts
grep -Fq 'versionName = "2.3.2"' source/app/build.gradle.kts

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

# Landscape search must not expand Home/browser content vertically.
SEARCH=source/app/src/main/java/com/drivedeck/tubedeck/ui/components/SearchAssistField.kt
POLICY=source/app/src/main/java/com/drivedeck/tubedeck/ui/components/SearchCompactPolicy.kt
grep -Fq 'compactLandscape = compactLandscapeHeader' "$HOME"
grep -Fq 'if (compactLandscapeHeader)' "$TOP"
grep -Fq 'Popup(' "$SEARCH"
grep -Fq 'if (!compactLandscape && hasSearchFocus && choices.isNotEmpty())' "$SEARCH"
test -f "$POLICY"
test -f source/app/src/test/java/com/drivedeck/tubedeck/ui/components/SearchCompactPolicyTest.kt

echo "PASS: v2.3.2, 19 themes, short-landscape compact search overlay, backdrop, focus, insets, blackout"
