package com.novastream.iptv.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Normalizes provider URLs without recording credentials, query strings or fragments. */
public final class PortalEndpoint {
    private PortalEndpoint(){}
    public static String normalize(String input) {
        String raw=input==null?"":input.trim();
        if(raw.isEmpty())throw new IllegalArgumentException("Enter a portal address.");
        if(raw.indexOf('\r')>=0||raw.indexOf('\n')>=0)throw new IllegalArgumentException("Portal address contains a line break.");
        if(!raw.contains("://"))raw="http://"+raw;
        final URI uri;
        try {uri=new URI(raw);}catch(URISyntaxException error){
            throw new IllegalArgumentException("Malformed portal address. Check the host and port.");
        }
        String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);
        if(!scheme.equals("http")&&!scheme.equals("https"))
            throw new IllegalArgumentException("Portal must use HTTP or HTTPS.");
        if(uri.getUserInfo()!=null)
            throw new IllegalArgumentException("Enter the account username/password in their separate fields.");
        String host=uri.getHost();
        if(host==null||host.isEmpty())
            throw new IllegalArgumentException("Portal hostname is invalid. Enter an address like http://server.example:8080");
        int port=uri.getPort();
        if(port>65535)throw new IllegalArgumentException("Portal port must be between 1 and 65535.");
        String path=uri.getRawPath();
        if(path==null)path="";
        path=path.replaceAll("/+$","");
        String lower=path.toLowerCase(Locale.ROOT);
        if(lower.endsWith("/player_api.php"))path=path.substring(0,path.length()-"/player_api.php".length());
        else if(lower.endsWith("/get.php"))path=path.substring(0,path.length()-"/get.php".length());
        else if(lower.endsWith("/xmltv.php"))path=path.substring(0,path.length()-"/xmltv.php".length());
        path=path.replaceAll("/+$","");
        // Query parameters might include password and should never be preserved as part of the base.
        String authority=host.indexOf(':')>=0&&!host.startsWith("[")?"["+host+"]":host;
        return scheme+"://"+authority+(port>0?":"+port:"")+path;
    }
    public static String displayHost(String base){
        try{
            URI uri=new URI(normalize(base));
            return uri.getHost()+(uri.getPort()>0?":"+uri.getPort():"");
        }catch(Exception error){return "the configured portal";}
    }
}
