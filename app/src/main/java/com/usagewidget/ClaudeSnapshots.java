package com.usagewidget;
import android.content.Context;
import org.json.*;

final class ClaudeSnapshots {
 static void save(Context c,JSONArray fresh)throws JSONException {
  synchronized(UsageClient.class){
   JSONArray old=Store.rows(c),all=new JSONArray();
   for(int i=0;i<old.length();i++){JSONObject r=old.getJSONObject(i);if(!"claude".equals(r.optString("provider")))all.put(r);}
   for(int i=0;i<fresh.length();i++){
    JSONObject r=fresh.getJSONObject(i);
    if(r.has("error")){
     String err=r.optString("error");
     r.put("error","Unavailable · "+err);
     for(int j=0;j<old.length();j++){
      JSONObject previous=old.getJSONObject(j);
      // Keep the last successful reading; label it stale only if there was one.
      if(previous.optString("id").equals(r.optString("id"))){r=new JSONObject(previous.toString()).put("error",(previous.optLong("updated",0)>0?"Stale · ":"Unavailable · ")+err);break;}
     }
    }
    all.put(r);
   }
   Store.save(c,all);
   if(!Store.prefs(c).getString("status","").startsWith("codex:"))Store.prefs(c).edit().putString("status","Usage snapshot · local reset times").apply();
   Store.prefs(c).edit().putBoolean("claudeConnected",true).putString("claudeStatus",fresh.length()==0?"Claude: no chat workspaces for this login":"Claude: synced when the Claude screen opens · no background refresh").apply();
   UsageWidget.update(c);
  }
 }
 static void fail(Context c,String message){
  synchronized(UsageClient.class){
   JSONArray all=Store.rows(c);
   for(int i=0;i<all.length();i++)try{JSONObject r=all.getJSONObject(i);if("claude".equals(r.optString("provider")))r.put("error",(r.optLong("updated",0)>0?"Stale · ":"Unavailable · ")+message);}catch(JSONException ignored){}
   Store.save(c,all);Store.prefs(c).edit().putString("claudeStatus","Claude: "+message).apply();UsageWidget.update(c);
  }
 }
}
