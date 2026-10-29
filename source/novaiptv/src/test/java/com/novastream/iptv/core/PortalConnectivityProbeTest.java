package com.novastream.iptv.core;
import java.net.URI;
import org.junit.Test;
import static org.junit.Assert.*;

public class PortalConnectivityProbeTest {
    @Test public void stripsPasswordsAndPathsFromDiagnosticTarget(){
        URI result=PortalConnectivityProbe.hostOnly(
          "http://provider.example:8080/get.php?username=private&password=secret&type=m3u");
        assertEquals("provider.example",result.getHost());
        assertEquals(8080,result.getPort());
        assertEquals("/",result.getPath());
        assertNull(result.getRawQuery());
        assertNull(result.getRawUserInfo());
    }
    @Test public void usesConfiguredPortBeforeOptionalChecks(){
        int[] p=PortalConnectivityProbe.probePorts(
            PortalConnectivityProbe.hostOnly("http://provider.example:8080/get.php?password=secret"));
        assertArrayEquals(new int[]{8080,80,443},p);
    }
    @Test public void doesNotDuplicateStandardPorts(){
        int[] p=PortalConnectivityProbe.probePorts(PortalConnectivityProbe.hostOnly("https://provider.example/path"));
        assertArrayEquals(new int[]{443,80},p);
    }
    @Test public void rejectsBadHostAndProtocol(){
        try{PortalConnectivityProbe.hostOnly("https:///?password=x");fail("bad host");}
        catch(IllegalArgumentException expected){}
        try{PortalConnectivityProbe.hostOnly("ftp://provider.example/");fail("bad scheme");}
        catch(IllegalArgumentException expected){}
    }
}
