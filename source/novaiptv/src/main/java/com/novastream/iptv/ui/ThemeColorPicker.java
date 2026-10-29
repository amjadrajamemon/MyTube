package com.novastream.iptv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Remote-, touch-, keyboard- and air-mouse-friendly HSV color adjuster. */
public final class ThemeColorPicker {
    public interface OnColorSelected { void selected(int color); }
    private ThemeColorPicker() {}

    private static int dp(Activity a,int n) {
        return (int)(a.getResources().getDisplayMetrics().density*n+.5f);
    }

    public static void show(Activity activity,String title,int initial,OnColorSelected callback) {
        final float[] hsv=new float[3];
        Color.colorToHSV(initial | 0xFF000000,hsv);
        LinearLayout layout=new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(activity,22),dp(activity,14),dp(activity,22),dp(activity,18));
        // Small landscape phones and Android TV dialogs cannot always fit three
        // sliders plus a preview. Keep the dialog actions visible by scrolling
        // only the picker content, not the Apply / Cancel buttons.
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(layout);

        TextView preview=new TextView(activity);
        preview.setText("LIVE COLOR PREVIEW");
        preview.setTextSize(16);
        preview.setGravity(Gravity.CENTER);
        preview.setTextColor(Color.WHITE);
        GradientDrawable surface=new GradientDrawable();
        surface.setCornerRadius(dp(activity,12));
        preview.setBackground(surface);
        layout.addView(preview,new LinearLayout.LayoutParams(-1,dp(activity,64)));

        String[] names={"Hue","Saturation","Brightness"};
        int[] limits={360,100,100};
        int[] values={Math.round(hsv[0]),Math.round(hsv[1]*100),Math.round(hsv[2]*100)};
        TextView[] labels=new TextView[3];
        SeekBar[] sliders=new SeekBar[3];
        Runnable refresh=()->{
            int color=Color.HSVToColor(hsv);
            surface.setColor(color);
            double luminance=(.2126*Color.red(color)+.7152*Color.green(color)+.0722*Color.blue(color));
            preview.setTextColor(luminance>150?Color.BLACK:Color.WHITE);
            for(int i=0;i<3;i++)
                if(labels[i]!=null) labels[i].setText(names[i]+": "+Math.round(i==0?hsv[0]:hsv[i]*100)+(i==0?"°":"%"));
        };
        for(int i=0;i<3;i++){
            final int index=i;
            TextView label=new TextView(activity);
            label.setTextSize(15);
            label.setPadding(0,dp(activity,10),0,0);
            labels[i]=label;
            layout.addView(label);
            SeekBar bar=new SeekBar(activity);
            bar.setMax(limits[i]);
            bar.setProgress(values[i]);
            bar.setFocusable(true);
            bar.setContentDescription(names[i]+" color adjustment");
            sliders[i]=bar;
            layout.addView(bar,new LinearLayout.LayoutParams(-1,dp(activity,46)));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onProgressChanged(SeekBar seek,int progress,boolean fromUser){
                    hsv[index]=index==0?progress:progress/100f;
                    refresh.run();
                }
                @Override public void onStartTrackingTouch(SeekBar seek){}
                @Override public void onStopTrackingTouch(SeekBar seek){}
            });
        }
        refresh.run();
        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Apply color",(dialog,which)->callback.selected(Color.HSVToColor(hsv)))
            .setNegativeButton("Cancel",null)
            .show();
    }
}
