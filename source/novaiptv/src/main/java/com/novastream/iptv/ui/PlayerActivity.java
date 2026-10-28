package com.novastream.iptv.ui;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.util.Rational;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import com.novastream.iptv.core.*;
import com.novastream.iptv.model.Channel;
import java.util.*;

/** TV, touch and headset compatible player. One ExoPlayer instance; one stream at a time. */
@UnstableApi
public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView view;
    private Catalog catalog;
    private Channel current;
    private TextView headline, hint;
    private LinearLayout toolbar;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int retries=0, fit=0;
    private String activeUrl="", lastError="";
    private boolean triedAlternate=false, dialogOpen=false;
    private final Player.Listener listener=new Player.Listener(){
        @Override public void onPlaybackStateChanged(int state) {
            if(hint==null)return;
            if(state==Player.STATE_READY) {hint.setText("");hint.setVisibility(View.GONE);}
            else if(state==Player.STATE_BUFFERING) {
                hint.setVisibility(View.VISIBLE);
                hint.setText("Loading stream…");
            }
        }
        @Override public void onPlayerError(PlaybackException error) {
            lastError=error.getErrorCodeName();
            // Xtream live sources frequently serve MPEG-TS and refuse the HLS
            // .m3u8 variant (or the reverse). Try the alternate endpoint once.
            if(!triedAlternate && canSwitchLiveFormat(activeUrl)
                && (error.errorCode==PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                 || error.errorCode==PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                 || error.errorCode==PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                 || error.errorCode==PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
                String next=alternateLiveUrl(activeUrl);
                if(next!=null) {
                    triedAlternate=true;
                    showMessage("Trying alternative live stream format…");
                    handler.postDelayed(()->{if(!isFinishing()&&player!=null)loadStream(next);},250);
                    return;
                }
            }
            boolean network=error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                ||error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                ||error.errorCode==PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
            if(network && retries<2) {
                retries++;
                showMessage("Network reconnect "+retries+"/2");
                handler.postDelayed(()->{if(!isFinishing()&&player!=null)loadStream(activeUrl);},1200L*retries);
                return;
            }
            showMessage("Playback error: "+lastError+" • Options → Diagnose or External");
            if(!dialogOpen && !isFinishing()) {
                dialogOpen=true;
                new AlertDialog.Builder(PlayerActivity.this).setTitle("Stream could not play")
                    .setMessage(diagnosticMessage())
                    .setPositiveButton("Try again",(d,w)->{dialogOpen=false;play(current);})
                    .setNeutralButton("External player",(d,w)->{dialogOpen=false;external();})
                    .setNegativeButton("Close",(d,w)->dialogOpen=false)
                    .setOnCancelListener(d->dialogOpen=false).show();
            }
        }
    };
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private void showMessage(String text){if(hint!=null){hint.setVisibility(View.VISIBLE);hint.setText(text);}}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.BLACK);
        catalog=Catalog.get();
        current=catalog.find(getIntent().getStringExtra("id"));
        if(current==null){finish();return;}
        build();
        player=new ExoPlayer.Builder(this).build();
        player.addListener(listener);
        NovaPlaybackBridge.claim(player);
        view.setPlayer(player);
        play(current);
    }
    private Button button(String label,Runnable run){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);
        b.setTextColor(Color.WHITE);b.setTextSize(14);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(9),0,dp(9),0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2A3D56));
        b.setOnClickListener(v->run.run());
        b.setFocusable(true);return b;
    }
    private void build(){
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.BLACK);setContentView(frame);
        view=new PlayerView(this);view.setUseController(true);view.setControllerShowTimeoutMs(4500);
        frame.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);
        frame.addView(overlay,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(0xD30D1524);
        toolbar.addView(button("‹",this::finish),new LinearLayout.LayoutParams(dp(54),dp(48)));
        headline=new TextView(this);headline.setTextColor(Color.WHITE);headline.setTextSize(16);
        headline.setSingleLine(true);headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headline.setGravity(Gravity.CENTER_VERTICAL);headline.setPadding(dp(8),0,dp(8),0);
        toolbar.addView(headline,new LinearLayout.LayoutParams(0,dp(48),1));
        toolbar.addView(button("⋮",this::options),new LinearLayout.LayoutParams(dp(56),dp(48)));
        overlay.addView(toolbar);
        hint=new TextView(this);hint.setTextColor(0xFFFFDE9B);hint.setTextSize(13);
        hint.setMaxLines(2);hint.setPadding(dp(10),dp(5),dp(10),dp(5));
        hint.setBackgroundColor(0xAD0D1524);overlay.addView(hint);
        // Show title/menu only when the Media3 controller is visible.
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility-> {
            if(toolbar!=null && !(Build.VERSION.SDK_INT>=26 && isInPictureInPictureMode()))
                toolbar.setVisibility(visibility==View.VISIBLE?View.VISIBLE:View.GONE);
        });
    }
    private void play(Channel item){
        current=item;retries=0;triedAlternate=false;lastError="";dialogOpen=false;
        headline.setText(item.name);showMessage("Connecting…");
        loadStream(item.url);
        catalog.viewed(item.id);
        try{catalog.persist(this);}catch(Exception ignored){}
    }
    private void loadStream(String url){
        activeUrl=url;
        MediaItem.Builder media=new MediaItem.Builder().setUri(url);
        String path=url.toLowerCase(Locale.ROOT);
        if(path.matches(".*\\.m3u8(?:[?#].*)?$"))media.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if(path.matches(".*\\.mpd(?:[?#].*)?$"))media.setMimeType(MimeTypes.APPLICATION_MPD);
        else if(path.matches(".*\\.ism(?:[?#].*)?$"))media.setMimeType(MimeTypes.APPLICATION_SS);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent(current.userAgent.isEmpty()?Net.USER_AGENT:current.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12000).setReadTimeoutMs(24000);
        if(!current.referrer.isEmpty()){
            HashMap<String,String> headers=new HashMap<>();headers.put("Referer",current.referrer);
            http.setDefaultRequestProperties(headers);
        }
        DefaultMediaSourceFactory sources=new DefaultMediaSourceFactory(this)
            .setDataSourceFactory(new DefaultDataSource.Factory(this,http));
        player.stop();player.clearMediaItems();
        player.setMediaSource(sources.createMediaSource(media.build()));
        player.prepare();player.play();
    }
    // Do not mutate stored channels or credentials merely to retry stream format.
    private boolean canSwitchLiveFormat(String url){
        return StreamUrlCompat.alternateLive(current.kind,current.group,current.url,url)!=null;
    }
    private String alternateLiveUrl(String url){
        return StreamUrlCompat.alternateLive(current.kind,current.group,current.url,url);
    }
    private String diagnosticMessage(){
        StringBuilder b=new StringBuilder("Error: ").append(lastError);
        if(lastError.contains("BAD_HTTP_STATUS")||lastError.contains("FILE_NOT_FOUND"))
            b.append("\nThe provider rejected the stream URL, or it has expired. Refresh this playlist and confirm the subscription.");
        else if(lastError.contains("DECODING")||lastError.contains("CODEC"))
            b.append("\nYour device may not support the stream codec. Try an external player.");
        else if(lastError.contains("NETWORK"))
            b.append("\nCheck the connection and provider server.");
        else b.append("\nCheck stream format, provider availability and DRM authorization.");
        b.append("\nLive TS/HLS fallback: ").append(triedAlternate?"attempted":"not needed");
        b.append("\nOnly authorized streams are supported.");
        return b.toString();
    }
    private List<Channel> eligible(){
        List<Channel> out=new ArrayList<>();
        if(!"live".equals(StreamKind.detect(current.kind,current.group,current.url)))return out;
        for(Channel c:catalog.channels)
            if(catalog.isSourceEnabled(c.source)&&"live".equals(StreamKind.detect(c.kind,c.group,c.url)))out.add(c);
        return out;
    }
    private void next(int delta){
        List<Channel> channels=eligible();if(channels.isEmpty())return;
        int position=0;for(int i=0;i<channels.size();i++)if(channels.get(i).id.equals(current.id)){position=i;break;}
        play(channels.get(Math.floorMod(position+delta,channels.size())));
    }
    private void ratio(){
        fit=(fit+1)%3;
        view.setResizeMode(fit==0?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT:
            fit==1?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);
        Toast.makeText(this,new String[]{"Fit","Zoom","Stretch"}[fit],Toast.LENGTH_SHORT).show();
    }
    private void options(){
        String[] labels={"Retry current stream","Try alternate TS / HLS","Open in external player",
            "Add / remove favorite","Fit / Zoom / Stretch","Next live channel",
            "Previous live channel","Picture in Picture","Playback diagnostics"};
        new AlertDialog.Builder(this).setTitle(current.name)
            .setItems(labels,(d,w)->{
                switch(w){
                    case 0:play(current);break;
                    case 1:
                        String alt=alternateLiveUrl(activeUrl);
                        if(alt!=null){triedAlternate=true;showMessage("Trying alternate stream…");loadStream(alt);}
                        else Toast.makeText(this,"Alternate format only available for Xtream /live/ TS/HLS",Toast.LENGTH_LONG).show();
                        break;
                    case 2:external();break;
                    case 3:catalog.toggleFavorite(current.id);
                        try{catalog.persist(this);}catch(Exception ignored){}break;
                    case 4:ratio();break;
                    case 5:next(1);break;
                    case 6:next(-1);break;
                    case 7:enterPip();break;
                    case 8:new AlertDialog.Builder(this).setTitle("Playback diagnostics")
                        .setMessage(diagnosticMessage()).setPositiveButton("OK",null).show();break;
                    default:break;
                }
            }).show();
    }
    private void external(){
        try{
            Intent i=new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(android.net.Uri.parse(activeUrl),"video/*");
            if(player!=null)player.pause();
            startActivity(Intent.createChooser(i,"Open stream with"));
        }catch(Exception e){Toast.makeText(this,"Install a compatible external video player",Toast.LENGTH_LONG).show();}
    }
    private void enterPip(){
        if(Build.VERSION.SDK_INT<26||!getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)){
            Toast.makeText(this,"Picture-in-Picture unavailable on this device",Toast.LENGTH_SHORT).show();return;
        }
        try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}
        catch(Exception e){Toast.makeText(this,"Could not enter Picture-in-Picture",Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onUserLeaveHint(){super.onUserLeaveHint();if(catalog.pip&&player!=null&&player.isPlaying())enterPip();}
    @Override public void onPictureInPictureModeChanged(boolean mode,Configuration config){
        super.onPictureInPictureModeChanged(mode,config);
        if(toolbar!=null)toolbar.setVisibility(mode?View.GONE:View.VISIBLE);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event){
        switch(key){
            case KeyEvent.KEYCODE_CHANNEL_UP:case KeyEvent.KEYCODE_MEDIA_NEXT:next(1);return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:case KeyEvent.KEYCODE_MEDIA_PREVIOUS:next(-1);return true;
            case KeyEvent.KEYCODE_MENU:case KeyEvent.KEYCODE_F1:options();return true;
            case KeyEvent.KEYCODE_ESCAPE:case KeyEvent.KEYCODE_BACK:finish();return true;
            case KeyEvent.KEYCODE_SPACE:case KeyEvent.KEYCODE_K:
                if(player!=null){if(player.isPlaying())player.pause();else player.play();}return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_J:
                if(player!=null&&player.isCurrentMediaItemSeekable())player.seekTo(Math.max(0,player.getCurrentPosition()-10000));return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:case KeyEvent.KEYCODE_L:
                if(player!=null&&player.isCurrentMediaItemSeekable())player.seekTo(player.getCurrentPosition()+10000);return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:case KeyEvent.KEYCODE_HEADSETHOOK:
                if(player!=null){if(player.isPlaying())player.pause();else player.play();}return true;
            default:return super.onKeyDown(key,event);
        }
    }
    @Override protected void onStop(){
        super.onStop();
        if(player!=null && !(Build.VERSION.SDK_INT>=26&&isInPictureInPictureMode()))player.pause();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(player!=null){player.removeListener(listener);NovaPlaybackBridge.release(player);view.setPlayer(null);player.release();player=null;}
        super.onDestroy();
    }
}
