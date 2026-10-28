package com.novastream.iptv.core;

import com.novastream.iptv.model.Channel;
import org.json.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Xtream-compatible portal reader. Supports live, VOD and series episode retrieval. */
public final class Xtream {
    private final String server, user, password;
    private final List<String> warnings=new ArrayList<>();
    public String warnings(){return String.join("; ",warnings);}
    public Xtream(String host, String user, String pass) {
        String s=host.trim().replaceAll("/+$", "");
        if (!(s.startsWith("http://") || s.startsWith("https://"))) throw new IllegalArgumentException("Server must start with http:// or https://");
        this.server=s; this.user=user; this.password=pass;
        if (user.isEmpty() || pass.isEmpty()) throw new IllegalArgumentException("Username/password required");
    }
    private String enc(String x) { try {return URLEncoder.encode(x,"UTF-8");} catch(Exception e){throw new IllegalStateException(e);} }
    private String api(String action) { return server+"/player_api.php?username="+enc(user)+"&password="+enc(password)+"&action="+action; }
    private JSONArray array(String action) throws Exception {
        byte[] body=Net.bytes(api(action),16*1024*1024);
        String json=new String(body, StandardCharsets.UTF_8);
        if (json.trim().startsWith("{")) throw new IOException("Portal returned an object instead of a channel list (check credentials)");
        return new JSONArray(json);
    }
    public List<Channel> loadAll() throws Exception {
        ArrayList<Channel> result=new ArrayList<>();
        warnings.clear();
        collect(result,"get_live_streams","live","get_live_categories","Live TV");
        collect(result,"get_vod_streams","movie","get_vod_categories","Movies/VOD");
        collect(result,"get_series","series","get_series_categories","Series");
        if(result.isEmpty())throw new IOException("No playable items returned. Check account access and portal status. "+warnings());
        return result;
    }
    private void collect(List<Channel> output,String action,String kind,String categoryAction,String name){
        try{add(output,array(action),kind,categories(categoryAction));}
        catch(Exception e){warnings.add(name+" cannot be loaded from this portal");}
    }
    private String pathPart(String s) { try { return new URI(null, null, "/"+s, null).getRawPath().substring(1); } catch(Exception e){ return enc(s); } }
    private Map<String,String> categories(String action) {
        Map<String,String> result=new HashMap<>();
        try { JSONArray list=array(action);for(int i=0;i<list.length();i++){
            JSONObject x=list.optJSONObject(i);if(x!=null)result.put(x.optString("category_id"),x.optString("category_name"));
        }}catch(Exception ignored){}return result;
    }
    private void add(List<Channel> output, JSONArray arr, String kind,Map<String,String> categories) throws IOException {
        for(int i=0;i<arr.length();i++) {
            if(output.size()>=M3uParser.MAX_CHANNELS) throw new IOException("Portal exceeded item limit");
            JSONObject j=arr.optJSONObject(i); if(j==null) continue;
            String sid=j.optString(kind.equals("series")?"series_id":"stream_id");
            if(sid.isEmpty()) continue;
            String ext=j.optString("container_extension", "mp4");
            if(!ext.matches("[A-Za-z0-9]{1,6}")) ext="mp4";
            String url=kind.equals("series") ? api("get_series_info")+"&series_id="+enc(sid) :
                server+(kind.equals("live")?"/live/":"/movie/")+pathPart(user)+"/"+pathPart(password)+"/"+enc(sid)+"."+(kind.equals("live")?"m3u8":ext);
            String direct=j.optString("direct_source","").trim();
            if(!kind.equals("series")&&M3uParser.validStream(direct))url=direct;
            String name=j.optString("name", "Untitled");
            String cid=j.optString("category_id",kind);
            String group=j.optString("category_name",categories.getOrDefault(cid,cid));
            String logo=j.optString("stream_icon",j.optString("cover",""));
            output.add(new Channel(M3uParser.hash(server+"|"+kind+"|"+sid),name,url,group,logo,"",kind,server));
        }
    }
    public List<Channel> episodes(String seriesApiUrl, String seriesName) throws Exception {
        String json=new String(Net.bytes(seriesApiUrl,8*1024*1024),StandardCharsets.UTF_8);
        JSONObject root=new JSONObject(json); JSONObject seasons=root.optJSONObject("episodes");
        ArrayList<Channel> episodes=new ArrayList<>(); if(seasons==null) return episodes;
        for(Iterator<String> it=seasons.keys();it.hasNext();) {
            String season=it.next(); JSONArray arr=seasons.optJSONArray(season); if(arr==null) continue;
            for(int i=0;i<arr.length();i++) {
                JSONObject j=arr.optJSONObject(i);if(j==null)continue;
                String id=j.optString("id");if(id.isEmpty())continue;
                String ext=j.optString("container_extension", "mp4"); if(!ext.matches("[A-Za-z0-9]{1,6}"))ext="mp4";
                String url=server+"/series/"+pathPart(user)+"/"+pathPart(password)+"/"+enc(id)+"."+ext;
                episodes.add(new Channel(M3uParser.hash(server+"|episode|"+id),j.optString("title",seriesName+" - E"+(i+1)),url,
                        "Season "+season,"","","episode",server));
            }
        }
        return episodes;
    }
    public String epg() { return server+"/xmltv.php?username="+enc(user)+"&password="+enc(password); }
}
