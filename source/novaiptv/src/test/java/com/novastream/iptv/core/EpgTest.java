package com.novastream.iptv.core;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import static org.junit.Assert.*;

public class EpgTest {
    @Test public void rejectsDtdEntities() throws Exception {
        String xml="<!DOCTYPE tv [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><tv>&x;</tv>";
        try{Epg.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));fail("Expected rejection");}
        catch(org.xml.sax.SAXException expected){}
    }
    @Test public void readsNowFromGuide() throws Exception {
        SimpleDateFormat fmt=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String s=fmt.format(new Date(System.currentTimeMillis()-60000));String e=fmt.format(new Date(System.currentTimeMillis()+3600000));
        String xml="<tv><channel id=\"news.id\"><display-name>Test News</display-name></channel><programme start=\""+s+"\" stop=\""+e+"\" channel=\"news.id\"><title>Today Live</title></programme></tv>";
        Epg epg=Epg.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals("Today Live",epg.current("news.id",""));assertEquals("Today Live",epg.current("", "test news"));
    }
}
