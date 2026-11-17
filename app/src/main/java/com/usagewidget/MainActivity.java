package com.usagewidget;
import android.app.*;
import android.appwidget.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.widget.*;
import android.view.*;
import android.webkit.*;
import org.json.*;
public class MainActivity extends Activity {
 LinearLayout root,cards;TextView status;Handler handler=new Handler();final Runnable poll=new Runnable(){public void run(){render();handler.postDelayed(this,2500);}};
 public void onCreate(Bundle b){super.onCreate(b);CookieManager.getInstance();
  ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,32,24,24);root.setBackgroundColor(Color.rgb(12,16,23));scroll.addView(root);setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(24,32+insets.getSystemWindowInsetTop(),24,24+insets.getSystemWindowInsetBottom());return insets;});
  text(root,"USAGE WIDGET",28,Color.WHITE);text(root,"Your AI allowances, in one place",16,0xffa8f0c6);
  text(root,"Preview 0.2 · Connections need testing with your accounts. Only usage and account information is read; no AI prompts are sent.",13,0xffa7b0c0);
  button("Connect / reconnect ChatGPT & Codex",()->login("codex"));button("Connect / reconnect Claude",()->login("claude"));
  button("Refresh all",()->{if(Store.prefs(this).getBoolean("claudeConnected",false))login("claude");Store.prefs(this).edit().putString("status","Refresh queued · waiting for network / Android").apply();RefreshJob.now(this);render();});
  Switch auto=new Switch(this);auto.setText("Codex background refresh (~30 minutes)");auto.setTextColor(Color.WHITE);auto.setChecked(Store.prefs(this).getBoolean("auto",true));auto.setOnCheckedChangeListener((v,on)->{Store.prefs(this).edit().putBoolean("auto",on).apply();RefreshJob.schedule(this);});root.addView(auto);
  button("Add home-screen widget",()->{AppWidgetManager m=AppWidgetManager.getInstance(this);if(m.isRequestPinAppWidgetSupported())m.requestPinAppWidget(new ComponentName(this,UsageWidget.class),null,null);else Toast.makeText(this,"Long press your home screen → Widgets → Usage Widget",Toast.LENGTH_LONG).show();});
  status=text(root,"",13,0xffa7b0c0);cards=new LinearLayout(this);cards.setOrientation(1);root.addView(cards);
  text(root,"Sign in to discover every workspace accessible to that login. An organisation may not expose usage or a plan name; these fields stay unavailable. Claude refresh requires its visible browser page: tap widget refresh or Refresh all, and Claude usage syncs automatically once the page loads. Codex background timing depends on Android battery and network scheduling. Reset times use your phone’s time zone.",13,0xffa7b0c0);
  button("Disconnect all & delete local data",()->new AlertDialog.Builder(this).setMessage("Remove both connections and all cached usage from this app?").setPositiveButton("Delete",(d,w)->{getSystemService(android.app.job.JobScheduler.class).cancelAll();new Thread(()->{synchronized(UsageClient.class){Store.prefs(this).edit().clear().putBoolean("auto",false).commit();runOnUiThread(()->CookieManager.getInstance().removeAllCookies(ok->{CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();UsageWidget.update(this);render();}));}}).start();}).setNegativeButton("Cancel",null).show());
  RefreshJob.schedule(this);
  handleRefresh(getIntent());
 }
 public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleRefresh(i);}
 void handleRefresh(Intent i){if(i.getBooleanExtra("refresh",false)){i.removeExtra("refresh");RefreshJob.now(this);if(Store.prefs(this).getBoolean("claudeConnected",false))login("claude");}}
 void login(String p){startActivity(new Intent(this,LoginActivity.class).putExtra("provider",p));}
 TextView text(LinearLayout layout,String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,8,0,12);layout.addView(t);return t;}
 void button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);root.addView(b);b.setOnClickListener(v->r.run());}
 void render(){if(status==null)return;status.setText(Store.status(this));cards.removeAllViews();JSONArray rows=Store.rows(this);for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(20,12,20,12);card.setBackgroundResource(R.drawable.card);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,10,0,10);cards.addView(card,lp);text(card,r.optString("name")+" · "+r.optString("plan","Plan unavailable"),17,Color.WHITE);text(card,UsageFormat.detail(r),14,0xffa8f0c6);}}
 public void onResume(){super.onResume();handler.post(poll);}public void onPause(){handler.removeCallbacks(poll);super.onPause();}
}
