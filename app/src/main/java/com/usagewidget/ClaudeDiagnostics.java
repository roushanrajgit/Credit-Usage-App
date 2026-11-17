package com.usagewidget;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.webkit.CookieManager;
import java.io.*;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.util.*;
/** Opt-in debug-only probe; no raw body, cookie or token logging. */
final class ClaudeDiagnostics {
 static boolean enabled(Context c){return (c.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
 static String redact(String s){
  Set<String> words=new HashSet<>(Arrays.asList("html","head","body","title","error","forbidden","access","denied","blocked","cloudflare","challenge","just","a","moment","please","enable","javascript","cookies","to","continue","unauthorized","application","json","text","charset","utf","server","nginx","true","false","null","mitigated"));
  StringBuilder out=new StringBuilder();
  for(String word:s.toLowerCase(Locale.US).split("[^a-z]+"))if(!word.isEmpty())out.append(words.contains(word)?word:"[redacted]").append(' ');
  return out.toString();
 }
 static void probe(Context c,String browserUa){
  if(!enabled(c))return;
  new Thread(()->{
   HttpsURLConnection con=null;
   try{
    con=(HttpsURLConnection)new URL("https://claude.ai/api/organizations").openConnection();
    con.setRequestMethod("GET");con.setInstanceFollowRedirects(false);con.setConnectTimeout(15000);con.setReadTimeout(15000);
    con.setRequestProperty("Accept","application/json");con.setRequestProperty("User-Agent",UsageClient.UA);
    String cookie=CookieManager.getInstance().getCookie("https://claude.ai");if(cookie!=null)con.setRequestProperty("Cookie",cookie);
    int status=con.getResponseCode();
    Log.d("ClaudeUsageDiagnostic","native status="+status+" cookiePresent="+(cookie!=null&&!cookie.isEmpty())+" sameUserAgent="+UsageClient.UA.equals(browserUa));
    for(String name:new String[]{"Server","Content-Type","CF-Mitigated","CF-Ray","Via","WWW-Authenticate"}){
     String value=con.getHeaderField(name);
     Log.d("ClaudeUsageDiagnostic",name+"="+(value==null?"absent":("CF-Ray".equals(name)||"WWW-Authenticate".equals(name)?"present":redact(value))));
    }
    if(status>=400){InputStream in=con.getErrorStream();if(in!=null)try(Reader reader=new InputStreamReader(in,"UTF-8")){char[] chars=new char[500];int total=0,n;while(total<500&&(n=reader.read(chars,total,500-total))>0)total+=n;Log.d("ClaudeUsageDiagnostic","bodyPrefixRedacted="+redact(new String(chars,0,total)));}}
   }catch(Exception e){Log.d("ClaudeUsageDiagnostic","Native probe failed (network/timeout); details omitted");}
   finally{if(con!=null)con.disconnect();}
  },"claude-diagnostic").start();
 }
}
