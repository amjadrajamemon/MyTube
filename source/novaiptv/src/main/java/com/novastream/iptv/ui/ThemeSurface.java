package com.novastream.iptv.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Family-specific geometry, backgrounds, material and typography. */
public final class ThemeSurface {
    private ThemeSurface(){}
    private static int mix(int a,int b,float amount){
        float k=Math.max(0,Math.min(1,amount));
        return Color.argb(
            (int)(Color.alpha(a)*(1-k)+Color.alpha(b)*k),
            (int)(Color.red(a)*(1-k)+Color.red(b)*k),
            (int)(Color.green(a)*(1-k)+Color.green(b)*k),
            (int)(Color.blue(a)*(1-k)+Color.blue(b)*k));
    }
    public static GradientDrawable wallpaper(Style style,ThemeIdentity theme){
        int[] palette;
        switch(theme.family){
            case 0: palette=new int[]{mix(style.bg,Color.BLACK,.45f),mix(style.surface,style.accent,.12f),style.bg};break;
            case 1: palette=new int[]{mix(style.accent,style.bg,.74f),mix(style.bg,style.surface,.44f),mix(style.accent,style.bg,.92f)};break;
            case 2: palette=new int[]{mix(style.bg,style.surface,.2f),style.bg,mix(style.surface,style.bg,.73f)};break;
            case 4:palette=new int[]{0xFF02060E,0xFF0A1829,0xFF030913};break;
            default:palette=new int[]{style.bg,mix(style.surface,style.bg,.50f),style.bg};break;
        }
        GradientDrawable.Orientation[] directions={GradientDrawable.Orientation.TL_BR,
            GradientDrawable.Orientation.TR_BL,GradientDrawable.Orientation.TOP_BOTTOM,
            GradientDrawable.Orientation.LEFT_RIGHT};
        return new GradientDrawable(directions[theme.variant],palette);
    }
    public static GradientDrawable panel(Style style,ThemeIdentity theme,boolean selected,boolean hero){
        GradientDrawable drawable=new GradientDrawable();
        int bg;
        switch(theme.family){
            case 0:
                bg=selected?mix(style.accent,style.surface,.45f):mix(style.surface,style.bg,.14f);break;
            case 1:
                bg=selected?mix(style.accent,style.surface,.48f):mix(style.surface,style.bg,.35f);break;
            case 2:
                bg=selected?mix(style.accent,style.surface,.18f):style.surface;break;
            case 4:
                bg=selected?0xFF07568B:0xFF101925;break;
            default:
                bg=selected?mix(style.accent,style.surface,.3f):mix(style.surface,style.bg,.24f);break;
        }
        drawable.setColor(bg);
        int pxRadius=theme.radius;
        if(hero)pxRadius=Math.max(2,pxRadius-2);
        if(theme.family==3)pxRadius=Math.min(pxRadius,8);
        if(theme.family==4)pxRadius=2;
        drawable.setCornerRadius(pxRadius);
        int border=(selected||theme.family==1||theme.family==2||theme.family==4)?(selected?2:1):0;
        if(border>0)drawable.setStroke(border,selected?(theme.family==4?0xFF78CAFA:style.accent):(theme.family==4?0xFF566D81:mix(style.accent,style.surface,.48f)));
        return drawable;
    }
    public static void button(Button button, Style style, ThemeIdentity theme, boolean selected){
        float font=new float[]{.98f,.95f,1.07f,.99f,1.0f}[theme.family];
        button.setTextSize(13*style.scale*font);
        button.setTypeface(theme.family==2?Typeface.SERIF:Typeface.DEFAULT,
            theme.family==3?Typeface.BOLD:Typeface.NORMAL);
        button.setAllCaps(theme.family==2);
        button.setTextColor(theme.family==4?0xFFF3F8FC:(selected?style.bg:style.text));
        button.setBackground(panel(style,theme,selected,false));
        button.setMinHeight(theme.family==4?56:theme.family==3?42:46);
        button.setMinimumWidth(0);
    }
    public static void heading(TextView text,Style style,ThemeIdentity theme){
        text.setTypeface(theme.family==2?Typeface.create("serif",Typeface.BOLD):
            Typeface.create(theme.family==3?"sans-serif-condensed":"sans-serif",Typeface.BOLD));
        text.setLetterSpacing(theme.family==2?.065f:theme.family==0?.035f:theme.family==4?.02f:0f);
        text.setTextColor(theme.family==4?0xFFF2F8FC:(theme.family==2?style.text:style.accent));
    }
    public static void decorateCard(View card,Style style,ThemeIdentity theme,boolean focus){
        card.setBackground(panel(style,theme,focus,false));
        card.setElevation(focus?8:(theme.family==0?5:theme.family==1?4:0));
        // Scaling cards makes a focused tile collide with neighbors on TV.
        card.setScaleX(1);card.setScaleY(1);
    }
    public static int dp(Context c,int n){return (int)(c.getResources().getDisplayMetrics().density*n+.5f);}
}