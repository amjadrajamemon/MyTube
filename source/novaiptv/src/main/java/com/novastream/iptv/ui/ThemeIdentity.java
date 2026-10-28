package com.novastream.iptv.ui;

/** UI families are intentionally structural, not just alternate palettes. */
public final class ThemeIdentity {
    public final int index, family, variant, radius, gutter, heroHeight, imageHeight;
    public final float titleMultiplier, cardMultiplier;
    public final boolean navigationAtBottom, darkCard, immersiveBackdrop;
    public static final String[] FAMILIES={"CINEMA","FROSTED GLASS","EDITORIAL","TV DASHBOARD"};
    private static final String[] VARIANTS={"Signature","Minimal","Dynamic","Comfort"};
    private ThemeIdentity(int i){
        index=i;family=i/4;variant=i%4;
        int[] corners={24,20,6,12};
        radius=Math.max(2,corners[family]+new int[]{0,-4,6,2}[variant]);
        gutter=new int[]{12,16,18,9}[family]+new int[]{0,-2,2,3}[variant];
        heroHeight=(new int[]{96,64,74,54}[family])+(variant==2?12:0);
        imageHeight=(new int[]{207,190,163,120}[family])+(variant==3?18:0);
        titleMultiplier=new float[]{1.35f,1.10f,1.28f,1.00f}[family] + .04f*variant;
        cardMultiplier=new float[]{1.1f,.97f,1.0f,.9f}[family]+.025f*variant;
        navigationAtBottom=family==3;
        darkCard=family!=2;
        immersiveBackdrop=family==0||family==1;
    }
    public static ThemeIdentity of(int choice){
        return new ThemeIdentity(Math.floorMod(choice,16));
    }
    public static String[] labels(String[] palettes){
        String[] results=new String[16];
        for(int i=0;i<16;i++){
            ThemeIdentity t=of(i);
            String palette=palettes!=null&&i<palettes.length?palettes[i]:"Color "+(i+1);
            results[i]=FAMILIES[t.family]+"  /  "+VARIANTS[t.variant]+"  •  "+palette;
        }
        return results;
    }
    public String heading(){
        return new String[]{"CINEMA. AT HOME.","YOUR GLASS VIEW.","THE NOVA EDITION","LIVE TV COMMAND CENTER"}[family];
    }
    public String subheading(){
        return new String[]{"Discover films, channels & series","A lighter way to explore","Curated entertainment, your way","Channels and favorites within reach"}[family];
    }
}