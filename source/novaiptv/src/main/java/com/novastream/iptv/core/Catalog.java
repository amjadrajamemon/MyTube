package com.novastream.iptv.core;

import android.content.Context;
import com.novastream.iptv.model.Channel;
import org.json.*;
import java.util.*;

/** Shared catalog loaded on the main thread, saved encrypted. */
public final class Catalog {
    public final ArrayList<Channel> channels=new ArrayList<>();
    public final Set<String> favorites=new LinkedHashSet<>();
    /** User-defined order is stored separately to preserve existing installations. */
    public final ArrayList<String> favoriteOrder=new ArrayList<>();
    /** Named collections reference channel IDs and never duplicate playlist entries. */
    public final LinkedHashMap<String,ArrayList<String>> favoriteFolders=new LinkedHashMap<>();
    public final ArrayList<String> recent=new ArrayList<>();
    public final HashMap<String,String> xtreamCredentials=new HashMap<>();
    /** Labels and connection addresses are encrypted with the library; never shown raw. */
    public final LinkedHashMap<String,String> sourceNames=new LinkedHashMap<>();
    public final HashMap<String,String> sourceHosts=new HashMap<>();
    public final Set<String> disabledSources=new HashSet<>();
    public String epgUrl="";
    public Epg epg=new Epg();
    public int theme=0, textSize=0, rowDensity=1;
    public String customBg="", customAccent="", customText="";
    public boolean pip=true;
    private static Catalog instance;
    private Catalog(){}
    public static synchronized Catalog get(){if(instance==null)instance=new Catalog();return instance;}
    public static synchronized Catalog load(Context context) throws Exception {
        Catalog c=new Catalog();String raw=new SecureStore(context).load();
        if(!raw.isEmpty()){
            JSONObject j=new JSONObject(raw);JSONArray a=j.optJSONArray("channels");
            if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)c.channels.add(Channel.fromJson(o));}
            a=j.optJSONArray("favorites");if(a!=null)for(int i=0;i<a.length();i++)c.favorites.add(a.optString(i));
            a=j.optJSONArray("favoriteOrder");if(a!=null)for(int i=0;i<a.length();i++){
                String id=a.optString(i);if(c.favorites.contains(id)&&!c.favoriteOrder.contains(id))c.favoriteOrder.add(id);
            }
            for(String id:c.favorites)if(!c.favoriteOrder.contains(id))c.favoriteOrder.add(id);
            JSONObject folders=j.optJSONObject("favoriteFolders");
            if(folders!=null)for(Iterator<String> k=folders.keys();k.hasNext();){
                String name=k.next();JSONArray saved=folders.optJSONArray(name);
                if(saved==null||name.trim().isEmpty())continue;
                ArrayList<String> ids=new ArrayList<>();
                for(int i=0;i<saved.length();i++){
                    String id=saved.optString(i);
                    if(c.favorites.contains(id)&&!ids.contains(id))ids.add(id);
                }
                if(c.favoriteFolders.size()<20)c.favoriteFolders.put(name,ids);
            }
            a=j.optJSONArray("recent");if(a!=null)for(int i=0;i<a.length();i++)c.recent.add(a.optString(i));
            c.epgUrl=j.optString("epgUrl","");c.theme=j.optInt("theme",0);c.textSize=j.optInt("textSize",0);c.pip=j.optBoolean("pip",true);c.rowDensity=j.optInt("rowDensity",1);
            c.customBg=j.optString("customBg","");c.customAccent=j.optString("customAccent","");c.customText=j.optString("customText","");
            JSONObject creds=j.optJSONObject("xtreamCredentials");if(creds!=null)for(Iterator<String> it=creds.keys();it.hasNext();){String k=it.next();c.xtreamCredentials.put(k,creds.optString(k));}
            JSONObject names=j.optJSONObject("sourceNames");
            if(names!=null)for(Iterator<String> it=names.keys();it.hasNext();){
                String k=it.next();c.sourceNames.put(k,names.optString(k));
            }
            JSONObject hosts=j.optJSONObject("sourceHosts");
            if(hosts!=null)for(Iterator<String> it=hosts.keys();it.hasNext();){
                String k=it.next();c.sourceHosts.put(k,hosts.optString(k));
            }
            JSONArray off=j.optJSONArray("disabledSources");
            if(off!=null)for(int i=0;i<off.length();i++)c.disabledSources.add(off.optString(i));
        }
        instance=c;return c;
    }
    public void persist(Context context) throws Exception {
        JSONObject j=new JSONObject();JSONArray a=new JSONArray();for(Channel c:channels)a.put(c.toJson());
        JSONObject folderJson=new JSONObject();
        for(Map.Entry<String,ArrayList<String>> entry:favoriteFolders.entrySet())
            folderJson.put(entry.getKey(),new JSONArray(entry.getValue()));
        j.put("channels",a).put("favorites",new JSONArray(favorites))
                .put("favoriteOrder",new JSONArray(favoriteOrder))
                .put("favoriteFolders",folderJson).put("recent",new JSONArray(recent))
                .put("epgUrl",epgUrl).put("theme",theme).put("textSize",textSize).put("pip",pip)
                .put("xtreamCredentials",new JSONObject(xtreamCredentials))
                .put("sourceNames",new JSONObject(sourceNames))
                .put("sourceHosts",new JSONObject(sourceHosts))
                .put("disabledSources",new JSONArray(disabledSources))
                .put("rowDensity",rowDensity).put("customBg",customBg).put("customAccent",customAccent).put("customText",customText);
        new SecureStore(context).save(j.toString());
    }
    public void addFavorite(String id){
        if(id==null||id.isEmpty())return;
        if(favorites.add(id))favoriteOrder.add(id);
    }
    public void toggleFavorite(String id){
        if(favorites.contains(id))removeFavorite(id);
        else addFavorite(id);
    }
    public void removeFavorite(String id){
        favorites.remove(id);favoriteOrder.remove(id);
        for(List<String> folder:favoriteFolders.values())folder.remove(id);
    }
    public void clearAllFavorites(){
        favorites.clear();favoriteOrder.clear();
        for(List<String> list:favoriteFolders.values())list.clear();
    }
    public boolean createFavoriteFolder(String name){
        if(!validFolderName(name,null)||favoriteFolders.size()>=20)return false;
        favoriteFolders.put(name,new ArrayList<>());return true;
    }
    private boolean validFolderName(String name,String except){
        if(name==null||name.length()>32||name.trim().isEmpty()||name.indexOf('\n')>=0
            ||name.indexOf('\r')>=0||name.equalsIgnoreCase("All Favorites"))return false;
        for(String existing:favoriteFolders.keySet())
            if(!existing.equals(except)&&existing.equalsIgnoreCase(name))return false;
        return true;
    }
    public boolean renameFavoriteFolder(String oldName,String newName){
        if(!favoriteFolders.containsKey(oldName)||!validFolderName(newName,oldName))return false;
        if(oldName.equals(newName))return true;
        LinkedHashMap<String,ArrayList<String>> updated=new LinkedHashMap<>();
        for(Map.Entry<String,ArrayList<String>> e:favoriteFolders.entrySet())
            updated.put(e.getKey().equals(oldName)?newName:e.getKey(),e.getValue());
        favoriteFolders.clear();favoriteFolders.putAll(updated);return true;
    }
    public boolean assignFavorite(String folder,String id){
        ArrayList<String> f=favoriteFolders.get(folder);if(f==null||id==null)return false;
        addFavorite(id);if(!f.contains(id))f.add(id);return true;
    }
    public void unassignFavorite(String folder,String id){
        ArrayList<String> f=favoriteFolders.get(folder);if(f!=null)f.remove(id);
    }
    public boolean moveFavorite(String id,String folder,int direction){
        List<String> ids=folder==null||folder.isEmpty()?favoriteOrder:favoriteFolders.get(folder);
        if(ids==null)return false;
        int from=ids.indexOf(id),to=from+(direction<0?-1:1);
        if(from<0||to<0||to>=ids.size())return false;
        Collections.swap(ids,from,to);return true;
    }
    public List<Channel> favoriteEntries(String folder,String kind,String query){
        List<String> sequence=(folder==null||folder.isEmpty())?favoriteOrder:favoriteFolders.get(folder);
        if(sequence==null)return Collections.emptyList();
        String needle=query==null?"":query.toLowerCase(Locale.ROOT);
        HashMap<String,Channel> byId=new HashMap<>();
        for(Channel c:channels)byId.put(c.id,c);
        ArrayList<Channel> output=new ArrayList<>();
        for(String id:sequence){
            if(!favorites.contains(id))continue;
            Channel c=byId.get(id);if(c==null||!isSourceEnabled(c.source))continue;
            String type=StreamKind.detect(c.kind,c.group,c.url);
            if("Live".equals(kind)&&!type.equals("live"))continue;
            if("Movies".equals(kind)&&!type.equals("movie"))continue;
            if("Series".equals(kind)&&!type.equals("series")&&!type.equals("episode"))continue;
            if(!c.name.toLowerCase(Locale.ROOT).contains(needle) &&
                !c.group.toLowerCase(Locale.ROOT).contains(needle))continue;
            output.add(c);
        }
        return output;
    }
    public List<String> sources(){
        LinkedHashSet<String> keys=new LinkedHashSet<>(sourceNames.keySet());
        for(Channel c:channels)keys.add(c.source);
        return new ArrayList<>(keys);
    }
    public boolean isSourceEnabled(String key){return !disabledSources.contains(key);}
    public void setSourceEnabled(String key,boolean enabled){
        if(enabled)disabledSources.remove(key);else disabledSources.add(key);
    }
    public String sourceHost(String key){return sourceHosts.getOrDefault(key,key);}
    public String defaultSourceName(String key){
        try{
            String host=sourceHost(key);
            java.net.URI uri=new java.net.URI(host);
            String hostname=uri.getHost();
            if(hostname!=null&&!hostname.isEmpty())return hostname;
        }catch(Exception ignored){}
        if(key.startsWith("content:"))return "Local playlist";
        if(key.startsWith("xtream:"))return "Xtream account";
        return "Playlist";
    }
    public String sourceName(String key){
        String saved=sourceNames.get(key);
        return saved==null||saved.isEmpty()?defaultSourceName(key):saved;
    }
    public boolean renameSource(String key,String name){
        String n=name==null?"":name.trim();
        if(n.isEmpty()||n.length()>48||n.indexOf('\n')>=0||n.indexOf('\r')>=0||!sources().contains(key))return false;
        sourceNames.put(key,n);return true;
    }
    public int sourceSize(String key){
        int count=0;for(Channel c:channels)if(c.source.equals(key))count++;return count;
    }
    public void removeSource(String key){
        for(Channel c:new ArrayList<>(channels))if(c.source.equals(key)){
            removeFavorite(c.id);recent.remove(c.id);
        }
        channels.removeIf(c->c.source.equals(key));
        xtreamCredentials.remove(key);sourceHosts.remove(key);
        sourceNames.remove(key);disabledSources.remove(key);
    }
    public void replaceSource(String source,List<Channel> updated) {
        channels.removeIf(x->x.source.equals(source));
        sourceNames.putIfAbsent(source,defaultSourceName(source));
        Set<String> ids=new HashSet<>();for(Channel c:channels)ids.add(c.id);
        for(Channel x:updated)if(channels.size()<75000 && ids.add(x.id))channels.add(x);
    }
    public Channel find(String id){for(Channel c:channels)if(c.id.equals(id))return c;return null;}
    public void viewed(String id){recent.remove(id);recent.add(0,id);while(recent.size()>50)recent.remove(recent.size()-1);}
    public List<Channel> filtered(String tab,String term,String group) {
        ArrayList<Channel> result=new ArrayList<>();
        String search=term.toLowerCase(Locale.ROOT);
        for(Channel c:channels){
            if(!isSourceEnabled(c.source))continue;
            if(!group.isEmpty()&&!c.group.equals(group))continue;
            if(!c.name.toLowerCase(Locale.ROOT).contains(search) && !c.group.toLowerCase(Locale.ROOT).contains(search))continue;
            if(tab.equals("Favorites")&&!favorites.contains(c.id))continue;
            if(tab.equals("Recent")&&!recent.contains(c.id))continue;
            String type=StreamKind.detect(c.kind,c.group,c.url);
            if(tab.equals("Live")&&!type.equals("live"))continue;
            if(tab.equals("Movies")&&!type.equals("movie"))continue;
            if(tab.equals("Series")&&!type.equals("series")&&!type.equals("episode"))continue;
            result.add(c);
        }
        if(tab.equals("Recent"))result.sort(Comparator.comparingInt(c->recent.indexOf(c.id)));
        if(tab.equals("Favorites"))result.sort(Comparator.comparingInt(c->{
            int i=favoriteOrder.indexOf(c.id);return i<0?Integer.MAX_VALUE:i;
        }));
        else if(!tab.equals("Recent"))result.sort((a,b)->a.name.compareToIgnoreCase(b.name));
        return result;
    }
}
