package com.novastream.iptv.ui;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.novastream.iptv.core.*;
import com.novastream.iptv.model.Channel;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Responsive Views UI: native D-pad navigation on TV, touch on phones/tablets. */
public final class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Catalog catalog; private Style style; private final LogoLoader logos=new LogoLoader();
    private LinearLayout root,header,tabs; private ListView list; private GridView grid;
    private TextView emptyState; private boolean gridMode=false; private int viewMode=-1;
    private TextView status; private EditText searchBox;
    private String tab="Live",group="",search="",selectedSource="";
    private List<Channel> shown=new ArrayList<>();
    private boolean tv;
    private static final int PICK_PLAYLIST=61;
    @Override public void onCreate(Bundle b){super.onCreate(b);
        tv=getPackageManager().hasSystemFeature("android.software.leanback") || getPackageManager().hasSystemFeature("android.hardware.type.television");
        viewMode=getSharedPreferences("nova_appearance",MODE_PRIVATE).getInt("view_mode",-1);
        try{catalog=Catalog.load(this);ready();}
        catch(Exception e){new AlertDialog.Builder(this).setTitle("Encrypted library unavailable")
            .setMessage("Your saved playlist cannot be unlocked (e.g. device restore or Keystore reset). You can start a fresh local library; this does not affect your provider.")
            .setPositiveButton("Reset local library",(d,w)->{new SecureStore(this).reset();
                try{catalog=Catalog.load(this);ready();}catch(Exception ex){showError("Storage",ex);}})
            .setNegativeButton("Exit",(d,w)->finish()).setCancelable(false).show();}
    }
    private void ready(){applyTheme();if(catalog.channels.isEmpty())getWindow().getDecorView().postDelayed(this::sourceChooser,600);
        if(!catalog.epgUrl.isEmpty())refreshEpg(false);}
    private void applyTheme(){style=new Style(catalog.theme,catalog.textSize,catalog.customBg,catalog.customAccent,catalog.customText);getWindow().setStatusBarColor(style.bg);getWindow().setNavigationBarColor(style.bg);build();}
    private Button button(String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setTextSize(13*style.scale);b.setTextColor(style.text);b.setAllCaps(false);
        b.setPadding(Style.dp(this,9),Style.dp(this,6),Style.dp(this,9),Style.dp(this,6));b.setMinHeight(Style.dp(this,46));
        b.setBackground(style.panel(style.surface,14,false));b.setOnClickListener(v->action.run());
        b.setOnFocusChangeListener((v,focus)->{b.setBackground(style.panel(style.surface,14,focus));b.setScaleX(focus?1.03f:1f);b.setScaleY(focus?1.03f:1f);});return b;
    }
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void extraMenu(){new AlertDialog.Builder(this).setTitle("Nova IPTV")
        .setItems(new String[]{"★ Manage Favorites","Add / manage sources","Manage playlists",
            "Grid / List display","Themes","Settings","Groups","Program guide"},(d,w)->{
            if(w==0)manageFavorites();else if(w==1)sourceChooser();else if(w==2)manageSources();
            else if(w==3)toggleGrid();else if(w==4)themes();else if(w==5)settings();
            else if(w==6)groups();
            else if(w==7){if(catalog.epgUrl.isEmpty())promptEpg();else refreshEpg(true);}
        }).show();}
    private void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Style.dp(this,12),Style.dp(this,10),Style.dp(this,12),Style.dp(this,8));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{style.bg,style.surface,style.bg});root.setBackground(background);
        setContentView(root);
        header=horizontal();TextView title=style.label(this,"◈ NOVA IPTV",tv?22:18,true);
        title.setTextColor(style.accent);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title,new LinearLayout.LayoutParams(0,Style.dp(this,50),1));
        header.addView(button("☰",this::extraMenu),
            new LinearLayout.LayoutParams(Style.dp(this,56),Style.dp(this,48)));
        // The view switch lives in the scrollable actions and menu, not the title bar.
        root.addView(header);
        if(getResources().getConfiguration().screenHeightDp>=420){
            HorizontalScrollView quick=new HorizontalScrollView(this);quick.setHorizontalScrollBarEnabled(false);
            LinearLayout actions=horizontal();
            actions.addView(button("★ Favorites",this::manageFavorites));
            actions.addView(button("≡ Playlists",this::manageSources));
            actions.addView(button("▦ Grid / ☷ List",this::toggleGrid));
            actions.addView(button("＋ Source",this::sourceChooser));
            actions.addView(button("◉ Themes",this::themes));
            actions.addView(button("⚙ Settings",this::settings));
            actions.addView(button("Groups",this::groups));
            actions.addView(button("Guide",()->{if(catalog.epgUrl.isEmpty())promptEpg();else refreshEpg(true);}));
            quick.addView(actions);root.addView(quick);
        }
        searchBox=new EditText(this);searchBox.setSingleLine(true);searchBox.setTextColor(style.text);
        searchBox.setHintTextColor(style.accent);searchBox.setTextSize(15*style.scale);
        searchBox.setHint("Search live TV, movies, series");
        searchBox.setPadding(Style.dp(this,12),0,Style.dp(this,12),0);
        searchBox.setBackground(style.panel(style.surface,12,false));
        root.addView(searchBox,new LinearLayout.LayoutParams(-1,Style.dp(this,48)));
        searchBox.setText(search);searchBox.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){search=s.toString();refilter();}public void afterTextChanged(Editable e){}});
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);tabs=horizontal();
        for(String t:new String[]{"Live","Movies","Series","Favorites","Recent","All"}){
            Button b=button(t,()->{tab=t;group="";rebuildTabs();refilter();});
            b.setMinWidth(Style.dp(this,88));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,Style.dp(this,Math.max(51,(int)(46*style.scale))));p.setMargins(Style.dp(this,3),Style.dp(this,5),Style.dp(this,3),Style.dp(this,5));tabs.addView(b,p);
        }sc.addView(tabs);root.addView(sc);rebuildTabs();
        list=new ListView(this);list.setDividerHeight(Style.dp(this,5));list.setDivider(null);list.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        list.setClipToPadding(false);list.setPadding(0,Style.dp(this,8),0,Style.dp(this,8));
        list.setOnItemClickListener((a,v,pos,id)->{if(pos<shown.size())open(shown.get(pos));});
        list.setOnItemLongClickListener((a,v,pos,id)->{if(pos<shown.size())actions(shown.get(pos));return true;});
        grid=new GridView(this);
        int widthDp=getResources().getConfiguration().screenWidthDp;
        int minimumWidth=(int)(tv?225*style.scale:155*style.scale);
        grid.setNumColumns(Math.min(6,Math.max(1,(widthDp-24)/Math.max(135,minimumWidth))));
        grid.setHorizontalSpacing(Style.dp(this,9));grid.setVerticalSpacing(Style.dp(this,9));
        grid.setPadding(Style.dp(this,4),Style.dp(this,8),Style.dp(this,4),Style.dp(this,12));
        grid.setClipToPadding(false);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        grid.setOnItemClickListener((a,v,pos,id)->{if(pos<shown.size())open(shown.get(pos));});
        grid.setOnItemLongClickListener((a,v,pos,id)->{if(pos<shown.size())actions(shown.get(pos));return true;});
        FrameLayout library=new FrameLayout(this);
        library.addView(list,new FrameLayout.LayoutParams(-1,-1));
        library.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        emptyState=style.label(this,"",tv?18:15,false);
        emptyState.setTextColor(style.text);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(Style.dp(this,23),Style.dp(this,30),Style.dp(this,23),Style.dp(this,25));
        library.addView(emptyState,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        root.addView(library,new LinearLayout.LayoutParams(-1,0,1));
        status=style.label(this,"",11,false);status.setTextColor(style.accent);
        status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(status,new LinearLayout.LayoutParams(-1,Style.dp(this,30)));
        refilter();
    }
    private boolean useGrid(){return viewMode<0?(tab.equals("Movies")||tab.equals("Series")):viewMode==1;}
    private void toggleGrid(){
        viewMode=useGrid()?0:1;
        getSharedPreferences("nova_appearance",MODE_PRIVATE).edit().putInt("view_mode",viewMode).apply();
        applyTheme();
    }
    private void rebuildTabs(){for(int i=0;i<tabs.getChildCount();i++){
        Button b=(Button)tabs.getChildAt(i);boolean selected=b.getText().toString().equals(tab);
        b.setTextColor(selected?style.bg:style.text);b.setBackground(style.panel(selected?style.accent:style.surface,13,selected));
    }}
    private void refilter(){if(list==null||catalog==null)return;
        gridMode=useGrid();
        shown=new ArrayList<>(catalog.filtered(tab,search,group));
        if(!selectedSource.isEmpty())shown.removeIf(c->!c.source.equals(selectedSource));
        list.setAdapter(new ChannelAdapter(shown));
        grid.setAdapter(new PosterAdapter(shown));
        list.setVisibility(shown.isEmpty()||gridMode?View.GONE:View.VISIBLE);
        grid.setVisibility(shown.isEmpty()||!gridMode?View.GONE:View.VISIBLE);
        emptyState.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);
        if(shown.isEmpty()){
            if(tab.equals("Movies")||tab.equals("Series"))
                emptyState.setText("No "+tab.toLowerCase(Locale.ROOT)+" found for this selection.\n\nCheck that your provider includes VOD access, then use Playlists → Refresh.\n\nTry All if a playlist uses unusual categories.");
            else emptyState.setText(catalog.channels.isEmpty()?"Your library is empty.\n\nUse + Source to add an authorized IPTV playlist.":"No matching items. Change your search, category or playlist filter.");
        }
        int liveCount=0,movieCount=0,seriesCount=0;
        for(Channel entry:catalog.channels){
            if(!catalog.isSourceEnabled(entry.source))continue;
            String k=StreamKind.detect(entry.kind,entry.group,entry.url);
            if(k.equals("live"))liveCount++;
            else if(k.equals("movie"))movieCount++;
            else if(k.equals("series")||k.equals("episode"))seriesCount++;
        }
        status.setText("Live "+liveCount+"  •  Movies "+movieCount+"  •  Series "+seriesCount+
            "  •  "+shown.size()+" shown  •  "+catalog.sources().size()+" playlists  •  "+(gridMode?"Grid":"List"));
    }
    private class ChannelAdapter extends BaseAdapter {
        private final List<Channel> entries;ChannelAdapter(List<Channel> entries){this.entries=entries;}
        public int getCount(){return entries.size();}public Object getItem(int p){return entries.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View convert,android.view.ViewGroup parent){
            Channel c=entries.get(p);
            LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Style.dp(MainActivity.this,14),Style.dp(MainActivity.this,3+catalog.rowDensity*3),Style.dp(MainActivity.this,14),Style.dp(MainActivity.this,3+catalog.rowDensity*3));
            card.setBackground(style.panel(style.surface,14,false));card.setFocusable(false);
            
            TextView name=style.label(MainActivity.this,(catalog.favorites.contains(c.id)?"★  ":"▷  ")+c.name,tv?19:16,true);
            name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);card.addView(name);
            String current=c.kind.equals("live")?catalog.epg.current(c.tvgId,c.name):"";
            String meta=c.group+"   •   "+StreamKind.detect(c.kind,c.group,c.url).toUpperCase(Locale.ROOT)+(current.isEmpty()?"":"   •   Now: "+current);
            TextView sub=style.label(MainActivity.this,meta,12,false);sub.setSingleLine(true);sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setTextColor(style.accent);card.addView(sub);
            LinearLayout row=horizontal();row.setBackground(style.panel(style.surface,14,false));row.setFocusable(true);
            int thumb=Style.dp(MainActivity.this,tv?74:56);
            ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(thumb,thumb);ip.setMargins(Style.dp(MainActivity.this,5),Style.dp(MainActivity.this,4),0,Style.dp(MainActivity.this,4));
            row.addView(image,ip);logos.load(image,c.logo);
            row.addView(card,new LinearLayout.LayoutParams(0,-2,1));
            row.setOnClickListener(v->open(c));row.setOnLongClickListener(v->{actions(c);return true;});
            row.setOnFocusChangeListener((v,focus)->row.setBackground(style.panel(style.surface,14,focus)));
            return row;
        }
    }
    private class PosterAdapter extends BaseAdapter {
        private final List<Channel> entries;
        PosterAdapter(List<Channel> items){entries=items;}
        public int getCount(){return entries.size();}
        public Object getItem(int position){return entries.get(position);}
        public long getItemId(int position){return position;}
        public View getView(int position,View old,ViewGroup parent){
            Channel c=entries.get(position);
            LinearLayout tile=new LinearLayout(MainActivity.this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(Style.dp(MainActivity.this,7),Style.dp(MainActivity.this,8),
                Style.dp(MainActivity.this,7),Style.dp(MainActivity.this,10));
            tile.setBackground(style.panel(style.surface,14,false));
            String kind=StreamKind.detect(c.kind,c.group,c.url);
            ImageView art=new ImageView(MainActivity.this);
            art.setBackgroundColor(0x30151F3B);
            art.setScaleType(kind.equals("movie")||kind.equals("series")?
                ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);
            int imageHeight=Style.dp(MainActivity.this,kind.equals("movie")||kind.equals("series")?tv?190:168:tv?128:102);
            tile.addView(art,new LinearLayout.LayoutParams(-1,imageHeight));
            logos.load(art,c.logo);
            TextView title=style.label(MainActivity.this,(catalog.favorites.contains(c.id)?"★ ":"")+c.name,tv?16:14,true);
            title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
            title.setMinHeight(Style.dp(MainActivity.this,(int)(65*style.scale)));
            tile.addView(title,new LinearLayout.LayoutParams(-1,-2));
            TextView description=style.label(MainActivity.this,kind.toUpperCase(Locale.ROOT)+"  •  "+c.group,10,false);
            description.setTextColor(style.accent);
            description.setSingleLine(true);description.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(description);
            tile.setFocusable(true);tile.setClickable(true);
            tile.setOnFocusChangeListener((v,focused)->{
                tile.setBackground(style.panel(focused?style.accent:style.surface,14,focused));
                title.setTextColor(focused?style.bg:style.text);
                tile.setScaleX(1f);tile.setScaleY(1f);
            });
            tile.setOnClickListener(v->open(c));
            tile.setOnLongClickListener(v->{actions(c);return true;});
            return tile;
        }
    }
    private void open(Channel c){
        if(c.kind.equals("series") && c.url.contains("get_series_info")){openSeries(c);return;}
        catalog.viewed(c.id);save();startActivity(new Intent(this,PlayerActivity.class).putExtra("id",c.id));
    }
    private void manageFavorites(){startActivity(new Intent(this,FavoritesActivity.class));}
    private void actions(Channel c){
        String action=catalog.favorites.contains(c.id)?"Remove favorite":"Add favorite";
        new AlertDialog.Builder(this).setTitle(c.name).setItems(new String[]{"Play",action,"View program guide","Copy channel name"},(d,w)->{
            if(w==0)open(c);if(w==1){catalog.toggleFavorite(c.id);save();refilter();}
            if(w==2)showGuide(c);if(w==3){android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("Channel name",c.name));toast("Channel name copied");}
        }).show();
    }
    private void groups(){Set<String> groups=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);for(Channel c:catalog.channels)groups.add(c.group);
        List<String> entries=new ArrayList<>();entries.add("All groups");entries.addAll(groups);
        new AlertDialog.Builder(this).setTitle("Choose a group").setItems(entries.toArray(new String[0]),(d,w)->{group=w==0?"":entries.get(w);refilter();}).show();}
    private void sourceChooser(){new AlertDialog.Builder(this).setTitle("Add authorized content")
        .setItems(new String[]{"Import M3U / M3U8 URL","Import local M3U file","Connect Xtream-compatible portal","Set XMLTV guide URL","Manage playlists"},(d,w)->{
            if(w==0)promptUrl();if(w==1)pickFile();if(w==2)promptXtream();if(w==3)promptEpg();if(w==4)manageSources();
        }).setNegativeButton("Close",null).show();}
    private EditText field(String hint,int type){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setInputType(type);e.setTextSize(15);e.setPadding(Style.dp(this,12),Style.dp(this,12),Style.dp(this,12),Style.dp(this,12));return e;}
    private void promptUrl(){EditText e=field("https://example.com/list.m3u",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Import M3U playlist").setMessage("Add a playlist supplied by a provider you are authorized to use.")
            .setView(e).setNegativeButton("Cancel",null).setPositiveButton("Import",(d,w)->importUrl(e.getText().toString().trim())).show();}
    private void importUrl(String url){if(!(url.startsWith("https://")||url.startsWith("http://"))){toast("Enter a full HTTP(S) URL");return;}
        runTask("Importing playlist",()->{
            M3uParser.Result r;try(InputStream in=Net.open(url,12*1024*1024)){r=M3uParser.parse(in,url,url);}
            runOnUiThread(()->{catalog.replaceSource(url,r.channels);if(catalog.epgUrl.isEmpty()&&!r.epgUrl.isEmpty())catalog.epgUrl=r.epgUrl;
                save();refilter();toast("Imported "+r.channels.size()+" streams"+(r.rejected>0?"; skipped "+r.rejected:""));
                if(!catalog.epgUrl.isEmpty())refreshEpg(false);
            });
        });
    }
    private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_PLAYLIST);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request!=PICK_PLAYLIST||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        runTask("Reading local playlist",()->{
            M3uParser.Result r;try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Cannot read document");r=M3uParser.parse(new BoundedStream(in,12*1024*1024),uri.toString(),"");}
            runOnUiThread(()->{catalog.replaceSource(uri.toString(),r.channels);save();refilter();toast("Imported "+r.channels.size()+" items");});
        });
    }
    private void promptXtream(){LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(Style.dp(this,12),0,Style.dp(this,12),0);
        EditText server=field("https://portal.example:port",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText username=field("Username",android.text.InputType.TYPE_CLASS_TEXT);
        EditText password=field("Password",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fields.addView(server);fields.addView(username);fields.addView(password);
        new AlertDialog.Builder(this).setTitle("Xtream-compatible portal").setMessage("Use your own authorized provider. Prefer HTTPS where supported.")
            .setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Connect",(d,w)->{
                String host=server.getText().toString().trim().replaceAll("/+$",""),u=username.getText().toString(),p=password.getText().toString();
                runTask("Loading portal",()->{
                    Xtream x=new Xtream(host,u,p);List<Channel> items=x.loadAll();String warnings=x.warnings();
                    runOnUiThread(()->{
                        String key=sourceKey(host,u,p);
                        List<Channel> mapped=remapXtream(items,key);
                        catalog.replaceSource(key,mapped);
                        catalog.sourceHosts.put(key,host);
                        catalog.xtreamCredentials.put(key,u+"\n"+p);
                        if(catalog.epgUrl.isEmpty())catalog.epgUrl=x.epg();selectedSource="";group="";save();refilter();
                        toast("Loaded "+items.size()+" portal items");
                        if(!warnings.isEmpty())new AlertDialog.Builder(this).setTitle("Portal categories unavailable")
                            .setMessage(warnings+". Recheck your subscription, portal URL or provider.")
                            .setPositiveButton("OK",null).show();
                        refreshEpg(false);});
                });
            }).show();
    }
    private void openSeries(Channel series){String credentials=catalog.xtreamCredentials.get(series.source);
        if(credentials==null||!credentials.contains("\n")){toast("Series credentials are unavailable; reconnect the portal");return;}
        String[] parts=credentials.split("\n",2);
        runTask("Loading episodes",()->{
            List<Channel> eps=new Xtream(catalog.sourceHost(series.source),parts[0],parts[1]).episodes(series.url,series.name);
            runOnUiThread(()->{if(eps.isEmpty()){toast("No playable episodes returned by portal");return;}
                String[] labels=new String[eps.size()];for(int i=0;i<eps.size();i++)labels[i]=eps.get(i).name;
                new AlertDialog.Builder(this).setTitle(series.name).setItems(labels,(d,w)->{
                    Channel ep=eps.get(w);
                    Channel own=new Channel(M3uParser.hash(series.source+"|"+ep.id),ep.name,ep.url,ep.group,ep.logo,ep.tvgId,ep.kind,series.source,ep.userAgent,ep.referrer);
                    if(catalog.find(own.id)==null)catalog.channels.add(own);save();open(own);
                }).setNegativeButton("Close",null).show();
            });
        });
    }
    private void promptEpg(){EditText e=field("https://example.com/epg.xml.gz",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        e.setText(catalog.epgUrl);new AlertDialog.Builder(this).setTitle("XMLTV program guide").setView(e).setNegativeButton("Cancel",null)
            .setPositiveButton("Load",(d,w)->{String s=e.getText().toString().trim();if(!(s.startsWith("https://")||s.startsWith("http://"))){toast("Guide requires HTTP(S)");return;}
                catalog.epgUrl=s;save();refreshEpg(true);}).show();}
    private void refreshEpg(boolean report){if(catalog.epgUrl.isEmpty())return;
        runTask("Refreshing XMLTV guide",()->{
            Epg next;try(InputStream in=Net.open(catalog.epgUrl,32*1024*1024)){next=Epg.parse(in);}
            runOnUiThread(()->{catalog.epg=next;refilter();if(report)toast("Guide loaded for "+next.count()+" channel IDs");});
        });
    }
    private void showGuide(Channel c){List<Epg.Program> programs=catalog.epg.schedule(c.tvgId,c.name);
        if(programs.isEmpty()){new AlertDialog.Builder(this).setTitle(c.name).setMessage("No matching program data. Try refreshing XMLTV or checking the tvg-id mapping.").setPositiveButton("OK",null).show();return;}
        SimpleDateFormat f=new SimpleDateFormat("EEE HH:mm",Locale.getDefault());StringBuilder b=new StringBuilder();
        for(Epg.Program p:programs){b.append(f.format(new Date(p.start))).append("–").append(f.format(new Date(p.end))).append("   ").append(p.title).append("\n\n");}
        new AlertDialog.Builder(this).setTitle(c.name+" • 48h guide").setMessage(b.toString()).setPositiveButton("Close",null).show();}
    private String sourceKey(String host,String user,String password){
        String legacy=catalog.xtreamCredentials.get(host);
        if(legacy!=null&&legacy.equals(user+String.valueOf((char)10)+password)&&catalog.sourceSize(host)>0)return host;
        return "xtream:"+M3uParser.hash(host.toLowerCase(Locale.ROOT)+"|"+user);
    }
    private List<Channel> remapXtream(List<Channel> items,String key){
        ArrayList<Channel> mapped=new ArrayList<>();
        for(Channel c:items)mapped.add(new Channel(M3uParser.hash(key+"|"+c.id),c.name,c.url,
                c.group,c.logo,c.tvgId,c.kind,key,c.userAgent,c.referrer));
        return mapped;
    }
    private void manageSources(){
        List<String> sources=catalog.sources();
        if(sources.isEmpty()){toast("No playlists added. Choose + Source to import one.");return;}
        ArrayList<String> labels=new ArrayList<>();
        labels.add("★ Show all enabled playlists");
        for(String key:sources){
            String label=catalog.sourceName(key);
            String state=catalog.isSourceEnabled(key)?"ON":"OFF";
            labels.add((selectedSource.equals(key)?"✓ ":"")+label+"  •  "+state+"  •  "+catalog.sourceSize(key)+" items");
        }
        new AlertDialog.Builder(this).setTitle("Manage playlists ("+sources.size()+")")
            .setItems(labels.toArray(new String[0]),(d,w)->{
                if(w==0){selectedSource="";group="";refilter();toast("Showing all enabled playlists");return;}
                sourceActions(sources.get(w-1));
            }).setNeutralButton("+ Add", (d,w)->sourceChooser()).setNegativeButton("Close",null).show();
    }
    private void sourceActions(String key){
        final boolean on=catalog.isSourceEnabled(key);
        String title=catalog.sourceName(key)+" ("+catalog.sourceSize(key)+" items)";
        new AlertDialog.Builder(this).setTitle(title)
            .setItems(new String[]{"Show only this playlist",on?"Disable playlist":"Enable playlist",
                    "Rename playlist","Refresh playlist","Remove playlist"},(d,w)->{
                if(w==0){
                    if(!catalog.isSourceEnabled(key))catalog.setSourceEnabled(key,true);
                    selectedSource=key;group="";save();refilter();
                }
                if(w==1){
                    catalog.setSourceEnabled(key,!on);
                    if(on&&selectedSource.equals(key))selectedSource="";
                    group="";save();refilter();manageSources();
                }
                if(w==2)renamePlaylist(key);
                if(w==3)refreshPlaylist(key);
                if(w==4)new AlertDialog.Builder(this).setTitle("Remove playlist?")
                    .setMessage("Remove the playlist, its saved streams and its favorites from this device? This does not change the provider account.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Remove",(a,b)->{
                        catalog.removeSource(key);if(selectedSource.equals(key))selectedSource="";
                        group="";save();refilter();manageSources();
                    }).show();
            }).setNegativeButton("Back",(d,w)->manageSources()).show();
    }
    private void renamePlaylist(String key){
        EditText e=field("Playlist name",android.text.InputType.TYPE_CLASS_TEXT);
        e.setText(catalog.sourceName(key));
        new AlertDialog.Builder(this).setTitle("Rename playlist").setView(e)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
                if(!catalog.renameSource(key,e.getText().toString())){toast("Enter a name up to 48 characters");return;}
                save();refilter();manageSources();
            }).show();
    }
    private void refreshPlaylist(String key){
        final String credentials=catalog.xtreamCredentials.get(key);
        if(credentials!=null&&credentials.indexOf((char)10)>=0){
            String[] pair=credentials.split(String.valueOf((char)10),2);
            String host=catalog.sourceHost(key);
            runTask("Refreshing portal",()->{
                Xtream portal=new Xtream(host,pair[0],pair[1]);
                List<Channel> items=portal.loadAll();
                if(items.isEmpty())throw new IOException("Provider returned no items; existing playlist kept.");
                runOnUiThread(()->{
                    catalog.replaceSource(key,remapXtream(items,key));save();refilter();
                    toast("Refreshed "+catalog.sourceSize(key)+" items");
                });
            });return;
        }
        runTask("Refreshing playlist",()->{
            M3uParser.Result parsed;
            if(key.startsWith("content:")){
                try(InputStream stream=getContentResolver().openInputStream(Uri.parse(key))){
                    if(stream==null)throw new IOException("File access expired. Re-import the local playlist.");
                    parsed=M3uParser.parse(new BoundedStream(stream,12*1024*1024),key,"");
                }
            }else if(key.startsWith("https://")||key.startsWith("http://")){
                try(InputStream stream=Net.open(key,12*1024*1024)){
                    parsed=M3uParser.parse(stream,key,key);
                }
            }else throw new IOException("Unknown playlist type. Import the source again.");
            if(parsed.channels.isEmpty())throw new IOException("No playable streams returned; previous copy preserved.");
            runOnUiThread(()->{
                catalog.replaceSource(key,parsed.channels);save();refilter();
                toast("Refreshed "+catalog.sourceSize(key)+" items");
            });
        });
    }
    private void themes(){new AlertDialog.Builder(this).setTitle("16 high-definition theme palettes")
        .setSingleChoiceItems(Style.NAMES,catalog.theme,(d,w)->{catalog.theme=w;save();applyTheme();d.dismiss();})
        .setNegativeButton("Close",null).show();}
    private void settings(){new AlertDialog.Builder(this).setTitle("Customize Nova IPTV")
        .setItems(new String[]{"Themes (16 presets)","Custom background, accent and text colors","Text / accessibility scale","Row spacing / density","Picture-in-Picture: "+(catalog.pip?"ON":"OFF"),"Refresh EPG","Clear viewing history"},(d,w)->{
            if(w==0)themes();if(w==1)customColors();if(w==2)fontSize();if(w==3)density();
            if(w==4){catalog.pip=!catalog.pip;save();toast("PiP "+(catalog.pip?"enabled":"disabled"));}
            if(w==5){if(catalog.epgUrl.isEmpty())promptEpg();else refreshEpg(true);}
            if(w==6)new AlertDialog.Builder(this).setTitle("Clear history?").setNegativeButton("Cancel",null)
                .setPositiveButton("Clear",(d2,w2)->{catalog.recent.clear();save();refilter();}).show();
        }).show();}
    private void customColors(){LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);
        EditText bg=field("Background #RRGGBB (blank = preset)",android.text.InputType.TYPE_CLASS_TEXT);
        EditText accent=field("Accent #RRGGBB (blank = preset)",android.text.InputType.TYPE_CLASS_TEXT);
        EditText txt=field("Text #RRGGBB (blank = preset)",android.text.InputType.TYPE_CLASS_TEXT);
        bg.setText(catalog.customBg);accent.setText(catalog.customAccent);txt.setText(catalog.customText);
        fields.addView(bg);fields.addView(accent);fields.addView(txt);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Customize theme colors")
            .setMessage("Enter six-digit hexadecimal colors. Leave empty to use the preset. Maintain readable contrast.")
            .setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String[] vals={bg.getText().toString().trim(),accent.getText().toString().trim(),txt.getText().toString().trim()};
            for(String value:vals)if(!value.isEmpty()&&!value.matches("#[a-fA-F0-9]{6}")){toast("Colors must use #RRGGBB");return;}
            catalog.customBg=vals[0];catalog.customAccent=vals[1];catalog.customText=vals[2];save();applyTheme();dialog.dismiss();
        }));dialog.show();
    }
    private void density(){new AlertDialog.Builder(this).setTitle("Channel row spacing")
        .setSingleChoiceItems(new String[]{"Compact","Balanced","Comfortable"},catalog.rowDensity,(d,w)->{
            catalog.rowDensity=w;save();applyTheme();d.dismiss();}).setNegativeButton("Close",null).show();}
    private void fontSize(){String[] options={"Compact 100%","Comfortable 112%","Large 124%","Extra large 136%","Maximum 148%"};
        new AlertDialog.Builder(this).setTitle("Text size").setSingleChoiceItems(options,catalog.textSize,(d,w)->{
            catalog.textSize=w;save();applyTheme();d.dismiss();}).setNegativeButton("Close",null).show();}
    private void save(){try{catalog.persist(this);}catch(Exception e){showError("Saving encrypted library",e);}}
    private void runTask(String title,Throwing task){
        ProgressBar progress=new ProgressBar(this);progress.setPadding(24,24,24,24);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(progress).setCancelable(false).create();dialog.show();
        worker.execute(()->{try{task.run();runOnUiThread(dialog::dismiss);}catch(Exception e){runOnUiThread(()->{dialog.dismiss();showError(title,e);});}});
    }
    @FunctionalInterface private interface Throwing{void run() throws Exception;}
    private void showError(String title,Exception e){String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        // Avoid showing API URLs and embedded credentials from exception chains.
        message=message.replaceAll("(?i)(password|username)=([^&\\s]+)","$1=REDACTED");
        new AlertDialog.Builder(this).setTitle(title+" failed").setMessage(message).setPositiveButton("OK",null).show();}
    private void toast(String m){Toast.makeText(this,m,Toast.LENGTH_LONG).show();}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);applyTheme();}
    @Override public void onBackPressed(){if(!group.isEmpty()){group="";refilter();}else if(!search.isEmpty()){search="";searchBox.setText("");}else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();if(catalog!=null&&list!=null)refilter();}
    @Override protected void onDestroy(){worker.shutdownNow();logos.close();super.onDestroy();}
    private static class BoundedStream extends FilterInputStream{
        private final long limit;private long count;
        BoundedStream(InputStream in,long max){super(in);limit=max;}
        @Override public int read()throws IOException{int x=super.read();if(x!=-1)check(1);return x;}
        @Override public int read(byte[] b,int off,int len)throws IOException{int n=super.read(b,off,len);if(n>0)check(n);return n;}
        private void check(long n)throws IOException{count+=n;if(count>limit)throw new IOException("File too large");}
    }
}
