from pathlib import Path
root=Path("source/novaiptv/src/main/java/com/novastream/iptv")
p=root/"ui/PlayerActivity.java"
s=p.read_text(encoding="utf-8")
# Nova v0.1.4 has an explicit Catalog import; 0.1.5 imports core.*.
if "NovaPlaybackBridge.claim(player)" not in s:
    if "import com.novastream.iptv.core.*;" not in s and "import com.novastream.iptv.core.NovaPlaybackBridge;" not in s:
        anchor="import com.novastream.iptv.core.Catalog;"
        assert s.count(anchor)==1, "Missing Nova playback bridge import anchor"
        s=s.replace(anchor,anchor+"\nimport com.novastream.iptv.core.NovaPlaybackBridge;")
    if "player.addListener(listener);view.setPlayer(player);play(current);" in s:
        s=s.replace("player.addListener(listener);view.setPlayer(player);play(current);",
                    "player.addListener(listener);NovaPlaybackBridge.claim(player);view.setPlayer(player);play(current);")
    else:
        anchor="        player.addListener(listener);\n        view.setPlayer(player);"
        assert s.count(anchor)==1, "Unexpected Nova v0.1.5 player init"
        s=s.replace(anchor,"        player.addListener(listener);\n        NovaPlaybackBridge.claim(player);\n        view.setPlayer(player);")
    anchor="player.removeListener(listener);view.setPlayer(null);player.release();player=null;"
    assert s.count(anchor)==1, "Unexpected Nova player teardown"
    s=s.replace(anchor,"player.removeListener(listener);NovaPlaybackBridge.release(player);view.setPlayer(null);player.release();player=null;")
    s=s.replace("KEYCODE_HEADSETPHOOK","KEYCODE_HEADSETHOOK")
    p.write_text(s,encoding="utf-8")
assert "NovaPlaybackBridge.claim(player)" in s
assert "NovaPlaybackBridge.release(player)" in s
assert "KEYCODE_HEADSETPHOOK" not in s
