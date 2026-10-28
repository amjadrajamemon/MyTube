package com.novastream.iptv.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

public final class Style {
    public static final String[] NAMES={"Aurora Glass","Midnight Blue","Ocean Wave","Royal Violet","Ruby Cinema","Forest","Cyberpunk","Arctic Light","Ivory Gold","Solar Orange","Slate Pro","Emerald","Rose Quartz","Deep Space","Retro Teal","High Contrast"};
    private static final String[][] COLORS={
        {"#101A32","#192C48","#66E6FF","#F5FAFF"},{"#080F25","#13274C","#4B9CFF","#E9F3FF"},
        {"#031E30","#0B415C","#50DBE8","#F1FCFF"},{"#1B1139","#35245D","#C19AFF","#FCF6FF"},
        {"#270F1A","#472132","#FF6A83","#FFF4F6"},{"#0F241F","#224439","#87E0B4","#ECFFF3"},
        {"#150D2A","#29175C","#F577F0","#FFF6FF"},{"#D9E7F6","#F4F8FF","#1461B9","#162E49"},
        {"#EEE6DA","#FFF8E9","#9C691C","#32291D"},{"#30201A","#62402D","#FFBF62","#FFF8E8"},
        {"#12191F","#283645","#76A7C5","#F1F5FA"},{"#09231D","#174A3A","#59F1A8","#ECFFF5"},
        {"#351D2E","#5F304F","#FFB3D2","#FFF1F8"},{"#070D22","#182244","#85A1FF","#F5F8FF"},
        {"#0F3435","#1E5E60","#64EDCD","#EFFFF9"},{"#000000","#202020","#FFFF00","#FFFFFF"}
    };
    public final int bg,surface,accent,text; public final float scale;
    public Style(int index,int size,String customBg,String customAccent,String customText){
        String[] x=COLORS[Math.floorMod(index,COLORS.length)];
        bg=color(customBg,x[0]);surface=Color.parseColor(x[1]);accent=color(customAccent,x[2]);text=color(customText,x[3]);
        scale=1f+Math.max(0,Math.min(4,size))*.12f;
    }
    private static int color(String value,String fallback){try{return Color.parseColor(value.matches("#[a-fA-F0-9]{6}")?value:fallback);}catch(Exception ignored){return Color.parseColor(fallback);}}
    public GradientDrawable panel(int fill,float radius,boolean outline){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{fill,surface});d.setCornerRadius(radius);if(outline)d.setStroke(2,accent);return d;}
    public void card(View v,boolean selected){v.setBackground(panel(selected?surface:bg,16,selected));}
    public TextView label(Context c,String s,int size,boolean bold){TextView v=new TextView(c);v.setText(s);v.setTextColor(text);v.setTextSize(size*scale);v.setPadding(dp(c,10),dp(c,8),dp(c,10),dp(c,8));if(bold)v.setTypeface(null,1);return v;}
    public static int dp(Context c,int x){return (int)(x*c.getResources().getDisplayMetrics().density+.5f);}
}
