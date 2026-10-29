package com.novastream.iptv.core;

import androidx.media3.exoplayer.ExoPlayer;

/** Cross-module single-playback ownership; stops Nova whenever YouTube takes over. */
public final class NovaPlaybackBridge {
    private static ExoPlayer active;
    private NovaPlaybackBridge(){}
    public static void claim(ExoPlayer next){
        ExoPlayer previous;
        synchronized(NovaPlaybackBridge.class){ previous=active;active=next; }
        if(previous!=null && previous!=next)previous.stop();
    }
    public static void stop(){
        ExoPlayer previous;
        synchronized(NovaPlaybackBridge.class){ previous=active;active=null; }
        if(previous!=null)previous.stop();
    }
    public static synchronized void release(ExoPlayer player){
        if(active==player)active=null;
    }
}
