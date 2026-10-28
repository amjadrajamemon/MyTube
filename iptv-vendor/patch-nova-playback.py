from pathlib import Path
root=Path("source/novaiptv/src/main/java/com/novastream/iptv")
p=root/"ui/PlayerActivity.java"
s=p.read_text(encoding="utf-8")
if "NovaPlaybackBridge.claim(player)" not in s:
    old="import com.novastream.iptv.core.Catalog;"
    assert s.count(old)==1
    s=s.replace(old,old+"\nimport com.novastream.iptv.core.NovaPlaybackBridge;")
    old="player.addListener(listener);view.setPlayer(player);play(current);"
    assert s.count(old)==1
    s=s.replace(old,"player.addListener(listener);NovaPlaybackBridge.claim(player);view.setPlayer(player);play(current);")
    old="player.removeListener(listener);view.setPlayer(null);player.release();player=null;"
    assert s.count(old)==1
    s=s.replace(old,"player.removeListener(listener);NovaPlaybackBridge.release(player);view.setPlayer(null);player.release();player=null;")
    p.write_text(s,encoding="utf-8")
