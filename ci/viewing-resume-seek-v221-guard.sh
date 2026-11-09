#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
PLAYER=source/app/src/main/java/com/drivedeck/tubedeck/ui/screens/PlayerScreen.kt
SETTINGS=source/app/src/main/java/com/drivedeck/tubedeck/ui/screens/SettingsScreen.kt
MAIN=source/app/src/main/java/com/drivedeck/tubedeck/MainActivity.kt
VIDEO_STORE=source/app/src/main/java/com/drivedeck/tubedeck/player/VideoResumeStore.kt
POLICY=source/app/src/main/java/com/drivedeck/tubedeck/player/VideoResumePolicy.kt
SEEK=source/app/src/main/java/com/drivedeck/tubedeck/player/SeekPreviewPolicy.kt
THEMES=source/app/src/main/java/com/drivedeck/tubedeck/ui/theme/ThemeProfiles.kt
VIDEO_VIEW=source/app/src/main/res/layout/mytube_viewing_player_view.xml

test "$(grep -c 'DeckThemeProfile("' "$THEMES")" -eq 19
grep -Fq 'versionCode = 118' source/app/build.gradle.kts
grep -Fq 'versionName = "2.3.8"' source/app/build.gradle.kts
grep -Fq 'surface_type="texture_view"' "$VIDEO_VIEW"
grep -Fq 'key(carHeadUnit)' "$PLAYER"
! grep -Fq 'key(carHeadUnit, viewingMode != ViewingMode.NORMAL)' "$PLAYER"
grep -Fq 'mytube_viewing_player_view' "$PLAYER"
grep -Fq 'view.videoSurfaceView?.alpha = ViewingModePolicy.videoAlpha(' "$PLAYER"
grep -Fq 'activeViewingMode = mode' "$PLAYER"
grep -Fq 'onViewingPreferencesChanged(mode, safeDim, nightAudio)' "$PLAYER"
grep -Fq 'Applied to native video:' "$PLAYER"
grep -Fq 'Viewing mode: ' "$PLAYER"
grep -Fq 'PlayerViewingModesDialog(' "$PLAYER"
grep -Fq 'onViewingPreferencesChanged(mode, viewingDimPercent, nightAudioEnabled)' "$SETTINGS"
# No theme-specific fallback may prevent mode selection; both in-player dialogs
# and Settings use full-width touch/D-pad radio rows.
grep -Fq 'RadioButtonChecked' "$SETTINGS"
grep -Fq 'RadioButtonChecked' "$PLAYER"
grep -Fq 'val autoSaveDraft = currentSettings.copy(' "$SETTINGS"
grep -Fq 'LaunchedEffect(autoSaveDraft)' "$SETTINGS"
grep -Fq 'onSaveSettings(autoSaveDraft)' "$SETTINGS"
grep -Fq 'Settings are saved and applied automatically' "$SETTINGS"
! grep -Fq 'Save and Apply Settings' "$SETTINGS"
! grep -Fq 'onClick = { onPreviewSettings(appearanceOnlySettings()) }' "$SETTINGS"

grep -Fq 'onViewingPreferencesChanged = { mode, dim, nightAudio ->' "$MAIN"
grep -Fq 'selectedResumeStart.longValue' "$PLAYER"
grep -Fq 'VideoResumePolicy.mediaKey' "$VIDEO_STORE"
grep -Fq 'VideoResumePolicy.shouldSnapshot(' "$PLAYER"
grep -Fq 'SeekPreviewPolicy.frame(' "$PLAYER"
grep -Fq 'PointerEventType.Move' "$PLAYER"
grep -Fq 'remoteSeekPreviewSequence' "$PLAYER"
grep -Fq 'mytube_viewing_player_view' "$PLAYER"
grep -Fq 'enableScreenBlackout()' "$PLAYER"
grep -Fq 'controlsVisible = false' "$PLAYER"

# The single moon button is pinned BEFORE the horizontally scrollable controls.
python3 - <<'PY'
from pathlib import Path
p = Path('source/app/src/main/java/com/drivedeck/tubedeck/ui/screens/PlayerScreen.kt').read_text()
a = p.index('// Always visible in portrait; never hide Lights Out')
b = p.index('resolved?.chapters', a)
row = p[a:b]
assert row.count('Icons.Default.NightsStay') == 1, 'duplicate/hideable Lights Out toolbar action'
assert row.index('Icons.Default.NightsStay') < row.index('.horizontalScroll('), 'moon control must precede overflow strip'
assert 'onViewingPreferencesChanged(viewingMode, dimPreview, nightAudioEnabled)' not in row, 'old stale dropdown dismissal found'
assert 'val controllerScale = if (isPortrait)' in p, 'portrait buttons not scaled'
assert 'PlayerViewingModesDialog(' in p, 'selection dialog missing'
assert p.index('resumeStore.record(media.mediaUri, position, exoPlayer.duration)') < p.index('exoPlayer.release()'), 'resume snapshot must precede engine release'
assert 'onScrubChange = { scrubFraction = it }' not in p, 'remote seek source not separated'
print('PASS: 19-theme shared player, persistent portrait moon button, no stale dismissal, resume order, interactive previews')
PY
test -f source/app/src/test/java/com/drivedeck/tubedeck/player/VideoResumePolicyTest.kt
test -f source/app/src/test/java/com/drivedeck/tubedeck/player/SeekPreviewPolicyTest.kt
test -f source/app/src/test/java/com/drivedeck/tubedeck/ui/theme/ViewingModesAcrossThemesTest.kt
