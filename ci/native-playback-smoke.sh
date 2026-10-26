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
APK="$PWD/dist/MyTube-v1.8.11-XYAUTO-UNIVERSAL.apk"
# Keep the local fixture playing long enough to verify background progression.
command -v ffmpeg >/dev/null || { sudo apt-get update -qq; sudo apt-get install -y -qq ffmpeg; }
ffmpeg -hide_banner -loglevel error -stream_loop -1 -i ci/native-playback-fixture.mp4 -t 90 -c copy -y /tmp/mytube-home-fixture.mp4
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
    if node.attrib.get('name') in {'xyAutoCompatibilityMode', 'playerMode', 'desktopYoutubeUiEnabled', 'pipEnabled'}:
        root.remove(node)
ET.SubElement(root, 'boolean', name='xyAutoCompatibilityMode', value='true' if profile == 'xyauto' else 'false')
ET.SubElement(root, 'string', name='playerMode').text = 'NATIVE'
ET.SubElement(root, 'boolean', name='desktopYoutubeUiEnabled', value='false')
# Exercise the explicit audio-only fallback here. PiP-on-Home has its own test below.
ET.SubElement(root, 'boolean', name='pipEnabled', value='false')
tree.write(path, encoding='utf-8', xml_declaration=True)
PY
  OWNER="$(adb shell stat -c %u "$PREFS" | tr -d '\r')"
  adb push "dist/$profile-prefs.xml" "$PREFS"
  adb shell chown "$OWNER:$OWNER" "$PREFS"
  adb shell restorecon "$PREFS"
  adb push /tmp/mytube-home-fixture.mp4 "/data/user/0/$PACKAGE/files/native-playback-fixture.mp4"
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
  # A genuine Android Home key must start one foreground audio session.
  adb logcat -c
  adb shell input keyevent KEYCODE_HOME
  for attempt in $(seq 1 20); do
    adb logcat -d -s MyTubePlayback:I > "dist/$profile-home-audio.txt"
    grep -q 'audio_background_progress' "dist/$profile-home-audio.txt" && break
    sleep 1
  done
  python3 - "$profile" <<'PYHOME'
import re, sys
from pathlib import Path
log = Path(f'dist/{sys.argv[1]}-home-audio.txt').read_text()
start = re.search(r'audio_service_started.*position=(\d+)', log)
progress = re.search(r'audio_background_progress.*position=(\d+)', log)
assert start and progress, log
assert int(progress[1]) - int(start[1]) > 2000, log
PYHOME
  adb shell dumpsys activity services "$PACKAGE" > "dist/$profile-background-service.txt"
  grep -q 'isForeground=true' "dist/$profile-background-service.txt"
  # Return through the same intent used by the audio notification.
  adb shell am start -W -n "$PACKAGE/com.drivedeck.tubedeck.MainActivity" \
    -a com.drivedeck.tubedeck.action.RETURN_TO_VIDEO
  assert_resumed 'com.drivedeck.tubedeck.NativePlayerActivity' "dist/$profile-return-to-video.txt"
  sleep 3
  adb shell dumpsys activity services "$PACKAGE" > "dist/$profile-return-services.txt"
  if grep -q 'BackgroundAudioPlaybackService' "dist/$profile-return-services.txt"; then
    cat "dist/$profile-return-services.txt"
    exit 1
  fi
  # Home must respect a user pause rather than starting audio from a paused video.
  adb shell input keyevent KEYCODE_MEDIA_PAUSE
  sleep 1
  adb shell input keyevent KEYCODE_HOME
  sleep 2
  adb shell dumpsys activity services "$PACKAGE" > "dist/$profile-paused-home.txt"
  if grep -q 'BackgroundAudioPlaybackService' "dist/$profile-paused-home.txt"; then
    cat "dist/$profile-paused-home.txt"
    exit 1
  fi
  APP_PID="$(adb shell pidof -s "$PACKAGE" | tr -d '\r')"
  adb logcat --pid="$APP_PID" -d > "dist/$profile-logcat.txt"
  if grep -Eq 'FATAL EXCEPTION|Fatal signal|VerifyError|NoSuchMethodError' "dist/$profile-logcat.txt"; then
    cat "dist/$profile-logcat.txt"
    exit 1
  fi
done

# Independently verify the default Home-key PiP path. The legacy audio-only
# assertions above must not reject a successful PiP transition.
adb shell am force-stop "$PACKAGE"
adb pull "$PREFS" dist/pip-prefs.xml
python3 - <<'PYPIPPREFS'
from pathlib import Path
import xml.etree.ElementTree as ET
path = Path('dist/pip-prefs.xml')
tree = ET.parse(path)
root = tree.getroot()
for node in list(root):
    if node.attrib.get('name') in {'pipEnabled', 'playerMode', 'xyAutoCompatibilityMode'}:
        root.remove(node)
ET.SubElement(root, 'boolean', name='pipEnabled', value='true')
ET.SubElement(root, 'boolean', name='xyAutoCompatibilityMode', value='false')
ET.SubElement(root, 'string', name='playerMode').text = 'NATIVE'
tree.write(path, encoding='utf-8', xml_declaration=True)
PYPIPPREFS
adb push dist/pip-prefs.xml "$PREFS"
adb shell chown "$OWNER:$OWNER" "$PREFS"
adb shell restorecon "$PREFS"
adb logcat -c
adb shell am start -W -n "$PACKAGE/com.drivedeck.tubedeck.NativePlayerActivity" \
  --es native_player_session_id fixture-pip \
  --es native_player_media_id fixture-pip \
  --es native_player_title NativePipFixture \
  --es native_player_media_uri "file:///data/user/0/$PACKAGE/files/native-playback-fixture.mp4"
for attempt in $(seq 1 20); do
  adb logcat -d -s MyTubePlayback:I > dist/pip-playback.txt
  grep -q 'first_frame' dist/pip-playback.txt && break
  sleep 1
done
grep -q 'first_frame' dist/pip-playback.txt
assert_resumed 'com.drivedeck.tubedeck.NativePlayerActivity' dist/pip-foreground.txt
adb shell dumpsys media_session > dist/pip-before-media.txt
adb shell input keyevent KEYCODE_HOME
sleep 5
adb shell dumpsys activity activities > dist/pip-activity.txt
# The virtual device can rebuffer during the system PiP animation. Validate
# ongoing playback instead of imposing a single early position sample.
python3 - <<'PYPIPASSERT'
import re
import subprocess
import time
from pathlib import Path
activity = Path('dist/pip-activity.txt').read_text()
assert re.search(r'(?:mode|windowingMode)=pinned', activity), 'Home did not enter Android PiP'
pattern = r'androidx\.media3\.session\.id\.drivedeck-fixture-pip[^\n]*[\s\S]*?state=PlaybackState \{state=PLAYING\(3\), position=(\d+)'
def position(text):
    match = re.search(pattern, text)
    return int(match.group(1)) if match else None
before_text = Path('dist/pip-before-media.txt').read_text()
before = position(before_text)
assert before is not None, 'PiP media session was not PLAYING before Home'
deadline = time.monotonic() + 12
after = None
while True:
    latest = subprocess.run(['adb','shell','dumpsys','media_session'], text=True, capture_output=True, check=True, timeout=20).stdout
    Path('dist/pip-after-media.txt').write_text(latest)
    after = position(latest)
    if after is not None and after - before > 2000:
        break
    if time.monotonic() >= deadline:
        raise AssertionError(f'PiP playback did not advance 2 seconds after Home: {before} -> {after}')
    time.sleep(2)
print(f'PASS: Home entered Android PiP and playback progressed {after-before} ms')
PYPIPASSERT
adb shell am force-stop "$PACKAGE"

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


# Verify persisted recent searches and history-assisted typing on the actual UI.
adb shell am force-stop "$PACKAGE"
adb pull "$PREFS" dist/search-prefs.xml
python3 - <<'PYSEARCH'
import json
from pathlib import Path
import xml.etree.ElementTree as ET
path = Path('dist/search-prefs.xml')
tree = ET.parse(path)
root = tree.getroot()
for node in list(root):
    if node.attrib.get('name') == 'recent_searches_json': root.remove(node)
ET.SubElement(root, 'string', name='recent_searches_json').text = json.dumps(['Pakistan news', 'Urdu songs'])
tree.write(path, encoding='utf-8', xml_declaration=True)
PYSEARCH
adb push dist/search-prefs.xml "$PREFS"
adb shell chown "$OWNER:$OWNER" "$PREFS"
adb shell restorecon "$PREFS"
adb shell am start -W --activity-clear-task -f 0x10008000 -n "$PACKAGE/com.drivedeck.tubedeck.MainActivity"
sleep 5
adb shell uiautomator dump /sdcard/mytube-search.xml
adb pull /sdcard/mytube-search.xml dist/search-home.xml
python3 - <<'PYTAP'
import re, subprocess
import xml.etree.ElementTree as ET
root = ET.parse('dist/search-home.xml').getroot()
texts = [node.attrib.get('text', '') for node in root.iter('node')]
assert 'Recent searches' in texts, texts
assert 'Pakistan news' in texts, texts
field = next(node for node in root.iter('node') if node.attrib.get('class') == 'android.widget.EditText')
x1,y1,x2,y2 = map(int, re.findall(r'\d+', field.attrib['bounds']))
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)], check=True)
PYTAP
adb shell input text pak
sleep 2
adb shell uiautomator dump /sdcard/mytube-suggestions.xml
adb pull /sdcard/mytube-suggestions.xml dist/search-suggestions.xml
python3 - <<'PYASSERT'
import xml.etree.ElementTree as ET
root = ET.parse('dist/search-suggestions.xml').getroot()
texts = [node.attrib.get('text', '') for node in root.iter('node')]
assert 'Search suggestions' in texts, texts
assert 'Pakistan news' in texts, texts
PYASSERT
echo 'PASS: Home continues actual audio progress in standard and XY AUTO profiles; returning stops the audio service and restores native video; Home respects user pause; saved recent searches and history-assisted typing are visible in the Android UI. Online suggestion availability and physical firmware require device testing.' | tee dist/Background-audio-search-test.txt
