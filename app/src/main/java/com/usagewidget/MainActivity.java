package com.usagewidget;
import android.app.*;
import android.appwidget.*;
import android.os.*;
import android.content.*;
import android.text.SpannableStringBuilder;
import android.widget.*;
import android.view.*;
import android.webkit.*;
import org.json.*;
import static com.usagewidget.Ui.*;
public class MainActivity extends Activity {
 LinearLayout root;ScrollView scroll;Handler handler=new Handler();String lastSignature="";
 final Runnable poll=new Runnable(){public void run(){render(false);handler.postDelayed(this,2500);}};
 public void onCreate(Bundle b){super.onCreate(b);CookieManager.getInstance();
  scroll=new ScrollView(this);root=column(this);scroll.addView(root);setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(this,16),dp(this,20)+insets.getSystemWindowInsetTop(),dp(this,16),dp(this,20)+insets.getSystemWindowInsetBottom());return insets;});
  RefreshJob.schedule(this);
  handleRefresh(getIntent());
 }
 public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleRefresh(i);}
 void handleRefresh(Intent i){if(i.getBooleanExtra("refresh",false)){i.removeExtra("refresh");RefreshJob.now(this);if(Store.prefs(this).getBoolean("claudeConnected",false))login("claude");}}
 void login(String p){startActivity(new Intent(this,LoginActivity.class).putExtra("provider",p));}
 void display(){startActivity(new Intent(this,DisplayActivity.class));}
 void refreshAll(){if(Store.prefs(this).getBoolean("claudeConnected",false))login("claude");Store.prefs(this).edit().putString("status","Refresh queued · waiting for network / Android").apply();RefreshJob.now(this);render(true);}
 void addWidget(){AppWidgetManager m=AppWidgetManager.getInstance(this);if(m.isRequestPinAppWidgetSupported())m.requestPinAppWidget(new ComponentName(this,UsageWidget.class),null,null);else Toast.makeText(this,"Long press your home screen → Widgets → Usage Widget",Toast.LENGTH_LONG).show();}
 void setAuto(boolean on){Store.prefs(this).edit().putBoolean("auto",on).apply();RefreshJob.schedule(this);render(true);}
 void disconnect(){new AlertDialog.Builder(this).setMessage("Remove both connections, display choices and all cached usage from this app?").setPositiveButton("Delete",(d,w)->{getSystemService(android.app.job.JobScheduler.class).cancelAll();new Thread(()->{synchronized(UsageClient.class){Store.prefs(this).edit().clear().putBoolean("auto",false).commit();runOnUiThread(()->CookieManager.getInstance().removeAllCookies(ok->{CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();UsageWidget.update(this);render(true);}));}}).start();}).setNegativeButton("Cancel",null).show();}

 /** Rebuild only when something visible changed, so the 2.5 s poll doesn't flicker or reset scrolling. */
 void render(boolean force){
  if(root==null)return;
  SharedPreferences p=Store.prefs(this);
  String sig=Store.style(this)+p.getString("rows","")+p.getString("status","")+p.getString("claudeStatus","")+p.getBoolean("claudeConnected",false)+p.getBoolean("codexConnected",false)+p.getBoolean("auto",true)+Store.hidden(this)+(System.currentTimeMillis()/60000);
  if(!force&&sig.equals(lastSignature))return;
  lastSignature=sig;root.removeAllViews();
  boolean terminal=Store.terminal(this);
  scroll.setBackgroundColor(terminal?Render.TERM_BG:Render.BG);
  if(terminal)terminalScreen();else cardsScreen();
 }

 // ---------- Cards style ----------
 void cardsScreen(){
  LinearLayout head=row(this);
  LinearLayout titles=column(this);
  titles.addView(text(this,"AI usage",22,Render.TEXT,true,false));
  titles.addView(text(this,Render.summary(this),12,Render.MUTED,false,false));
  head.addView(titles,weight());
  ImageView gear=new ImageView(this);gear.setImageResource(R.drawable.ic_tune);gear.setContentDescription("Display and accounts");pad(gear,10);tappable(gear,this::display);
  head.addView(gear,new LinearLayout.LayoutParams(dp(this,44),dp(this,44)));
  root.addView(head);

  root.addView(section(this,"CONNECTIONS"));
  LinearLayout tiles=row(this);
  tiles.addView(connectionTile("claude","Claude",Store.prefs(this).getBoolean("claudeConnected",false),"Syncs when opened"),margins(weight(),this,0,0,4,0));
  tiles.addView(connectionTile("codex","Codex",Store.prefs(this).getBoolean("codexConnected",false),Store.prefs(this).getBoolean("auto",true)?"Auto · every ~30 min":"Background refresh off"),margins(weight(),this,4,0,0,0));
  root.addView(tiles);

  root.addView(section(this,"ACCOUNTS"));
  JSONArray rows=Store.visibleRows(this);int hidden=Store.hiddenCount(this);
  for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null)root.addView(accountCard(r),margins(fill(),this,0,0,0,8));}
  if(rows.length()==0){
   TextView empty=text(this,Store.rows(this).length()==0?"No accounts yet. Connect Claude or Codex above.":"All accounts are hidden.",13,Render.MUTED,false,false);
   empty.setPadding(dp(this,4),dp(this,4),0,dp(this,8));root.addView(empty);
  }
  if(hidden>0){TextView manage=text(this,hidden+" hidden · Manage",13,Render.CODEX_SOFT,false,false);pad(manage,6);tappable(manage,this::display);root.addView(manage);}

  TextView refresh=text(this,"↻  Refresh all",15,Render.BG,true,false);
  refresh.setGravity(Gravity.CENTER);refresh.setBackgroundResource(R.drawable.button_primary);refresh.setPadding(0,dp(this,12),0,dp(this,12));
  refresh.setOnClickListener(v->refreshAll());
  root.addView(refresh,margins(fill(),this,0,12,0,0));

  root.addView(section(this,"SETTINGS"));
  Switch auto=new Switch(this);auto.setText("Codex background refresh (~30 min)");auto.setTextColor(Render.TEXT);auto.setTextSize(14);auto.setChecked(Store.prefs(this).getBoolean("auto",true));
  auto.setPadding(dp(this,4),dp(this,10),dp(this,4),dp(this,10));auto.setOnCheckedChangeListener((v,on)->setAuto(on));root.addView(auto);
  root.addView(settingRow("Display & accounts",Render.TEXT,"›",this::display));
  root.addView(settingRow("Add home-screen widget",Render.TEXT,"›",this::addWidget));
  root.addView(settingRow("Disconnect & delete data",Render.LOW,"",this::disconnect));
  TextView note=text(this,"Only usage and account information is read; no prompts are sent. Claude refreshes while its screen is open; Codex timing depends on Android. Reset times use your phone's time zone.",12,Render.MUTED,false,false);
  note.setPadding(dp(this,4),dp(this,16),dp(this,4),0);root.addView(note);
 }
 View connectionTile(String provider,String name,boolean connected,String detail){
  LinearLayout t=column(this);t.setBackgroundResource(R.drawable.surface_card);pad(t,12);
  LinearLayout top=row(this);top.addView(logo(this,provider,22));
  TextView n=text(this,name,14,Render.TEXT,true,false);n.setPadding(dp(this,8),0,0,0);top.addView(n);t.addView(top);
  TextView s=text(this,connected?"● Connected":"○ Tap to connect",12,connected?Render.GOOD:Render.MUTED,false,false);s.setPadding(0,dp(this,6),0,0);t.addView(s);
  t.addView(text(this,connected?detail:"Sign in on the provider page",11,Render.MUTED,false,false));
  t.setOnClickListener(v->login(provider));t.setContentDescription(name+(connected?" connected. Tap to reconnect":" not connected. Tap to connect"));
  return t;
 }
 View accountCard(JSONObject r){
  String badge=UsageFormat.badge(r);
  LinearLayout card=column(this);card.setBackgroundResource(badge.isEmpty()?R.drawable.surface_card:R.drawable.surface_card_stale);pad(card,12);
  LinearLayout top=row(this);top.addView(logo(this,r,30));
  TextView name=text(this,r.optString("name"),14,Render.TEXT,true,false);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(this,10),0,dp(this,6),0);
  top.addView(name,weight());
  String plan=UsageFormat.plan(r.optString("plan"));
  if(!badge.isEmpty())top.addView(pill(this,badge,Render.WARN_SOFT,R.drawable.pill_stale));
  else if(!plan.isEmpty())top.addView(pill(this,plan,Render.brandSoft(r),Render.claude(r)?R.drawable.pill_claude:R.drawable.pill_codex));
  card.addView(top);
  LinearLayout gauges=row(this);gauges.setPadding(0,dp(this,12),0,0);
  String key=UsageFormat.key(r);
  addGauge(gauges,"5-hour",r.optJSONObject("session"),key);
  addGauge(gauges,"Weekly",r.optJSONObject("weekly"),key);
  String extra=r.optString("extra","");
  if(!extra.isEmpty()){TextView e=text(this,extra,12,Render.brandSoft(r),false,false);e.setPadding(dp(this,4),0,0,0);gauges.addView(e,weight());}
  card.addView(gauges);
  String reason=UsageFormat.reason(r);
  if(!reason.isEmpty()){TextView why=text(this,(r.optLong("updated",0)>0?"Last refresh failed: ":"")+reason+(r.optLong("updated",0)>0?" · showing saved values.":""),12,Render.MUTED,false,false);why.setPadding(0,dp(this,8),0,0);card.addView(why);}
  long updated=r.optLong("updated",0);
  if(updated>0){TextView u=text(this,UsageFormat.age(updated),11,Render.MUTED,false,false);u.setPadding(0,dp(this,6),0,0);card.addView(u);}
  return card;
 }
 void addGauge(LinearLayout parent,String fallback,JSONObject w,String key){
  if(w==null)return;
  LinearLayout g=column(this);g.setGravity(Gravity.CENTER_HORIZONTAL);
  g.addView(new GaugeView(this,UsageFormat.left(w,key)),new LinearLayout.LayoutParams(dp(this,58),dp(this,58)));
  String reset=UsageFormat.resetShort(w);
  TextView l=text(this,UsageFormat.laneLabel(fallback,w)+(reset.isEmpty()?"":"\n"+reset),11,Render.MUTED,false,false);l.setGravity(Gravity.CENTER);l.setPadding(0,dp(this,4),0,0);g.addView(l);
  parent.addView(g,margins(wrap(),this,0,0,16,0));
 }
 View settingRow(String label,int color,String trailing,Runnable action){
  LinearLayout r=row(this);r.setPadding(dp(this,4),dp(this,14),dp(this,4),dp(this,14));
  r.addView(text(this,label,14,color,false,false),weight());
  if(!trailing.isEmpty())r.addView(text(this,trailing,18,Render.MUTED,false,false));
  tappable(r,action);return r;
 }

 // ---------- Terminal style ----------
 void terminalScreen(){
  SpannableStringBuilder h=new SpannableStringBuilder();
  Render.add(h,"usage-widget",Render.TERM_GREEN);Render.add(h," v0.3",Render.TERM_MUTED);
  root.addView(termSized(h,14));
  root.addView(term("# "+Render.summary(this).replace("\n","\n# "),Render.TERM_MUTED));
  root.addView(term("\n## connections",Render.TERM_MUTED));
  root.addView(connectionLine("claude",Render.CLAUDE,Store.prefs(this).getBoolean("claudeConnected",false),"sync on open"));
  root.addView(connectionLine("codex",Render.CODEX,Store.prefs(this).getBoolean("codexConnected",false),Store.prefs(this).getBoolean("auto",true)?"auto ~30m":"auto off"));
  root.addView(term("\n## accounts",Render.TERM_MUTED));
  JSONArray rows=Store.visibleRows(this);int hidden=Store.hiddenCount(this);
  for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;TextView t=termSized(Render.terminal(r),12);t.setPadding(0,dp(this,4),0,dp(this,4));root.addView(t);}
  if(rows.length()==0)root.addView(term(Store.rows(this).length()==0?"(none) connect an account below":"(all hidden)",Render.TERM_MUTED));
  if(hidden>0)root.addView(action("[ "+hidden+" hidden · manage ]",Render.TERM_TEXT,this::display));
  root.addView(term("\n## actions",Render.TERM_MUTED));
  root.addView(action("[ refresh --all ]",Render.TERM_GREEN,this::refreshAll));
  root.addView(action("[ display ]",Render.TERM_TEXT,this::display));
  root.addView(action("[ connect claude ]",Render.TERM_TEXT,()->login("claude")));
  root.addView(action("[ connect codex ]",Render.TERM_TEXT,()->login("codex")));
  boolean auto=Store.prefs(this).getBoolean("auto",true);
  root.addView(action("[ codex-bg: "+(auto?"on":"off")+" ]",Render.TERM_TEXT,()->setAuto(!auto)));
  root.addView(action("[ add-widget ]",Render.TERM_TEXT,this::addWidget));
  root.addView(action("[ disconnect --purge ]",Render.LOW,this::disconnect));
  root.addView(term("\n# only usage and account info is read; no prompts are sent",Render.TERM_MUTED));
 }
 TextView term(CharSequence s,int color){TextView t=text(this,s,12,color,false,true);t.setLineSpacing(dp(this,2),1);return t;}
 TextView termSized(CharSequence s,float sp){TextView t=text(this,s,sp,Render.TERM_TEXT,false,true);t.setLineSpacing(dp(this,2),1);return t;}
 View connectionLine(String provider,int color,boolean connected,String detail){
  SpannableStringBuilder s=new SpannableStringBuilder();
  Render.add(s,"● ",color);Render.add(s,Render.pad(provider,9),Render.TERM_TEXT);
  Render.add(s,connected?"connected  ":"not-connected  ",connected?Render.TERM_GREEN:Render.TERM_MUTED);
  Render.add(s,connected?detail:"tap to connect",Render.TERM_MUTED);
  TextView t=termSized(s,12);t.setPadding(0,dp(this,4),0,dp(this,4));tappable(t,()->login(provider));return t;
 }
 View action(String label,int color,Runnable r){TextView t=term(label,color);t.setPadding(0,dp(this,8),0,dp(this,8));tappable(t,r);return t;}

 public void onResume(){super.onResume();render(true);handler.post(poll);}public void onPause(){handler.removeCallbacks(poll);super.onPause();}
}
