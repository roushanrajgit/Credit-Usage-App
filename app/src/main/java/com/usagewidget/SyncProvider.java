package com.usagewidget;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;

/**
 * Main-process mailbox for login processes. Not exported: only this app's own processes can call it.
 * Carries usage rows and an optional account email; never cookies or tokens.
 */
public class SyncProvider extends ContentProvider {
 static final Uri URI=Uri.parse("content://com.usagewidget.sync");

 /** Call the main process from any of this app's processes. */
 static Bundle call(Context c,String method,Bundle extras){return c.getContentResolver().call(URI,method,null,extras);}
 static Bundle args(String provider,int slot){Bundle b=new Bundle();b.putString("provider",provider);b.putInt("slot",slot);return b;}

 @Override public Bundle call(String method,String arg,Bundle extras){
  Context c=getContext();if(c==null||extras==null)return null;
  String provider=extras.getString("provider","");int slot=extras.getInt("slot",1);
  if(!Profiles.CLAUDE.equals(provider)&&!Profiles.CODEX.equals(provider))return null;
  if(slot<1||slot>Profiles.MAX)return null;
  try{
   switch(method){
    case "connected":{Bundle out=new Bundle();out.putBoolean("connected",Store.connected(c,provider,slot));return out;}
    case "connect":
     Store.prefs(c).edit().putBoolean(Store.key(provider,"Connected",slot),true).putString(Store.key(provider,"Status",slot),"Discovering subscriptions…").apply();
     RefreshJob.schedule(c);UsageWidget.update(c);return null;
    case "claudeSave":ClaudeSnapshots.save(c,slot,new JSONArray(extras.getString("rows","[]")),extras.getString("email",""));return null;
    case "claudeFail":
     // A manual refresh that fails still lists the login (with its error), so it can be retried or removed.
     if(extras.getBoolean("connect",false))Store.prefs(c).edit().putBoolean(Store.key(provider,"Connected",slot),true).apply();
     ClaudeSnapshots.fail(c,slot,extras.getString("message","Refresh failed"));return null;
    case "codexResult":{
     String rows=extras.getString("rows");
     UsageClient.merge(c,slot,rows==null?null:new JSONArray(rows),extras.getString("failure"),extras.getString("email",""));return null;}
    default:return null;
   }
  }catch(JSONException e){return null;}
 }
 @Override public boolean onCreate(){return true;}
 @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
 @Override public String getType(Uri u){return null;}
 @Override public Uri insert(Uri u,ContentValues v){return null;}
 @Override public int delete(Uri u,String s,String[] a){return 0;}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
}
