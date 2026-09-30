# MyTube r6.5 LiveDub v1.2.0 revision

Implemented in this source revision:
- Single Home search field; removed the duplicate search action/prompt area.
- Refined Home landing panel.
- Portrait native-player video area enlarged from 16:9 to 16:10, leaving less space to the companion/comments panel.
- Comments remain one native panel directly below the video and do not navigate to another video.
- Back behavior retains the priority chain: dismiss settings -> hide comments -> exit fullscreen -> close player.
- Double-tap seek, hold seek and two-finger pinch seek remain enabled.
- Auto quality no longer destroys/rebuilds playback every nine seconds. It makes a bandwidth-informed selection at load/re-selection and remains stable, preventing recurrent blank frames/decoder churn.
- The quality control opens Player Settings, where Auto plus every resolved video height is presented explicitly.
- Live Dub target languages remain the top 10 set: English, Urdu, Hindi, Arabic, Spanish, French, German, Portuguese, Russian and Chinese.

Important build note:
The execution environment used to prepare this revision could not reach services.gradle.org, so Gradle could not download 8.11.1 and a clean APK compile could not be completed here. The included compile_apk.bat is retained for compilation on the existing Android Studio/JDK/SDK machine.

Live Dub architecture note:
The existing v1.1.1 engine uses caption cues + ML Kit translation + local Android TTS. The UI is prepared for an audio-first sentence pipeline, but a true offline audio ASR path requires shipping/integrating an ASR runtime and model (for example whisper.cpp plus model assets). This revision does not falsely bundle a model that is absent from the source archive.
