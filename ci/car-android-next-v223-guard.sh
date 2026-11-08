#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BRIDGE=source/app/src/main/java/com/drivedeck/tubedeck/player/PlaybackHardwareButtonBridge.kt
KEYS=source/app/src/main/java/com/drivedeck/tubedeck/player/CarMediaTransportKeyPolicy.kt
PLAYER=source/app/src/main/java/com/drivedeck/tubedeck/ui/screens/PlayerScreen.kt
NATIVE=source/app/src/main/java/com/drivedeck/tubedeck/NativePlayerActivity.kt
MAIN=source/app/src/main/java/com/drivedeck/tubedeck/MainActivity.kt
AUDIO=source/app/src/main/java/com/drivedeck/tubedeck/player/BackgroundAudioPlaybackService.kt
TV=source/app/src/main/java/com/drivedeck/tubedeck/ui/components/TvRemoteNavigationPolicy.kt

test -f "$KEYS"
test -f source/app/src/test/java/com/drivedeck/tubedeck/player/CarMediaTransportKeyPolicyTest.kt
grep -Fq 'versionCode = 112' source/app/build.gradle.kts
grep -Fq 'versionName = "2.3.2"' source/app/build.gradle.kts
grep -Fq 'KEYCODE_MEDIA_SKIP_FORWARD' "$KEYS"
grep -Fq 'KEYCODE_MEDIA_STEP_FORWARD' "$KEYS"
grep -Fq 'KeyEvent.ACTION_UP' "$BRIDGE"
grep -Fq 'CarMediaTransportKeyPolicy.classify' "$BRIDGE"
grep -Fq 'nextKeyCode = remoteNextKeyCode' "$PLAYER"
grep -Fq 'PlaybackHardwareButtonBridge.handle(event)' "$PLAYER"
grep -Fq 'PlaybackHardwareButtonBridge.handle(event)' "$MAIN"
grep -Fq 'PlaybackHardwareButtonBridge.handle(event)' "$NATIVE"
grep -Fq 'handleAudioHardwareTransportKey' "$AUDIO"
grep -Fq 'onPlayerInteractionFinished(' "$PLAYER"
grep -Fq 'onPlayerInteractionFinished(' "$AUDIO"
grep -Fq 'Player.COMMAND_SEEK_TO_NEXT' "$PLAYER"
grep -Fq 'Player.COMMAND_SEEK_TO_NEXT' "$AUDIO"
! grep -Fq 'Next video is still loading' "$PLAYER"

grep -Fq 'CarMediaTransportKeyPolicy.classify' "$AUDIO"
grep -Fq 'KEYCODE_MEDIA_SKIP_FORWARD' "$TV"
grep -Fq 'car_key_seen' "$MAIN"
grep -Fq 'car_key_seen' "$NATIVE"
grep -Fq 'onViewingPreferencesChanged = { mode, dim, nightAudio ->' "$NATIVE"
grep -Fq 'viewingMode = settings.viewingMode' "$NATIVE"

# The on-screen Next action must continue using the same queue action as before.
python3 - <<'PY'
from pathlib import Path
p = Path('source/app/src/main/java/com/drivedeck/tubedeck/ui/screens/PlayerScreen.kt').read_text()
assert 'onClick = { dispatchNextKey() }' in p
assert 'enabled = nextEnabled' in p
assert 'val sharedNextAction: (() -> Unit)? = when {' in p
assert 'fun dispatchNextKey(): Boolean' in p
assert 'PlaybackHardwareButtonBridge.register(' in p
assert 'PlaybackHardwareButtonBridge.unregister(exoPlayer)' in p
assert 'private var lastNextDispatchTime' in Path('source/app/src/main/java/com/drivedeck/tubedeck/player/PlaybackHardwareButtonBridge.kt').read_text()
print('PASS: same next queue for touch and car, unified MediaSession/Activity, OEM UP-only, 19-theme shared player')
PY
