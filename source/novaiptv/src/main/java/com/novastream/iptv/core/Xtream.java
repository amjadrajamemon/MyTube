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
        String json=new String(Net.bytes(api(action),16*1024*1024), StandardCharsets.UTF_8).trim();
        if(json.startsWith("[")) return new JSONArray(json);
        if(json.startsWith("{")) {
            JSONObject response=new JSONObject(json);
            for(String key:new String[]{"data","streams","results","items","vod_streams","series","channels"}) {
                JSONArray payload=response.optJSONArray(key);
                if(payload!=null)return payload;
            }
            String error=response.optString("message",response.optString("error",""));
            if(response.has("user_info"))throw new IOException("Provider returned account details instead of "+action+"; verify VOD permissions");
            throw new IOException(error.isEmpty() ? "Provider did not supply a list for "+action : "Provider rejected "+action);
        }
        throw new IOException("Provider sent an unexpected response for "+action);
    }
    public List<Channel> loadAll() throws Exception {
        ArrayList<Channel> result=new ArrayList<>();
        warnings.clear();
        // Load VOD and Series first to prevent them being crowded out by massive live lists.
        collect(result,"get_vod_streams","movie","get_vod_categories","Movies/VOD");
        collect(result,"get_series","series","get_series_categories","Series");
        collect(result,"get_live_streams","live","get_live_categories","Live TV");
        boolean hasLive=false,hasVod=false,hasSeries=false;
        for(Channel item:result){
            if("live".equals(item.kind))hasLive=true;
            else if("movie".equals(item.kind))hasVod=true;
            else if("series".equals(item.kind))hasSeries=true;
        }
        // A provider can expose movies while its global live endpoint is empty,
        // over-limit, or category-only. Recover Live as well as VOD from M3U.
        if(!hasLive||!hasVod||!hasSeries)
            recoverFromM3u(result,!hasLive,!hasVod,!hasSeries);
        int liveCount=0,movieCount=0,seriesCount=0;
        for(Channel c:result){
            String type=StreamKind.detect(c.kind,c.group,c.url);
            if(type.equals("live"))liveCount++;
            else if(type.equals("movie"))movieCount++;
            else if(type.equals("series"))seriesCount++;
        }
        if(liveCount==0)warnings.add("LIVE TV EMPTY: global live API, category lists and M3U recovery yielded no live channels. Confirm Live TV entitlement and server URL.");
        if(movieCount==0)warnings.add("Movies/VOD empty: verify your provider's VOD entitlement.");
        if(result.isEmpty())throw new IOException("Provider returned no Live, Movies or Series items. "+warnings());
        return result;
    }
    private void collect(List<Channel> output,String action,String kind,String categoryAction,String name){
        Map<String,String> groups=categories(categoryAction);
        try{
            JSONArray full=array(action);
            if(full.length()==0){
                if(!recoverByCategory(output,action,kind,groups,name))
                    warnings.add(name+" returned zero entries (including category retrieval)");
                return;
            }
            add(output,full,kind,groups);
        }catch(Exception ex){
            if(recoverByCategory(output,action,kind,groups,name))return;
            String reason=ex instanceof java.io.FileNotFoundException?"HTTP 404":
                ex instanceof java.net.SocketTimeoutException?"timeout":
                ex instanceof java.net.UnknownHostException?"hostname unavailable":ex.getClass().getSimpleName();
            warnings.add(name+" unavailable ("+reason+"); check subscription and portal access");
        }
    }
    private boolean recoverByCategory(List<Channel> output,String action,String kind,
                                      Map<String,String> groups,String name){
        if(groups.isEmpty())return false;
        int recovered=0,attempted=0;
        HashSet<String> known=new HashSet<>();
        for(Channel item:output)known.add(item.url);
        for(String categoryId:groups.keySet()){
            if(attempted++>=80){warnings.add(name+" recovery limited to 80 groups");break;}
            try{
                JSONArray group=array(action+"&category_id="+enc(categoryId));
                ArrayList<Channel> partial=new ArrayList<>();
                add(partial,group,kind,groups);
                for(Channel item:partial){
                    if(output.size()>=72000)break;
                    if(known.add(item.url)){output.add(item);recovered++;}
                }
            }catch(Exception ignored){ /* Try other groups without exposing credentials. */ }
            if(output.size()>=72000)break;
        }
        if(recovered>0)warnings.add(name+": recovered "+recovered+" items by category");
        return recovered>0;
    }
    private void recoverFromM3u(List<Channel> result,boolean needLive,boolean needMovies,boolean needSeries){
        try {
            String endpoint=server+"/get.php?username="+enc(user)+"&password="+enc(password)+"&type=m3u_plus&output=ts";
            M3uParser.Result imported;
            try(java.io.InputStream input=Net.open(endpoint,20*1024*1024)){
                imported=M3uParser.parse(input,endpoint,endpoint);
            }
            HashSet<String> seen=new HashSet<>();
            for(Channel item:result)seen.add(item.url);
            int recovered=0;
            for(Channel item:imported.channels){
                String kind=StreamKind.detect(item.kind,item.group,item.url);
                if(("live".equals(kind)&&needLive)||("movie".equals(kind)&&needMovies)||("series".equals(kind)&&needSeries)){
                    if(seen.add(item.url) && result.size()<75000){result.add(item);recovered++;}
                }
            }
            if(recovered>0)warnings.add("Recovered "+recovered+" Live/VOD/Series items from provider playlist");
            else warnings.add("Provider M3U fallback has no additional classified Live/VOD/Series streams");
        }catch(Exception ignored){
            warnings.add("Provider M3U fallback unavailable; check playlist endpoint and subscription access");
        }
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
            if(i>=24000){warnings.add(kind+" limited to 24000 items per response");break;}
            if(output.size()>=72000)break;
            JSONObject j=arr.optJSONObject(i); if(j==null) continue;
            String sid=j.optString(kind.equals("series")?"series_id":"stream_id");
            if(sid.isEmpty()) continue;
            String ext=j.optString("container_extension", "mp4");
            if(!ext.matches("[A-Za-z0-9]{1,6}")) ext="mp4";
            String url=kind.equals("series") ? api("get_series_info")+"&series_id="+enc(sid) :
                server+(kind.equals("live")?"/live/":"/movie/")+pathPart(user)+"/"+pathPart(password)+"/"+enc(sid)+"."+(kind.equals("live")?"ts":ext);
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
