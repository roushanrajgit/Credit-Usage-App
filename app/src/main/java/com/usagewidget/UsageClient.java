package com.usagewidget;
import android.content.*;
import android.webkit.CookieManager;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;
import java.util.*;
final class UsageClient {
 static final String UA="Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
 static Object get(String host,String path,String bearer,String account)throws Exception {
  URL url=new URL("https://"+host+path);
  HttpsURLConnection con=(HttpsURLConnection)url.openConnection();
  con.setRequestMethod("GET");con.setInstanceFollowRedirects(false);con.setConnectTimeout(15000);con.setReadTimeout(15000);
  con.setRequestProperty("Accept","application/json");con.setRequestProperty("User-Agent",UA);
  String cookie=CookieManager.getInstance().getCookie("https://"+host);
  if(cookie!=null)con.setRequestProperty("Cookie",cookie);
  if(bearer!=null)con.setRequestProperty("Authorization","Bearer "+bearer);
  if(account!=null)con.setRequestProperty("ChatGPT-Account-Id",account);
  try {
   int code=con.getResponseCode();if(code!=200)throw new IOException(code==401?"Sign in again":code==403?"Access blocked · open connection":code==429?"Too many requests · try later":"Usage unavailable (HTTP "+code+")");
   try(InputStream in=con.getInputStream()){
    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
    while((n=in.read(buf))!=-1){if(out.size()+n>2097152)throw new IOException("Unexpected server response");out.write(buf,0,n);}
    return new JSONTokener(out.toString("UTF-8")).nextValue();
   }
  }finally{con.disconnect();}
 }
 static JSONArray codex()throws Exception {
  JSONObject session=(JSONObject)get("chatgpt.com","/api/auth/session",null,null);
  String token=session.optString("accessToken","");if(token.isEmpty())throw new IOException("Sign in again");
  JSONObject discovered=(JSONObject)get("chatgpt.com","/backend-api/accounts/check/v4-2023-04-27",token,null);
  JSONObject accounts=discovered.optJSONObject("accounts");if(accounts==null)throw new IOException("Workspace discovery unavailable");
  JSONArray out=new JSONArray();
  for(JSONObject a:UsageFormat.codexAccounts(accounts)){
   String id=UsageFormat.str(a,"account_id");
   JSONObject row=new JSONObject().put("id","codex:"+id).put("provider","codex").put("name","Codex · "+UsageFormat.codexName(a)).put("plan",UsageFormat.str(a,"plan_type"));
   try {JSONObject u=(JSONObject)get("chatgpt.com","/backend-api/wham/usage",token,id);
    JSONObject limits=u.optJSONObject("rate_limit");
    row.put("session",limits==null?null:limits.optJSONObject("primary_window")).put("weekly",limits==null?null:limits.optJSONObject("secondary_window")).put("updated",System.currentTimeMillis());
    if(!u.isNull("plan_type"))row.put("plan",u.optString("plan_type"));
    JSONObject credits=u.optJSONObject("credits");if(credits!=null){
     if(credits.optBoolean("unlimited"))row.put("extra","Credits: unlimited");
     else if(!credits.isNull("balance"))row.put("extra","Credits remaining: "+credits.optString("balance"));
    }
   }catch(Exception e){row.put("error",e.getMessage());}
   out.put(row);
  }
  return out;
 }
 static void refresh(Context c){
  if(!Store.prefs(c).getBoolean("codexConnected",false)){UsageWidget.update(c);return;}
  JSONArray fresh=null;String failure=null;
  try {fresh=codex();}catch(Exception e){failure=e.getMessage()==null?"Connection unavailable":e.getMessage();}
  synchronized(UsageClient.class){
   // Disconnect may have run while a network request was in progress.
   if(!Store.prefs(c).getBoolean("codexConnected",false))return;
   JSONArray old=Store.rows(c),all=new JSONArray();
   for(int i=0;i<old.length();i++){JSONObject r=old.optJSONObject(i);if(r!=null&&!"codex".equals(r.optString("provider")))all.put(r);}
   if(fresh!=null){
    for(int i=0;i<fresh.length();i++)try{
     JSONObject r=fresh.getJSONObject(i);
     if(r.has("error"))for(int j=0;j<old.length();j++){
      JSONObject prev=old.getJSONObject(j);if(prev.optString("id").equals(r.optString("id"))){r=new JSONObject(prev.toString()).put("error",r.optString("error"));break;}
     }
     all.put(r);
    }catch(JSONException ignored){}
   }else{
    for(int i=0;i<old.length();i++)try{JSONObject r=old.getJSONObject(i);if("codex".equals(r.optString("provider")))all.put(new JSONObject(r.toString()).put("error","Stale · reconnect or refresh"));}catch(JSONException ignored){}
   }
   Store.save(c,all);Store.prefs(c).edit().putString("status",failure!=null?"codex: "+failure:fresh.length()==0?"codex: no accessible workspaces":"Usage snapshot · local reset times").apply();UsageWidget.update(c);
  }
 }
}
