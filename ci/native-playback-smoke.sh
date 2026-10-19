#!/usr/bin/env bash
set -Eeuo pipefail
on_error() {
  local code=$?
  adb logcat -d -s AndroidRuntime:E MyTubePlayback:I ExoPlayerImplInternal:E || true
  for report in dist/*-startup.txt; do
    test -f "$report" || continue
    echo "Activity report: $report"
    grep -E 'ResumedActivity|realActivity|mFocusedApp' "$report" || true
  done
  adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' || true
  exit "$code"
}
trap on_error ERR
assert_resumed() {
  local component="$1" report="$2"
  for attempt in $(seq 1 30); do
    timeout 15 adb shell dumpsys activity activities > "$report"
    if grep -Eq "(mResumedActivity|topResumedActivity).*${component}" "$report"; then
      grep -E 'ResumedActivity' "$report" || true
      return 0
    fi
    sleep 1
  done
  grep -E 'ResumedActivity' "$report" || true
  return 1
}
PACKAGE=com.aistudio.tubedeck.kxmvd
APK="$PWD/dist/MyTube-v1.8.8-XYAUTO-UNIVERSAL.apk"
adb wait-for-device
adb root
adb wait-for-device
adb install --no-incremental "$APK"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -W -n "$PACKAGE/com.drivedeck.tubedeck.MainActivity"
sleep 8
assert_resumed 'com.drivedeck.tubedeck.MainActivity' dist/main-startup.txt
PREFS="/data/user/0/$PACKAGE/shared_prefs/tubedeck_prefs.xml"
for profile in standard xyauto; do
  adb shell am force-stop "$PACKAGE"
  adb pull "$PREFS" "dist/$profile-prefs.xml"
  python3 - "$profile" <<'PY'
import sys
from pathlib import Path
import xml.etree.ElementTree as ET
profile = sys.argv[1]
path = Path(f'dist/{profile}-prefs.xml')
tree = ET.parse(path)
root = tree.getroot()
for node in list(root):
    if node.attrib.get('name') in {'xyAutoCompatibilityMode', 'playerMode', 'desktopYoutubeUiEnabled'}:
        root.remove(node)
ET.SubElement(root, 'boolean', name='xyAutoCompatibilityMode', value='true' if profile == 'xyauto' else 'false')
ET.SubElement(root, 'string', name='playerMode').text = 'NATIVE'
ET.SubElement(root, 'boolean', name='desktopYoutubeUiEnabled', value='false')
tree.write(path, encoding='utf-8', xml_declaration=True)
PY
  OWNER="$(adb shell stat -c %u "$PREFS" | tr -d '\r')"
  adb push "dist/$profile-prefs.xml" "$PREFS"
  adb shell chown "$OWNER:$OWNER" "$PREFS"
  adb shell restorecon "$PREFS"
  adb push ci/native-playback-fixture.mp4 "/data/user/0/$PACKAGE/files/native-playback-fixture.mp4"
  adb shell chown "$OWNER:$OWNER" "/data/user/0/$PACKAGE/files/native-playback-fixture.mp4"
  adb shell restorecon "/data/user/0/$PACKAGE/files/native-playback-fixture.mp4"
  adb logcat -c
  adb shell am start -W -n "$PACKAGE/com.drivedeck.tubedeck.NativePlayerActivity" \
    --es native_player_session_id "fixture-$profile" \
    --es native_player_media_id "fixture-$profile" \
    --es native_player_title NativePlaybackFixture \
    --es native_player_media_uri "file:///data/user/0/$PACKAGE/files/native-playback-fixture.mp4"
  for attempt in $(seq 1 20); do
    adb logcat -d -s MyTubePlayback:I > "dist/$profile-playback.txt"
    if grep -q 'first_frame' "dist/$profile-playback.txt"; then break; fi
    sleep 1
  done
  grep -q 'first_frame' "dist/$profile-playback.txt"
  grep -q 'audio_decoder' "dist/$profile-playback.txt"
  if [ "$profile" = xyauto ]; then
    grep -q 'player_created compat=true' "dist/$profile-playback.txt"
    grep -q 'video_surface TextureView' "dist/$profile-playback.txt"
    grep -Eq 'video_decoder (c2.android|OMX.google)' "dist/$profile-playback.txt"
  else
    grep -q 'player_created compat=false' "dist/$profile-playback.txt"
    grep -q 'video_surface SurfaceView' "dist/$profile-playback.txt"
  fi
  assert_resumed 'com.drivedeck.tubedeck.NativePlayerActivity' "dist/$profile-fixture-startup.txt"
  sleep 3
  adb shell dumpsys media_session > "dist/$profile-media-session.txt"
  cat "dist/$profile-media-session.txt"
  # This legacy system dump is diagnostic. A Media3 Activity does not promise a
  # particular platform-session representation; real decoder/frame events above
  # and the foreground Activity are the required playback checks.
  assert_resumed 'com.drivedeck.tubedeck.NativePlayerActivity' "dist/$profile-retained-startup.txt"
  APP_PID="$(adb shell pidof -s "$PACKAGE" | tr -d '\r')"
  adb logcat --pid="$APP_PID" -d > "dist/$profile-logcat.txt"
  if grep -Eq 'FATAL EXCEPTION|Fatal signal|VerifyError|NoSuchMethodError' "dist/$profile-logcat.txt"; then
    cat "dist/$profile-logcat.txt"
    exit 1
  fi
done
# Stream access can be blocked on CI IPs. Regardless of source availability, the native
# Activity must retain ownership and show a playback error instead of returning to search.
for video in aqz-KE-bpKQ jNQXAC9IVRw; do
  adb shell am start -W -n "$PACKAGE/com.drivedeck.tubedeck.MainActivity" \
    -a android.intent.action.VIEW -d "https://www.youtube.com/watch?v=$video"
  sleep 8
  assert_resumed 'com.drivedeck.tubedeck.NativePlayerActivity' "dist/xyauto-$video-startup.txt"
done
adb logcat -d -s MyTubePlayback:I > dist/XY-AUTO-playback-report.txt
echo 'PASS: standalone startup; actual first video frame and audio decoder in standard and XY AUTO compatibility modes; TextureView and software video decoder verified in XY AUTO mode; the native Activity stays in front after decoding and repeated online launches. Physical XY AUTO firmware and YouTube stream delivery require device testing.' | tee dist/Emulator-smoke-test.txt
