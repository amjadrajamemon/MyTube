package com.novastream.iptv.ui;

import android.app.*;
import android.app.PictureInPictureParams;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.util.Rational;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import com.novastream.iptv.core.Net;
import androidx.media3.ui.PlayerView;
import com.novastream.iptv.core.Catalog;
import com.novastream.iptv.core.NovaPlaybackBridge;
import com.novastream.iptv.model.Channel;
import java.util.*;

/** One-player-per-activity lifecycle. Changing channels replaces MediaItem (never overlaps two players). */
@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private ExoPlayer player;private PlayerView view;private Catalog catalog;
    private Channel current;private TextView headline,hint;private LinearLayout toolbar;
    private Handler main=new Handler(Looper.getMainLooper());private int retries=0,fit=0;
    private String lastError="";
    private int dp(int x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
    private final Player.Listener listener=new Player.Listener(){
        @Override public void onPlayerError(PlaybackException error){
            lastError=error.getErrorCodeName();
            boolean transientNetwork=error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                ||error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                ||error.errorCode==PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
            if(transientNetwork&&retries<2){int delay=1000*(++retries);hint.setText("Reconnecting • attempt "+retries+"/2");
                main.postDelayed(()->{if(player!=null&&!isFinishing()){player.prepare();player.play();}},delay);
            }else{
                hint.setText("Playback failed: "+lastError+" • Options → diagnostics / external player");
                Toast.makeText(PlayerActivity.this,"Playback: "+lastError,Toast.LENGTH_LONG).show();
            }
        }
        @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY)hint.setText("");}
    };
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        catalog=Catalog.get();current=catalog.find(getIntent().getStringExtra("id"));if(current==null){finish();return;}
        build();player=new ExoPlayer.Builder(this).build();player.addListener(listener);NovaPlaybackBridge.claim(player);view.setPlayer(player);play(current);
    }
    private Button button(String label,Runnable run){Button b=new Button(this);b.setAllCaps(false);
        b.setText(label);b.setTextColor(Color.WHITE);b.setTextSize(13);
        b.setMinWidth(0);b.setPadding(dp(7),0,dp(7),0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF283852));
        b.setOnClickListener(v->run.run());return b;}
    private void build(){FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.BLACK);
        setContentView(frame);view=new PlayerView(this);view.setUseController(true);
        view.setControllerShowTimeoutMs(3500);frame.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);
        frame.addView(overlay,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setBackgroundColor(0xBB0D1524);
        toolbar.addView(button("‹",this::finish),new LinearLayout.LayoutParams(dp(48),dp(49)));
        headline=new TextView(this);headline.setTextColor(Color.WHITE);headline.setTextSize(16);
        headline.setSingleLine(true);headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headline.setGravity(Gravity.CENTER_VERTICAL);headline.setPadding(dp(8),0,dp(8),0);
        toolbar.addView(headline,new LinearLayout.LayoutParams(0,dp(49),1));
        toolbar.addView(button("⋮ Options",this::options),new LinearLayout.LayoutParams(-2,dp(49)));
        overlay.addView(toolbar);
        hint=new TextView(this);hint.setTextColor(0xFFFFCA8A);hint.setTextSize(12);
        hint.setMaxLines(2);hint.setPadding(dp(10),dp(4),dp(10),dp(4));
        hint.setBackgroundColor(0x990D1524);overlay.addView(hint);
    }
    private void play(Channel item){current=item;retries=0;lastError="";headline.setText(item.name);hint.setText("Connecting...");
        // setMediaItem replaces and stops the previous stream; no dual playback.
        MediaItem.Builder b=new MediaItem.Builder().setUri(item.url);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent(item.userAgent.isEmpty()?Net.USER_AGENT:item.userAgent)
            .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(12000).setReadTimeoutMs(20000);
        if(!item.referrer.isEmpty()){
            Map<String,String> headers=new HashMap<>();headers.put("Referer",item.referrer);
            http.setDefaultRequestProperties(headers);
        }
        DefaultMediaSourceFactory factory=new DefaultMediaSourceFactory(this)
            .setDataSourceFactory(new DefaultDataSource.Factory(this,http));
        String lower=item.url.toLowerCase(Locale.ROOT);
        if(lower.matches(".*\\.m3u8(?:[?#].*)?$"))b.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if(lower.matches(".*\\.mpd(?:[?#].*)?$"))b.setMimeType(MimeTypes.APPLICATION_MPD);
        else if(lower.matches(".*\\.ism(?:[?#].*)?$"))b.setMimeType(MimeTypes.APPLICATION_SS);
        player.stop();player.clearMediaItems();player.setMediaSource(factory.createMediaSource(b.build()));player.prepare();player.play();
        catalog.viewed(item.id);
        try{catalog.persist(this);}catch(Exception ignored){}
    }
    private List<Channel> eligible(){List<Channel> c=new ArrayList<>();for(Channel x:catalog.channels)
        if(x.kind.equals(current.kind)&&x.kind.equals("live"))c.add(x);return c;}
    private void next(int delta){List<Channel> all=eligible();if(all.isEmpty())return;
        int index=0;for(int i=0;i<all.size();i++)if(all.get(i).id.equals(current.id)){index=i;break;}
        play(all.get(Math.floorMod(index+delta,all.size())));
    }
    private void ratio(){fit=(fit+1)%3;view.setResizeMode(fit==0?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT:
        fit==1?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM:androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);
        Toast.makeText(this,new String[]{"Fit","Zoom","Stretch"}[fit],Toast.LENGTH_SHORT).show();}
    private void options(){new AlertDialog.Builder(this).setTitle(current.name).setItems(new String[]{
        "Open in external player","Add/remove favorite","Playback ratio","Next live channel","Previous live channel","Picture in Picture","Playback diagnostics"},(d,w)->{
        if(w==0)external();if(w==1){catalog.toggleFavorite(current.id);
            try{catalog.persist(this);}catch(Exception ignored){} }
        if(w==2)ratio();if(w==3)next(1);if(w==4)next(-1);
        if(w==5)enterPip();if(w==6)new AlertDialog.Builder(this).setTitle("Playback diagnostics")
            .setMessage(lastError.isEmpty()?"No player error recorded.":"Error: "+lastError
                +"\nCheck provider availability, codec, DRM and URL format.")
            .setPositiveButton("Close",null).show();
        }).show();}
    private void external(){try{
        Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(android.net.Uri.parse(current.url),"video/*");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(player!=null)player.pause();startActivity(Intent.createChooser(i,"Open stream using"));
    }catch(Exception e){Toast.makeText(this,"Install a compatible external player",Toast.LENGTH_LONG).show();}}
    private boolean pipSupported(){return android.os.Build.VERSION.SDK_INT>=26 &&
        getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);}
    private void enterPip(){if(!pipSupported()){Toast.makeText(this,"Picture-in-Picture not supported here",Toast.LENGTH_SHORT).show();return;}
        try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}
        catch(Exception e){Toast.makeText(this,"Picture-in-Picture unavailable",Toast.LENGTH_SHORT).show();}}
    @Override protected void onUserLeaveHint(){super.onUserLeaveHint();if(catalog.pip&&player!=null&&player.isPlaying())enterPip();}
    @Override public void onPictureInPictureModeChanged(boolean mode, Configuration c){super.onPictureInPictureModeChanged(mode,c);if(toolbar!=null)toolbar.setVisibility(mode?View.GONE:View.VISIBLE);}
    @Override public boolean onKeyDown(int key,KeyEvent e){switch(key){
        case KeyEvent.KEYCODE_CHANNEL_UP:case KeyEvent.KEYCODE_MEDIA_NEXT:next(1);return true;
        case KeyEvent.KEYCODE_CHANNEL_DOWN:case KeyEvent.KEYCODE_MEDIA_PREVIOUS:next(-1);return true;
        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:case KeyEvent.KEYCODE_HEADSETHOOK:
            if(player!=null){if(player.isPlaying())player.pause();else player.play();}return true;
        default:return super.onKeyDown(key,e);
    }}
    @Override protected void onStop(){super.onStop();if(player!=null && !(android.os.Build.VERSION.SDK_INT>=26 && isInPictureInPictureMode()))player.pause();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);if(player!=null){player.removeListener(listener);NovaPlaybackBridge.release(player);view.setPlayer(null);player.release();player=null;}super.onDestroy();}
}
