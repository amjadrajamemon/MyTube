package com.novastream.iptv.core;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Secure SAX XMLTV ingestion. Only a rolling 48-hour window is retained in memory. */
public final class Epg {
    public static final class Program {
        public final String title; public final long start, end;
        public Program(String title,long start,long end){this.title=title;this.start=start;this.end=end;}
    }
    private final Map<String,List<Program>> programmes=new HashMap<>();
    private final Map<String,String> names=new HashMap<>();
    public String current(String id, String title) {
        long now=System.currentTimeMillis(); String key=id;
        if(!programmes.containsKey(key)) key=names.getOrDefault(title.toLowerCase(Locale.ROOT), "");
        List<Program> list=programmes.get(key);if(list==null)return "";
        for(Program p:list)if(p.start<=now&&p.end>now)return p.title;return "";
    }
    public List<Program> schedule(String id,String channelName) {
        String key=programmes.containsKey(id)?id:names.getOrDefault(channelName.toLowerCase(Locale.ROOT),"");
        List<Program> a=programmes.get(key);return a==null?Collections.emptyList():new ArrayList<>(a);
    }
    public int count(){return programmes.size();}
    public static Epg parse(InputStream in) throws Exception {
        Epg epg=new Epg();
        SAXParserFactory f=SAXParserFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setXIncludeAware(false);f.setNamespaceAware(false);
        f.newSAXParser().parse(in,new DefaultHandler(){
            String currentId="", channelId="", element=""; long start,end;
            StringBuilder sb=new StringBuilder(); boolean insideProgram=false; int items=0;
            @Override public void startElement(String uri,String localName,String q,Attributes a) throws SAXException {
                if(q.equals("channel"))channelId=a.getValue("id");
                if(q.equals("programme")){
                    if(++items>100000)throw new SAXException("Guide too large");
                    insideProgram=true;currentId=a.getValue("channel");start=date(a.getValue("start"));end=date(a.getValue("stop"));
                }
                element=q;sb.setLength(0);
            }
            @Override public void characters(char[] c,int offset,int length){if(sb.length()<500)sb.append(c,offset,Math.min(length,500-sb.length()));}
            @Override public void endElement(String uri,String localName,String q) {
                if(q.equals("display-name")&&channelId!=null&&!channelId.isEmpty())epg.names.put(sb.toString().trim().toLowerCase(Locale.ROOT),channelId);
                if(q.equals("title")&&insideProgram&&currentId!=null) {
                    long now=System.currentTimeMillis();
                    if(end>now&&start<now+48L*60*60*1000 && end>start) {
                        List<Program> ls=epg.programmes.computeIfAbsent(currentId,k->new ArrayList<>());
                        if(ls.size()<100)ls.add(new Program(sb.toString().trim(),start,end));
                    }
                }
                if(q.equals("programme"))insideProgram=false;
                if(q.equals("channel"))channelId="";
                element="";sb.setLength(0);
            }
        });
        for(List<Program> ls:epg.programmes.values())ls.sort(Comparator.comparingLong(x->x.start));
        return epg;
    }
    private static long date(String raw) {
        if(raw==null||raw.length()<14)return 0;
        try {
            String v=raw.trim();
            if(v.length()==14)v+=" +0000";
            if(v.length()>20)v=v.substring(0,20);
            SimpleDateFormat fmt=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);
            fmt.setLenient(false);Date d=fmt.parse(v);return d==null?0:d.getTime();
        } catch(Exception e){return 0;}
    }
}
