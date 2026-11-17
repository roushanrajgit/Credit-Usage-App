package com.usagewidget;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.util.UUID;

/** Explicit foreground sync using the currently displayed authenticated page. */
final class ClaudeBrowser {
 interface Completion { void done(boolean success, String message); }
 private final Activity activity;
 private final WebView web;
 private final Handler handler = new Handler();
 private boolean busy;
 private boolean cancelled;
 private long deadline;
 private String key;
 private Completion completion;
 ClaudeBrowser(Activity a, WebView w) { activity=a;web=w; }
 static boolean trusted(String url) {
  if(url==null)return false;
  Uri u=Uri.parse(url);
  return "https".equals(u.getScheme()) && "claude.ai".equals(u.getHost()) && (u.getPort()==-1||u.getPort()==443) && u.getUserInfo()==null;
 }
 void start(Completion cb) {
  if(busy)return;
  if(!trusted(web.getUrl())) { cb.done(false,"Open Claude and finish signing in first.");return; }
  busy=true;cancelled=false;completion=cb;deadline=android.os.SystemClock.elapsedRealtime()+45000;
  key="__usageWidget_"+UUID.randomUUID().toString().replace("-","");
  try {
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   try(InputStream in=activity.getAssets().open("claude-usage.js")) {byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
   String js=bytes.toString("UTF-8").replace("__RESULT_KEY__",JSONObject.quote(key));
   web.evaluateJavascript(js,ignored->{if(!cancelled)handler.postDelayed(poll,250);});
  } catch(Exception e) {finish(false,"Unable to start Claude browser refresh.");}
 }
 private final Runnable poll=new Runnable(){public void run(){
  if(cancelled||!busy)return;
  if(!trusted(web.getUrl())){finish(false,"Page changed · return to Claude and retry.");return;}
  if(android.os.SystemClock.elapsedRealtime()>deadline){finish(false,"Refresh timed out · keep Claude open and retry.");return;}
  web.evaluateJavascript("location.origin === 'https://claude.ai' ? JSON.stringify(window["+JSONObject.quote(key)+"] || null) : null",value->{
   if(cancelled||!busy)return;
   try {
    Object outer=new JSONTokener(value==null?"null":value).nextValue();
    if(!(outer instanceof String)||"null".equals(outer)){handler.postDelayed(this,250);return;}
    if(((String)outer).length()>524288)throw new IOException();
    JSONObject result=new JSONObject((String)outer);
    if(result.has("error")){finish(false,result.optString("error","Claude refresh unavailable"));return;}
    JSONArray rows=result.getJSONArray("rows");
    ClaudeSnapshots.save(activity,rows);
    finish(true,rows.length()==0?"No accessible Claude workspaces.":"Claude browser refresh completed. Check each workspace for unavailable data.");
   }catch(Exception e){finish(false,"Unexpected Claude response · saved usage retained.");}
  });
 }};
 void navigation(){if(busy)finish(false,"Page changed during refresh · retry after it loads.");}
 void cancel(){cancelled=true;busy=false;handler.removeCallbacksAndMessages(null);completion=null;}
 private void finish(boolean ok,String message){
  busy=false;handler.removeCallbacksAndMessages(null);
  if(!ok)ClaudeSnapshots.fail(activity,"Stale · "+message);
  if(trusted(web.getUrl()))web.evaluateJavascript("delete window["+JSONObject.quote(key==null?"":key)+"]",null);
  Completion cb=completion;completion=null;if(cb!=null)cb.done(ok,message);
 }
}
