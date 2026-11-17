package com.usagewidget;
import android.app.*;
import android.os.*;
import android.content.*;
import android.webkit.*;
import android.widget.*;
import android.net.Uri;
public class LoginActivity extends Activity {
 WebView web;ClaudeBrowser claudeBrowser;
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,android.view.WindowManager.LayoutParams.FLAG_SECURE);
  String p=getIntent().getStringExtra("provider");if(!"claude".equals(p)&&!"codex".equals(p)){finish();return;}
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(0,30,0,20);setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
  TextView info=new TextView(this);info.setText("Sign in on the provider page below. Then tap Done. If Google sign-in is blocked, use a provider-supported email method. No password is collected by Usage Widget.");info.setPadding(16,12,16,12);root.addView(info);
  TextView address=new TextView(this);address.setPadding(16,4,16,4);root.addView(address);
  Button done=new Button(this);done.setText("Done — discover subscriptions");root.addView(done);
  web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);if(!"claude".equals(p))web.getSettings().setUserAgentString(UsageClient.UA);
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
  if("claude".equals(p)){claudeBrowser=new ClaudeBrowser(this,web);done.setText("Refresh Claude usage from this page");info.setText("Sign in, then open Settings → Usage. Tap Refresh Claude usage to sync the widget. Keep this screen open until it finishes. Claude refreshes here manually; background updates apply to Codex only.");}
  if("claude".equals(p)&&ClaudeDiagnostics.enabled(this)){
   Button diagnostic=new Button(this);diagnostic.setText("Debug: probe original native request");root.addView(diagnostic,3);
   diagnostic.setOnClickListener(v->{ClaudeDiagnostics.probe(this,web.getSettings().getUserAgentString());Toast.makeText(this,"Redacted diagnostic in local logcat: ClaudeUsageDiagnostic",Toast.LENGTH_LONG).show();});
  }
  web.setWebViewClient(new WebViewClient(){
   public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){if(claudeBrowser!=null)claudeBrowser.navigation();}
   public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return !"https".equals(r.getUrl().getScheme());}
   public void onPageFinished(WebView v,String url){address.setText(Uri.parse(url).getHost());CookieManager.getInstance().flush();}
  });
  done.setOnClickListener(v->{if(claudeBrowser!=null){done.setEnabled(false);done.setText("Reading Claude usage…");claudeBrowser.start((ok,message)->{done.setEnabled(true);done.setText("Refresh Claude usage from this page");info.setText(message);if(ok)finish();});return;}CookieManager.getInstance().flush();Store.prefs(this).edit().putBoolean(p+"Connected",true).putString("status","Discovering subscriptions…").apply();RefreshJob.now(this);finish();});
  web.loadUrl(p.equals("claude")?"https://claude.ai/login":"https://chatgpt.com/auth/login");
 }
 public void onDestroy(){if(claudeBrowser!=null)claudeBrowser.cancel();if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
