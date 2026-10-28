package com.novastream.iptv.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class Channel {
    public final String id, name, url, group, logo, tvgId, kind, source, userAgent, referrer;
    public Channel(String id, String name, String url, String group, String logo, String tvgId, String kind, String source) {
        this(id,name,url,group,logo,tvgId,kind,source,"","");
    }
    public Channel(String id,String name,String url,String group,String logo,String tvgId,String kind,String source,String userAgent,String referrer){
        this.id=id;this.name=name;this.url=url;this.group=group;this.logo=logo;this.tvgId=tvgId;this.kind=kind;this.source=source;
        this.userAgent=userAgent==null?"":userAgent;this.referrer=referrer==null?"":referrer;
    }
    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("id",id).put("name",name).put("url",url).put("group",group)
                .put("logo",logo).put("tvgId",tvgId).put("kind",kind).put("source",source)
                .put("userAgent",userAgent).put("referrer",referrer);
    }
    public static Channel fromJson(JSONObject j) {
        return new Channel(j.optString("id"),j.optString("name"),j.optString("url"),j.optString("group"),
                j.optString("logo"),j.optString("tvgId"),j.optString("kind","live"),j.optString("source"),
                j.optString("userAgent",""),j.optString("referrer",""));
    }
}
