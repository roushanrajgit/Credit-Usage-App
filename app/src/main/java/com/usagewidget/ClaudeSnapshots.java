package com.usagewidget;
import android.content.Context;
import org.json.*;

/** Claude readings per login slot. Runs in the main process (via SyncProvider). */
final class ClaudeSnapshots {
 static boolean mine(JSONObject r,int slot){return "claude".equals(r.optString("provider"))&&Store.rowSlot(r)==slot;}
 /** Replace this login's Claude rows; other logins and Codex rows are untouched. */
 static void save(Context c,int slot,JSONArray fresh,String email)throws JSONException {
  synchronized(UsageClient.class){
   JSONArray old=Store.rows(c),all=new JSONArray();
   for(int i=0;i<old.length();i++){JSONObject r=old.getJSONObject(i);if(!mine(r,slot))all.put(r);}
   for(int i=0;i<fresh.length();i++){
    JSONObject r=fresh.getJSONObject(i);
    if(r.has("error")){
     String err=r.optString("error");
     r.put("error","Unavailable · "+err);
     for(int j=0;j<old.length();j++){
      JSONObject previous=old.getJSONObject(j);
      // Keep the last successful reading of this login; label it stale only if there was one.
      if(mine(previous,slot)&&previous.optString("id").equals(r.optString("id"))){r=new JSONObject(previous.toString()).put("error",(previous.optLong("updated",0)>0?"Stale · ":"Unavailable · ")+err);break;}
     }
    }
    all.put(r.put("slot",slot));
   }
   Store.save(c,all);
   android.content.SharedPreferences.Editor e=Store.prefs(c).edit()
    .putBoolean(Store.key("claude","Connected",slot),true)
    .putString(Store.key("claude","Status",slot),fresh.length()==0?"No chat workspaces for this login":"Synced");
   if(email!=null&&!email.isEmpty())e.putString(Store.key("claude","Email",slot),email);
   e.apply();
   UsageWidget.update(c);
  }
 }
 static void fail(Context c,int slot,String message){
  synchronized(UsageClient.class){
   JSONArray all=Store.rows(c);
   for(int i=0;i<all.length();i++)try{JSONObject r=all.getJSONObject(i);if(mine(r,slot))r.put("error",(r.optLong("updated",0)>0?"Stale · ":"Unavailable · ")+message);}catch(JSONException ignored){}
   Store.save(c,all);Store.prefs(c).edit().putString(Store.key("claude","Status",slot),message).apply();UsageWidget.update(c);
  }
 }
}
