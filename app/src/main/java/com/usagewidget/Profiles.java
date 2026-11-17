package com.usagewidget;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.webkit.*;
import java.io.File;

/**
 * Login slots. Slot 1 of each provider uses the main process and the app's original WebView storage, so logins
 * made before multi-account support keep working. Slots 2-3 run in their own process (":c2", ":x3", ...) whose
 * WebView data directory is separated with WebView.setDataDirectorySuffix (Android 9+), so each login has its
 * own private cookies and storage. Older Android versions get one login per provider.
 */
final class Profiles {
 static final String CLAUDE="claude",CODEX="codex";
 static final int MAX=3;
 static boolean multiSupported(){return Build.VERSION.SDK_INT>=28;}
 static int maxSlots(){return multiSupported()?MAX:1;}
 static String host(String provider){return CLAUDE.equals(provider)?"claude.ai":"chatgpt.com";}
 static String title(String provider){return CLAUDE.equals(provider)?"Claude":"ChatGPT";}

 /** Process / data-directory suffix for a slot, or null for slot 1 (main process). */
 static String suffix(String provider,int slot){return slot<=1?null:(CLAUDE.equals(provider)?"c":"x")+slot;}

 static String processName(){return Build.VERSION.SDK_INT>=28?Application.getProcessName():"";}
 /** Slot served by the current process: 1 for the main process, N for ":cN" / ":xN". */
 static int currentSlot(){
  String p=processName();int i=p.indexOf(':');
  if(i<0||p.length()<i+3)return 1;
  try{int n=Integer.parseInt(p.substring(i+2));return n>=2&&n<=MAX?n:1;}catch(NumberFormatException e){return 1;}
 }

 static Class<?> loginActivity(String provider,int slot){
  if(slot<=1)return LoginActivity.class;
  if(CLAUDE.equals(provider))return slot==2?Slots.ClaudeLogin2.class:Slots.ClaudeLogin3.class;
  return slot==2?Slots.CodexLogin2.class:Slots.CodexLogin3.class;
 }
 static Class<?> codexJob(int slot){return slot<=1?RefreshJob.class:slot==2?Slots.CodexJob2.class:Slots.CodexJob3.class;}

 /** Intent that opens the sign-in / sync screen of one login. */
 static Intent login(Context c,String provider,int slot,String label){
  return new Intent(c,loginActivity(provider,slot)).putExtra("provider",provider).putExtra("slot",slot).putExtra("label",label);
 }

 /**
  * Sign out one provider from slot 1's shared storage (main process). If the other provider has no slot-1 login,
  * the whole store is wiped; otherwise only this provider's sites (incl. its sign-in domain) are expired.
  */
 static void clearSite(Context c,String provider){
  CookieManager cm=CookieManager.getInstance();
  String other=CLAUDE.equals(provider)?CODEX:CLAUDE;
  // Nothing else lives in slot 1's store: wipe it completely (also clears sign-in providers' cookies).
  if(!Store.connected(c,other,1)){cm.removeAllCookies(null);cm.flush();WebStorage.getInstance().deleteAllData();return;}
  // Otherwise expire only this provider's sites, including its sign-in domain, keeping the other login.
  String[] hosts=CLAUDE.equals(provider)?new String[]{"claude.ai"}:new String[]{"chatgpt.com","auth.openai.com","openai.com"};
  for(String host:hosts){
   String url="https://"+host;String all=cm.getCookie(url);
   if(all!=null)for(String part:all.split(";")){
    int eq=part.indexOf('=');String name=(eq<0?part:part.substring(0,eq)).trim();if(name.isEmpty())continue;
    String expire=name+"=; Max-Age=0; Path=/; Secure";
    cm.setCookie(url,expire);
    if(!name.startsWith("__Host-")){cm.setCookie(url,expire+"; Domain="+host);cm.setCookie(url,expire+"; Domain=."+host);}
   }
   WebStorage.getInstance().deleteOrigin(url);
  }
  cm.flush();
 }

 /** Delete a slot's whole private storage: stop its process, then remove its WebView directories. */
 static void wipe(Context c,String provider,int slot){
  String suffix=suffix(provider,slot);if(suffix==null)return;
  ActivityManager am=c.getSystemService(ActivityManager.class);
  if(am!=null&&am.getRunningAppProcesses()!=null)
   for(ActivityManager.RunningAppProcessInfo pi:am.getRunningAppProcesses())
    if((c.getPackageName()+":"+suffix).equals(pi.processName))android.os.Process.killProcess(pi.pid);
  // killProcess is asynchronous: wait briefly (max ~0.5 s) so the next sign-in can't reuse the dying process.
  for(int i=0;i<10&&running(c,suffix);i++)try{Thread.sleep(50);}catch(InterruptedException e){break;}
  for(File dir:new File[]{new File(c.getApplicationInfo().dataDir),c.getCacheDir()}){
   File[] files=dir.listFiles();if(files==null)continue;
   for(File f:files){String n=f.getName().toLowerCase(java.util.Locale.US);if(n.contains("webview")&&n.endsWith("_"+suffix))delete(f);}
  }
 }
 private static boolean running(Context c,String suffix){
  ActivityManager am=c.getSystemService(ActivityManager.class);if(am==null||am.getRunningAppProcesses()==null)return false;
  for(ActivityManager.RunningAppProcessInfo pi:am.getRunningAppProcesses())if((c.getPackageName()+":"+suffix).equals(pi.processName))return true;
  return false;
 }
 private static void delete(File f){File[] kids=f.listFiles();if(kids!=null)for(File k:kids)delete(k);f.delete();}
}
