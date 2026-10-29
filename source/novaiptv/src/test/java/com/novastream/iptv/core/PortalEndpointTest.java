package com.novastream.iptv.core;
import static org.junit.Assert.*;
import org.junit.Test;

public class PortalEndpointTest {
    @Test public void acceptsPlainServerWithPort(){
        assertEquals("http://provider.example:8080",PortalEndpoint.normalize("provider.example:8080"));
    }
    @Test public void convertsFullXtreamUrlsWithoutRetainingCredentials(){
        String url="http://provider.example:25461/get.php?username=alice&password=secret&type=m3u_plus";
        assertEquals("http://provider.example:25461",PortalEndpoint.normalize(url));
        assertEquals("provider.example:25461",PortalEndpoint.displayHost(url));
    }
    @Test public void preservesLegitimatePanelPrefix(){
        assertEquals("https://provider.example:8443/panel",PortalEndpoint.normalize(
            "https://provider.example:8443/panel/player_api.php?username=a&password=b"));
    }
    @Test public void rejectsInvalidHosts(){
        try{PortalEndpoint.normalize("http://:9999/player_api.php");fail("empty host");}
        catch(IllegalArgumentException expected){}
        try{PortalEndpoint.normalize("ftp://example.com");fail("not http");}
        catch(IllegalArgumentException expected){}
    }
    @Test public void stripsFragmentsAndWhitespace(){
        assertEquals("https://server.example:8080",PortalEndpoint.normalize(
             " https://server.example:8080/get.php?username=u#fragment "));
    }
}
