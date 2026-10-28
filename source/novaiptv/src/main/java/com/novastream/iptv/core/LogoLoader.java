package com.novastream.iptv.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Memory bounded, asynchronous logos/covers. Never blocks the main/UI thread. */
public final class LogoLoader {
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bmp){return bmp.getByteCount();}
    };
    private final ExecutorService pool=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(60),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Handler handler=new Handler(Looper.getMainLooper());
    public void load(ImageView image,String url){
        image.setImageDrawable(null);image.setTag(url);
        if(url==null||url.isEmpty()||!(url.startsWith("https://")||url.startsWith("http://")))return;
        Bitmap found=cache.get(url);if(found!=null){image.setImageBitmap(found);return;}
        pool.execute(()->{try{
            // Ensure oversized or malicious images cannot consume unbounded memory.
            byte[] bytes=Net.bytes(url,512*1024);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0)return;
            int sample=1;while(bounds.outWidth/sample>240||bounds.outHeight/sample>240)sample*=2;
            BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=sample;opt.inPreferredConfig=Bitmap.Config.RGB_565;
            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);
            if(bitmap==null)return;cache.put(url,bitmap);
            handler.post(()->{if(url.equals(image.getTag()))image.setImageBitmap(bitmap);});
        }catch(Exception ignored){} });
    }
    public void close(){pool.shutdownNow();handler.removeCallbacksAndMessages(null);}
}
