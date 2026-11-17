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
    if(r.has("error"))for(int j=0;j<old.length();j++){
     JSONObject previous=old.getJSONObject(j);
     if(previous.optString("id").equals(r.optString("id"))){r=new JSONObject(previous.toString()).put("error",r.optString("error"));break;}
    }
    all.put(r);
   }
   Store.save(c,all);
   if(!Store.prefs(c).getString("status","").startsWith("codex:"))Store.prefs(c).edit().putString("status","Usage snapshot · local reset times").apply();
   Store.prefs(c).edit().putBoolean("claudeConnected",true).putString("claudeStatus",fresh.length()==0?"Claude: no accessible workspaces":"Claude: foreground browser sync · automatic refresh unavailable").apply();
   UsageWidget.update(c);
  }
 }
 static void fail(Context c,String message){
  synchronized(UsageClient.class){
   JSONArray all=Store.rows(c);
   for(int i=0;i<all.length();i++)try{JSONObject r=all.getJSONObject(i);if("claude".equals(r.optString("provider")))r.put("error",message);}catch(JSONException ignored){}
   Store.save(c,all);Store.prefs(c).edit().putString("claudeStatus","Claude: "+message).apply();UsageWidget.update(c);
  }
 }
}
