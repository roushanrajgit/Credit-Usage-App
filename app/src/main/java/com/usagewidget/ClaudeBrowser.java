package com.usagewidget;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.util.UUID;

/** Foreground sync using the currently displayed authenticated page (started automatically once it loads, or by tap). */
final class ClaudeBrowser {
 /** retry=true means an automatic attempt was interrupted by navigation and should be re-armed, not reported. */
 interface Completion { void done(boolean success, String message, boolean retry); }
 private final Activity activity;
 private final WebView web;
 private final Handler handler = new Handler();
 private boolean busy;
 private boolean cancelled;
 private boolean auto;
 private long deadline;
 private String key;
 private Completion completion;
 ClaudeBrowser(Activity a, WebView w) { activity=a;web=w; }
 static boolean trusted(String url) {
  if(url==null)return false;
  Uri u=Uri.parse(url);
  return "https".equals(u.getScheme()) && "claude.ai".equals(u.getHost()) && (u.getPort()==-1||u.getPort()==443) && u.getUserInfo()==null;
 }
 /** Automatic sync only runs on a trusted, signed-in looking page — never on sign-in or verification screens. */
 static boolean autoSyncAllowed(String url) {
  if(!trusted(url))return false;
  String path=Uri.parse(url).getPath();
  if(path==null)path="/";
  for(String p:new String[]{"/login","/logout","/magic-link","/oauth","/sso","/verify","/onboarding"})
   if(path.equals(p)||path.startsWith(p+"/")||path.startsWith(p+"?"))return false;
  return true;
 }
 void start(boolean automatic, Completion cb) {
  if(busy)return;
  if(!trusted(web.getUrl())) { cb.done(false,"Open Claude and finish signing in first.",false);return; }
  busy=true;cancelled=false;auto=automatic;completion=cb;deadline=android.os.SystemClock.elapsedRealtime()+45000;
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
  if(!trusted(web.getUrl())){if(auto){interrupted("");return;}finish(false,"Page changed · return to Claude and retry.");return;}
  if(android.os.SystemClock.elapsedRealtime()>deadline){finish(false,"Refresh timed out · keep Claude open and retry.");return;}
  web.evaluateJavascript("location.origin === 'https://claude.ai' ? JSON.stringify(window["+JSONObject.quote(key)+"] || null) : null",value->{
   if(cancelled||!busy)return;
   try {
    Object outer=new JSONTokener(value==null?"null":value).nextValue();
    if(!(outer instanceof String)||"null".equals(outer)){handler.postDelayed(this,250);return;}
    if(((String)outer).length()>524288)throw new IOException();
    JSONObject result=new JSONObject((String)outer);
    // Signed out: an automatic attempt waits for sign-in instead of marking saved readings stale.
    if(auto&&"HTTP 401".equals(result.optString("error"))){interrupted("Sign in to Claude · usage syncs automatically afterwards.");return;}
    if(result.has("error")){finish(false,result.optString("error","Claude refresh unavailable"));return;}
    JSONArray rows=result.getJSONArray("rows");
    ClaudeSnapshots.save(activity,rows);
    finish(true,rows.length()==0?"No Claude chat workspaces found for this login.":"Claude usage updated.");
   }catch(Exception e){finish(false,"Unexpected Claude response · saved usage retained.");}
  });
 }};
 void navigation(){if(!busy)return;if(auto)interrupted("");else finish(false,"Page changed during refresh · retry after it loads.");}
 /** Automatic attempt cut short (redirect, route change, signed out): nothing is recorded; the next page load re-arms it. */
 private void interrupted(String message){
  busy=false;handler.removeCallbacksAndMessages(null);
  Completion cb=completion;completion=null;if(cb!=null)cb.done(false,message,true);
 }
 void cancel(){cancelled=true;busy=false;handler.removeCallbacksAndMessages(null);completion=null;}
 private void finish(boolean ok,String message){
  busy=false;handler.removeCallbacksAndMessages(null);
  if(!ok)ClaudeSnapshots.fail(activity,message);
  if(trusted(web.getUrl()))web.evaluateJavascript("delete window["+JSONObject.quote(key==null?"":key)+"]",null);
  Completion cb=completion;completion=null;if(cb!=null)cb.done(ok,ok?message:"Stale · "+message,false);
 }
}
