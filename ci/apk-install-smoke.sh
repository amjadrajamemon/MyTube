#!/usr/bin/env bash
# Android package-manager smoke test. Must run on a booted emulator via ADB.
# Tests the ACTUAL APK asset bytes, not a Gradle compilation shortcut.
set -Eeuo pipefail
APK="${1:?Pass Universal APK path}"
PACKAGE="com.aistudio.tubedeck.kxmvd"
OUT="${2:-dist}"
mkdir -p "$OUT"
test -s "$APK"
adb wait-for-device
adb shell getprop sys.boot_completed | tr -d '\r' | grep -qx 1
adb shell pm list packages >/dev/null

echo "Testing clean APK installation..."
adb install --no-incremental "$APK" | tee "$OUT/install-clean.log"
adb shell pm path "$PACKAGE" | tee "$OUT/installed-paths.txt"
grep -q "$PACKAGE" "$OUT/installed-paths.txt" || true
adb shell pm list packages | grep -Fqx "package:$PACKAGE"
adb shell dumpsys package "$PACKAGE" > "$OUT/package-dumpsys.txt"
grep -Fq "versionCode=117" "$OUT/package-dumpsys.txt"

echo "Testing update/reinstall with existing app data..."
adb install -r --no-incremental "$APK" | tee "$OUT/install-update.log"

echo "Testing application launch and foreground activity..."
adb logcat -c
adb shell am start -W -n "$PACKAGE/com.drivedeck.tubedeck.MainActivity" | tee "$OUT/launch.txt"
for attempt in $(seq 1 25); do
  adb shell dumpsys activity activities > "$OUT/activities.txt"
  if grep -Eq 'topResumedActivity|mResumedActivity' "$OUT/activities.txt" &&
     grep -Fq "com.drivedeck.tubedeck.MainActivity" "$OUT/activities.txt"; then
      break
  fi
  sleep 1
done
grep -Fq "com.drivedeck.tubedeck.MainActivity" "$OUT/activities.txt"
sleep 3
adb logcat -d -v threadtime > "$OUT/logcat-startup.txt"
if grep -A 12 'FATAL EXCEPTION\|Process: com.aistudio.tubedeck.kxmvd' "$OUT/logcat-startup.txt" | grep -q 'FATAL EXCEPTION'; then
  echo "::error::Fatal Android exception during first launch"
  exit 1
fi
echo "PASS: clean install, update install, manifest and app launch on emulator"
