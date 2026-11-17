package com.usagewidget;
import android.app.*;
import android.os.*;
import android.content.*;
import android.webkit.*;
import android.widget.*;
import android.net.Uri;

/**
 * Sign-in and Claude sync screen for one login. Slot 1 runs in the main process; slots 2-3 run as the
 * Slots.* subclasses in their own process, each with private WebView storage (see App and Profiles).
 */
public class LoginActivity extends Activity {
 static final String CLAUDE_USAGE_URL="https://claude.ai/settings/usage";
 static final long AUTO_SYNC_DELAY_MS=1500;
 WebView web;ClaudeBrowser claudeBrowser;Button done;TextView info;
 final Handler handler=new Handler();
 boolean autoSyncUsed;
 String provider;int slot;int[] chain;
 final Runnable autoSync=new Runnable(){public void run(){
  if(claudeBrowser==null||autoSyncUsed||isFinishing()||!ClaudeBrowser.autoSyncAllowed(web.getUrl()))return;
  autoSyncUsed=true;startClaudeSync(true);
 }};
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,android.view.WindowManager.LayoutParams.FLAG_SECURE);
  provider=getIntent().getStringExtra("provider");if(!Profiles.CLAUDE.equals(provider)&&!Profiles.CODEX.equals(provider)){finish();return;}
  slot=Profiles.currentSlot();chain=getIntent().getIntArrayExtra("chain");if(chain==null)chain=new int[0];
  String label=getIntent().getStringExtra("label");
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(0,30,0,20);setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
  TextView who=new TextView(this);who.setText(Profiles.title(provider)+" · "+(label==null||label.isEmpty()?"new login":label));who.setTextSize(16);who.setPadding(16,12,16,0);root.addView(who);
  info=new TextView(this);info.setText("Sign in on the provider page below. Then tap Done. If Google sign-in is blocked, use a provider-supported email method. No password is collected by Usage Widget.");info.setPadding(16,8,16,12);root.addView(info);
  TextView address=new TextView(this);address.setPadding(16,4,16,4);root.addView(address);
  done=new Button(this);done.setText("Done — discover subscriptions");root.addView(done);
  web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);if(!Profiles.CLAUDE.equals(provider))web.getSettings().setUserAgentString(UsageClient.UA);
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
  if(Profiles.CLAUDE.equals(provider)){claudeBrowser=new ClaudeBrowser(this,web,slot);done.setText("Refresh Claude usage now");info.setText("Usage syncs automatically once Claude has loaded. If you are signed out, sign in first. Keep this screen open until it closes."+(chain.length>0?" Your other Claude logins sync next; press Back to skip this one.":""));}
  if(Profiles.CLAUDE.equals(provider)&&ClaudeDiagnostics.enabled(this)){
   Button diagnostic=new Button(this);diagnostic.setText("Debug: probe original native request");root.addView(diagnostic,4);
   diagnostic.setOnClickListener(v->{ClaudeDiagnostics.probe(this,web.getSettings().getUserAgentString());Toast.makeText(this,"Redacted diagnostic in local logcat: ClaudeUsageDiagnostic",Toast.LENGTH_LONG).show();});
  }
  web.setWebViewClient(new WebViewClient(){
   public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){handler.removeCallbacks(autoSync);if(claudeBrowser!=null)claudeBrowser.navigation();}
   public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return !"https".equals(r.getUrl().getScheme());}
   public void onPageFinished(WebView v,String url){address.setText(Uri.parse(url).getHost());CookieManager.getInstance().flush();scheduleAutoSync();}
   // Also fires for single-page-app route changes (e.g. after sign-in), which onPageFinished misses.
   public void doUpdateVisitedHistory(WebView v,String url,boolean reload){CookieManager.getInstance().flush();scheduleAutoSync();}
  });
  done.setOnClickListener(v->{if(claudeBrowser!=null){handler.removeCallbacks(autoSync);autoSyncUsed=true;startClaudeSync(false);return;}
   CookieManager.getInstance().flush();
   SyncProvider.call(this,"connect",SyncProvider.args(provider,slot));
   RefreshJob.now(this,slot);finish();});
  web.loadUrl(Profiles.CLAUDE.equals(provider)?CLAUDE_USAGE_URL:"https://chatgpt.com/auth/login");
 }
 void scheduleAutoSync(){if(claudeBrowser==null||autoSyncUsed)return;handler.removeCallbacks(autoSync);handler.postDelayed(autoSync,AUTO_SYNC_DELAY_MS);}
 void startClaudeSync(boolean auto){
  done.setEnabled(false);done.setText("Reading Claude usage…");
  claudeBrowser.start(auto,(ok,message,retry)->{
   done.setEnabled(true);done.setText("Refresh Claude usage now");
   // An automatic attempt interrupted by a redirect or sign-out is not recorded as a failure;
   // it re-arms and runs again on the next page load/route change (no immediate retry loop).
   if(retry){autoSyncUsed=false;if(!message.isEmpty())info.setText(message);return;}
   info.setText(message);
   CookieManager.getInstance().flush();
   if(ok){next();finish();}
   // A failed login doesn't block the others: move on after the message has been visible briefly.
   else if(chain.length>0)handler.postDelayed(()->{if(!isFinishing()){next();finish();}},2500);
  });
 }
 /** Open the next Claude login in the refresh chain, if any. */
 void next(){
  if(chain.length==0)return;
  int[] rest=new int[chain.length-1];System.arraycopy(chain,1,rest,0,rest.length);
  startActivity(new Intent(this,Profiles.loginActivity(Profiles.CLAUDE,chain[0])).putExtra("provider",Profiles.CLAUDE).putExtra("chain",rest).putExtra("label",getIntent().getStringExtra("label"+chain[0])).putExtras(labelsOf(getIntent())));
  chain=new int[0];
 }
 static Bundle labelsOf(Intent i){Bundle b=new Bundle();for(int s=1;s<=Profiles.MAX;s++){String l=i.getStringExtra("label"+s);if(l!=null)b.putString("label"+s,l);}return b;}
 // Leaving with Back (e.g. a login that needs signing in again) still hands over to the next Claude login.
 @Override public void onBackPressed(){next();super.onBackPressed();}
 // Save the login to disk whenever the screen is left, so Android closing the app can't lose a fresh sign-in.
 public void onPause(){CookieManager.getInstance().flush();super.onPause();}
 public void onDestroy(){handler.removeCallbacks(autoSync);if(claudeBrowser!=null)claudeBrowser.cancel();if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
