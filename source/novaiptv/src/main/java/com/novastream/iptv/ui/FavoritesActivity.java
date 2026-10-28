package com.novastream.iptv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.content.res.ColorStateList;
import com.novastream.iptv.core.Catalog;
import com.novastream.iptv.core.StreamKind;
import com.novastream.iptv.model.Channel;
import java.util.*;

/** Standalone, touch and D-pad friendly Favorites management screen. */
public final class FavoritesActivity extends Activity {
    private Catalog catalog;
    private LinearLayout root, folderBar, filterBar;
    private ListView items;
    private TextView count;
    private EditText search;
    private String folder="", kind="All";
    private List<Channel> visible=new ArrayList<>();
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private Button button(String text, Runnable action){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(Color.WHITE);b.setMinWidth(0);b.setMinHeight(dp(44));
        b.setPadding(dp(9),0,dp(9),0);b.setBackgroundTintList(ColorStateList.valueOf(0xFF283752));
        b.setOnClickListener(v->action.run());return b;
    }
    private LinearLayout horizontal(){
        LinearLayout a=new LinearLayout(this);a.setOrientation(LinearLayout.HORIZONTAL);
        a.setGravity(Gravity.CENTER_VERTICAL);return a;
    }
    private void addScroll(LinearLayout parent, LinearLayout contents){
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);scroll.addView(contents);
        parent.addView(scroll,new LinearLayout.LayoutParams(-1,dp(50)));
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);catalog=Catalog.get();build();}
    @Override public void onResume(){super.onResume();if(items!=null)refresh();}
    private void save(){
        try{catalog.persist(this);}catch(Exception e){
            Toast.makeText(this,"Could not save Favorites. Check device storage.",Toast.LENGTH_LONG).show();
        }
    }
    private void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF111827);root.setPadding(dp(9),dp(5),dp(9),dp(5));
        setContentView(root);
        LinearLayout header=horizontal();
        header.addView(button("‹",this::finish),new LinearLayout.LayoutParams(dp(49),dp(48)));
        TextView heading=new TextView(this);heading.setText("★ Favorite Lists");
        heading.setTextSize(19);heading.setTextColor(Color.WHITE);heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));
        header.addView(button("⋮ Manage",this::menu),new LinearLayout.LayoutParams(-2,dp(48)));
        root.addView(header);
        search=new EditText(this);search.setHint("Find a favorite");search.setSingleLine(true);
        search.setTextColor(Color.WHITE);search.setHintTextColor(0xFF9EAEC9);search.setTextSize(15);
        search.setPadding(dp(12),0,dp(12),0);
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(49)));
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){refresh();}
            public void afterTextChanged(Editable e){}
        });
        folderBar=horizontal();addScroll(root,folderBar);
        filterBar=horizontal();addScroll(root,filterBar);
        items=new ListView(this);items.setDividerHeight(dp(4));items.setBackgroundColor(Color.TRANSPARENT);
        items.setOnItemClickListener((parent,view,position,id)->{
            if(position<visible.size())open(visible.get(position));
        });
        items.setOnItemLongClickListener((parent,view,position,id)->{
            if(position<visible.size())itemMenu(visible.get(position));return true;
        });
        root.addView(items,new LinearLayout.LayoutParams(-1,0,1));
        count=new TextView(this);count.setTextColor(0xFFC8D4E8);count.setTextSize(12);
        root.addView(count,new LinearLayout.LayoutParams(-1,dp(28)));
        rebuildSelectors();refresh();
    }
    private void rebuildSelectors(){
        folderBar.removeAllViews();
        addChoice(folderBar,"All Favorites",folder.isEmpty(),()->{folder="";refresh();rebuildSelectors();});
        for(String n:new ArrayList<>(catalog.favoriteFolders.keySet())){
            addChoice(folderBar,n,n.equals(folder),()->{folder=n;refresh();rebuildSelectors();});
        }
        addChoice(folderBar,"+ New List",false,this::newFolder);
        filterBar.removeAllViews();
        for(String k:new String[]{"All","Live","Movies","Series"}){
            addChoice(filterBar,k,k.equals(kind),()->{kind=k;refresh();rebuildSelectors();});
        }
    }
    private void addChoice(LinearLayout row,String name,boolean selected,Runnable action){
        Button b=button(name,action);
        if(selected)b.setBackgroundTintList(ColorStateList.valueOf(0xFF167DCA));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(43));
        lp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(b,lp);
    }
    private void refresh(){
        if(items==null||catalog==null)return;
        visible=catalog.favoriteEntries(folder,kind,search==null?"":search.getText().toString());
        items.setAdapter(new BaseAdapter(){
            public int getCount(){return visible.size();}
            public Object getItem(int i){return visible.get(i);}
            public long getItemId(int i){return i;}
            public View getView(int position,View old,ViewGroup parent){
                Channel c=visible.get(position);
                LinearLayout row=horizontal();row.setPadding(dp(6),dp(4),dp(6),dp(4));
                row.setBackgroundColor(position%2==0?0xFF202B40:0xFF172338);
                LinearLayout words=new LinearLayout(FavoritesActivity.this);words.setOrientation(LinearLayout.VERTICAL);
                TextView title=new TextView(FavoritesActivity.this);
                title.setText(c.name);title.setTextColor(Color.WHITE);title.setTextSize(16);
                title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                TextView subtitle=new TextView(FavoritesActivity.this);
                subtitle.setText(c.group+" • "+StreamKind.detect(c.kind,c.group,c.url));
                subtitle.setTextColor(0xFFA8C8E9);subtitle.setTextSize(12);
                subtitle.setSingleLine(true);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                words.addView(title);words.addView(subtitle);
                row.addView(words,new LinearLayout.LayoutParams(0,dp(54),1));
                row.addView(button("⋮",()->itemMenu(c)),new LinearLayout.LayoutParams(dp(52),dp(46)));
                row.setFocusable(true);row.setOnClickListener(v->open(c));
                row.setOnLongClickListener(v->{itemMenu(c);return true;});
                return row;
            }
        });
        count.setText(visible.size()+" shown • "+catalog.favorites.size()+" favorites saved");
    }
    private void open(Channel c){
        if(c.kind.equals("series")&&c.url.contains("get_series_info")){
            Toast.makeText(this,"Open a series from the main Series tab to select an episode.",Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this,PlayerActivity.class).putExtra("id",c.id));
    }
    private void itemMenu(Channel c){
        new AlertDialog.Builder(this).setTitle(c.name)
            .setItems(new String[]{"Play","Move up","Move down",
                    folder.isEmpty()?"Add to a named list":"Remove from this list",
                    "Remove from all favorites"},(dialog,which)->{
                if(which==0){open(c);return;}
                if(which==1||which==2){catalog.moveFavorite(c.id,folder,which==1?-1:1);}
                if(which==3){
                    if(folder.isEmpty()){assignFolder(c);return;}
                    catalog.unassignFavorite(folder,c.id);
                }
                if(which==4){new AlertDialog.Builder(this).setTitle("Remove favorite?")
                    .setMessage("Remove \""+c.name+"\" from all favorite lists?")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Remove",(d,w)->{catalog.removeFavorite(c.id);save();refresh();}).show();
                    return;
                }
                save();refresh();
            }).setNegativeButton("Cancel",null).show();
    }
    private void assignFolder(Channel c){
        List<String> folders=new ArrayList<>(catalog.favoriteFolders.keySet());
        if(folders.isEmpty()){new AlertDialog.Builder(this).setMessage("Create a named list first?")
            .setPositiveButton("Create", (d,w)->newFolder()).setNegativeButton("Cancel",null).show();return;}
        String[] names=folders.toArray(new String[0]);
        boolean[] checked=new boolean[folders.size()];
        for(int i=0;i<folders.size();i++)checked[i]=catalog.favoriteFolders.get(names[i]).contains(c.id);
        new AlertDialog.Builder(this).setTitle("Add to favorite lists")
            .setMultiChoiceItems(names,checked,(dialog,index,isChecked)->{
                if(isChecked)catalog.assignFavorite(names[index],c.id);
                else catalog.unassignFavorite(names[index],c.id);
                save();
            }).setPositiveButton("Done",(d,w)->refresh()).show();
    }
    private void menu(){
        new AlertDialog.Builder(this).setTitle("Manage Favorite Lists")
            .setItems(new String[]{"Create new list","Rename current list",
                    "Delete current list","Clear all favorites"},(d,w)->{
                if(w==0)newFolder();
                if(w==1)renameFolder();
                if(w==2)deleteFolder();
                if(w==3)new AlertDialog.Builder(this).setTitle("Clear all favorites?")
                    .setMessage("This removes all favorites and named list memberships. Your IPTV playlists will not be deleted.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Clear all",(a,b)->{catalog.clearAllFavorites();save();refresh();}).show();
            }).show();
    }
    private EditText nameField(String existing){
        EditText name=new EditText(this);name.setSingleLine(true);name.setText(existing);name.setSelectAllOnFocus(true);
        name.setHint("For example: Sports, Family, Movies");name.setTextSize(16);
        return name;
    }
    private void newFolder(){
        EditText field=nameField("");
        new AlertDialog.Builder(this).setTitle("Create favorite list").setView(field)
            .setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{
                String name=field.getText().toString().trim();
                if(!catalog.createFavoriteFolder(name)){Toast.makeText(this,"Invalid, duplicate or too many lists (maximum 20).",Toast.LENGTH_LONG).show();return;}
                folder=name;save();rebuildSelectors();refresh();
            }).show();
    }
    private void renameFolder(){
        if(folder.isEmpty()){Toast.makeText(this,"Select a named list first.",Toast.LENGTH_SHORT).show();return;}
        String old=folder;EditText field=nameField(old);
        new AlertDialog.Builder(this).setTitle("Rename favorite list").setView(field)
            .setNegativeButton("Cancel",null).setPositiveButton("Rename",(d,w)->{
                String next=field.getText().toString().trim();
                if(!catalog.renameFavoriteFolder(old,next)){Toast.makeText(this,"Invalid or duplicate list name.",Toast.LENGTH_LONG).show();return;}
                folder=next;save();rebuildSelectors();refresh();
            }).show();
    }
    private void deleteFolder(){
        if(folder.isEmpty()){Toast.makeText(this,"Select a named list first.",Toast.LENGTH_SHORT).show();return;}
        String selected=folder;
        new AlertDialog.Builder(this).setTitle("Delete \""+selected+"\"?")
            .setMessage("Only this custom list is removed. Items remain in All Favorites.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                catalog.favoriteFolders.remove(selected);folder="";save();rebuildSelectors();refresh();
            }).show();
    }
}
