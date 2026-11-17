package com.usagewidget;
import android.app.*;
import android.os.*;
import android.content.*;
import android.webkit.*;
import android.widget.*;
import android.net.Uri;
public class LoginActivity extends Activity {
 static final String CLAUDE_USAGE_URL="https://claude.ai/settings/usage";
 static final long AUTO_SYNC_DELAY_MS=1500;
 WebView web;ClaudeBrowser claudeBrowser;Button done;TextView info;
 final Handler handler=new Handler();
 boolean autoSyncUsed;
 final Runnable autoSync=new Runnable(){public void run(){
  if(claudeBrowser==null||autoSyncUsed||isFinishing()||!ClaudeBrowser.autoSyncAllowed(web.getUrl()))return;
  autoSyncUsed=true;startClaudeSync(true);
 }};
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,android.view.WindowManager.LayoutParams.FLAG_SECURE);
  String p=getIntent().getStringExtra("provider");if(!"claude".equals(p)&&!"codex".equals(p)){finish();return;}
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(0,30,0,20);setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
  info=new TextView(this);info.setText("Sign in on the provider page below. Then tap Done. If Google sign-in is blocked, use a provider-supported email method. No password is collected by Usage Widget.");info.setPadding(16,12,16,12);root.addView(info);
  TextView address=new TextView(this);address.setPadding(16,4,16,4);root.addView(address);
  done=new Button(this);done.setText("Done — discover subscriptions");root.addView(done);
  web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);if(!"claude".equals(p))web.getSettings().setUserAgentString(UsageClient.UA);
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
  if("claude".equals(p)){claudeBrowser=new ClaudeBrowser(this,web);done.setText("Refresh Claude usage now");info.setText("Usage syncs automatically once Claude has loaded. If you are signed out, sign in first. Keep this screen open until it closes. Background updates apply to Codex only.");}
  if("claude".equals(p)&&ClaudeDiagnostics.enabled(this)){
   Button diagnostic=new Button(this);diagnostic.setText("Debug: probe original native request");root.addView(diagnostic,3);
   diagnostic.setOnClickListener(v->{ClaudeDiagnostics.probe(this,web.getSettings().getUserAgentString());Toast.makeText(this,"Redacted diagnostic in local logcat: ClaudeUsageDiagnostic",Toast.LENGTH_LONG).show();});
  }
  web.setWebViewClient(new WebViewClient(){
   public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){handler.removeCallbacks(autoSync);if(claudeBrowser!=null)claudeBrowser.navigation();}
   public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return !"https".equals(r.getUrl().getScheme());}
   public void onPageFinished(WebView v,String url){address.setText(Uri.parse(url).getHost());CookieManager.getInstance().flush();scheduleAutoSync();}
   // Also fires for single-page-app route changes (e.g. after sign-in), which onPageFinished misses.
   public void doUpdateVisitedHistory(WebView v,String url,boolean reload){scheduleAutoSync();}
  });
  done.setOnClickListener(v->{if(claudeBrowser!=null){handler.removeCallbacks(autoSync);autoSyncUsed=true;startClaudeSync(false);return;}CookieManager.getInstance().flush();Store.prefs(this).edit().putBoolean(p+"Connected",true).putString("status","Discovering subscriptions…").apply();RefreshJob.now(this);finish();});
  web.loadUrl(p.equals("claude")?CLAUDE_USAGE_URL:"https://chatgpt.com/auth/login");
 }
 void scheduleAutoSync(){if(claudeBrowser==null||autoSyncUsed)return;handler.removeCallbacks(autoSync);handler.postDelayed(autoSync,AUTO_SYNC_DELAY_MS);}
 void startClaudeSync(boolean auto){
  done.setEnabled(false);done.setText("Reading Claude usage…");
  claudeBrowser.start(auto,(ok,message,retry)->{
   done.setEnabled(true);done.setText("Refresh Claude usage now");
   // An automatic attempt interrupted by a redirect or sign-out is not recorded as a failure;
   // it re-arms and runs again on the next page load/route change (no immediate retry loop).
   if(retry){autoSyncUsed=false;if(!message.isEmpty())info.setText(message);return;}
   info.setText(message);if(ok)finish();
  });
 }
 public void onDestroy(){handler.removeCallbacks(autoSync);if(claudeBrowser!=null)claudeBrowser.cancel();if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
