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
  // One-time cleanup of single-login status text from versions before 0.4.
  if(Store.prefs(this).getString("claudeStatus","").startsWith("Claude:"))Store.prefs(this).edit().remove("claudeStatus").apply();
  RefreshJob.schedule(this);
  handleRefresh(getIntent());
 }
 public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleRefresh(i);}
 void handleRefresh(Intent i){if(i.getBooleanExtra("refresh",false)){i.removeExtra("refresh");RefreshJob.now(this);syncClaude();}}
 void display(){startActivity(new Intent(this,DisplayActivity.class));}
 /** Intent for one login's screen, carrying every login's display name for the Claude refresh chain. */
 Intent loginIntent(String provider,int slot){
  Intent i=Profiles.login(this,provider,slot,Store.connected(this,provider,slot)?Store.label(this,provider,slot):"");
  for(int s=1;s<=Profiles.MAX;s++)i.putExtra("label"+s,Store.label(this,Profiles.CLAUDE,s));
  return i;
 }
 void openLogin(String provider,int slot){startActivity(loginIntent(provider,slot));}
 /** Claude logins sync one after another: each opens its own screen, syncs and hands over to the next. */
 void syncClaude(){
  java.util.List<Integer> slots=Store.slots(this,Profiles.CLAUDE);if(slots.isEmpty())return;
  int[] rest=new int[slots.size()-1];for(int k=1;k<slots.size();k++)rest[k-1]=slots.get(k);
  startActivity(loginIntent(Profiles.CLAUDE,slots.get(0)).putExtra("chain",rest));
 }
 void addLogin(String provider){
  int slot=Store.freeSlot(this,provider);
  if(slot<0){Toast.makeText(this,"All "+Profiles.maxSlots()+" "+Profiles.title(provider)+" logins are in use. Remove one first.",Toast.LENGTH_LONG).show();return;}
  // Start from a clean sign-in, even if an earlier attempt in this slot was abandoned.
  if(slot==1)Profiles.clearSite(this,provider);else Profiles.wipe(this,provider,slot);
  openLogin(provider,slot);
 }
 void refreshAll(){Store.prefs(this).edit().putString("status","Refresh queued · waiting for network / Android").apply();RefreshJob.now(this);syncClaude();render(true);}
 void addWidget(){AppWidgetManager m=AppWidgetManager.getInstance(this);if(m.isRequestPinAppWidgetSupported())m.requestPinAppWidget(new ComponentName(this,UsageWidget.class),null,null);else Toast.makeText(this,"Long press your home screen → Widgets → Usage Widget",Toast.LENGTH_LONG).show();}
 void setAuto(boolean on){Store.prefs(this).edit().putBoolean("auto",on).apply();RefreshJob.schedule(this);render(true);}
 void loginMenu(View anchor,String provider,int slot){
  PopupMenu m=new PopupMenu(this,anchor);
  m.getMenu().add(0,1,0,Profiles.CLAUDE.equals(provider)?"Refresh now":"Open / sign in again");
  if(Profiles.CODEX.equals(provider))m.getMenu().add(0,4,1,"Refresh now");
  m.getMenu().add(0,2,2,"Rename");
  m.getMenu().add(0,3,3,"Sign out & remove");
  m.setOnMenuItemClickListener(item->{
   switch(item.getItemId()){
    case 1:openLogin(provider,slot);return true;
    case 4:RefreshJob.now(this,slot);Toast.makeText(this,"Refresh queued",Toast.LENGTH_SHORT).show();return true;
    case 2:rename(provider,slot);return true;
    case 3:removeLogin(provider,slot);return true;
    default:return false;
   }
  });
  m.show();
 }
 void rename(String provider,int slot){
  EditText input=new EditText(this);input.setSingleLine(true);input.setText(Store.label(this,provider,slot));input.setSelectAllOnFocus(true);
  new AlertDialog.Builder(this).setTitle("Name this "+Profiles.title(provider)+" login").setView(input)
   .setPositiveButton("Save",(d,w)->{Store.setLabel(this,provider,slot,input.getText().toString());UsageWidget.update(this);render(true);})
   .setNeutralButton("Use email",(d,w)->{Store.setLabel(this,provider,slot,"");render(true);})
   .setNegativeButton("Cancel",null).show();
 }
 void removeLogin(String provider,int slot){
  new AlertDialog.Builder(this).setMessage("Sign out "+Store.label(this,provider,slot)+" and delete its cached usage? Your other logins are not affected.")
   .setPositiveButton("Sign out",(d,w)->{
    // Remove the saved flags first, so clearing slot 1 knows whether the other provider still uses it.
    Store.removeLogin(this,provider,slot);
    if(slot==1)Profiles.clearSite(this,provider);else Profiles.wipe(this,provider,slot);
    RefreshJob.schedule(this);UsageWidget.update(this);render(true);
   }).setNegativeButton("Cancel",null).show();
 }
 void disconnect(){new AlertDialog.Builder(this).setMessage("Sign out every login and remove display choices and all cached usage from this app?").setPositiveButton("Delete",(d,w)->{getSystemService(android.app.job.JobScheduler.class).cancelAll();
  for(int slot=2;slot<=Profiles.MAX;slot++){Profiles.wipe(this,Profiles.CLAUDE,slot);Profiles.wipe(this,Profiles.CODEX,slot);}
  new Thread(()->{synchronized(UsageClient.class){Store.prefs(this).edit().clear().putBoolean("auto",false).commit();runOnUiThread(()->CookieManager.getInstance().removeAllCookies(ok->{CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();UsageWidget.update(this);render(true);}));}}).start();}).setNegativeButton("Cancel",null).show();}

 /** One login's status line: "2 workspaces · synced 4m ago", or its last problem. Returns {text, colour}. */
 Object[] loginSummary(String provider,int slot){
  JSONArray all=Store.rows(this);int count=0;long latest=0;boolean stale=false;
  for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r==null||!provider.equals(r.optString("provider"))||Store.rowSlot(r)!=slot)continue;
   count++;latest=Math.max(latest,r.optLong("updated",0));if(!UsageFormat.badge(r).isEmpty())stale=true;}
  String status=Store.loginStatus(this,provider,slot);
  if(count==0)return new Object[]{status.isEmpty()?"No workspaces yet · tap to sync":status,Render.MUTED};
  String text=count+(count==1?" workspace":" workspaces")+(latest>0?" · "+UsageFormat.age(latest).replace("updated","synced"):"");
  if(stale&&!status.isEmpty()&&!status.equals("Synced"))text+=" · "+status;
  return new Object[]{text,stale?Render.WARN_SOFT:Render.GOOD};
 }

 /** Rebuild only when something visible changed, so the 2.5 s poll doesn't flicker or reset scrolling. */
 void render(boolean force){
  if(root==null)return;
  SharedPreferences p=Store.prefs(this);
  String sig=new java.util.TreeMap<>(p.getAll()).toString()+(System.currentTimeMillis()/60000);
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

  boolean autoOn=Store.prefs(this).getBoolean("auto",true);
  connections(Profiles.CLAUDE,"CLAUDE · SYNCS WHEN OPENED, ONE LOGIN AFTER ANOTHER");
  connections(Profiles.CODEX,"CHATGPT / CODEX · "+(autoOn?"AUTO EVERY ~30 MIN":"BACKGROUND REFRESH OFF"));

  root.addView(section(this,"ACCOUNTS"));
  JSONArray rows=Store.visibleRows(this);int hidden=Store.hiddenCount(this);
  for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null)root.addView(accountCard(r),margins(fill(),this,0,0,0,8));}
  if(rows.length()==0){
   TextView empty=text(this,Store.accounts(this).length()==0?"No accounts yet. Add a Claude or ChatGPT login above.":"All accounts are hidden.",13,Render.MUTED,false,false);
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
 /** Section listing each login of a provider, plus "Add … account" while a slot is free. */
 void connections(String provider,String heading){
  root.addView(section(this,heading));
  for(int slot:Store.slots(this,provider))root.addView(loginRow(provider,slot),margins(fill(),this,0,0,0,6));
  int free=Store.freeSlot(this,provider);
  if(free>0){
   boolean none=Store.slots(this,provider).isEmpty();
   int left=0;for(int s2=1;s2<=Profiles.maxSlots();s2++)if(!Store.connected(this,provider,s2))left++;
   TextView add=text(this,"+  "+(none?"Connect ":"Add ")+Profiles.title(provider)+(none?"":" account")+(none?"":"  ("+left+(left==1?" slot":" slots")+" left)"),13,Render.MUTED,false,false);
   add.setBackgroundResource(R.drawable.add_dashed);add.setPadding(dp(this,12),dp(this,12),dp(this,12),dp(this,12));
   add.setOnClickListener(v->addLogin(provider));
   root.addView(add,margins(fill(),this,0,0,0,6));
  }
 }
 View loginRow(String provider,int slot){
  LinearLayout r=row(this);r.setBackgroundResource(R.drawable.surface_card);r.setPadding(dp(this,12),dp(this,10),dp(this,4),dp(this,10));
  r.addView(logo(this,provider,30));
  LinearLayout names=column(this);names.setPadding(dp(this,10),0,dp(this,6),0);
  TextView n=text(this,Store.label(this,provider,slot),14,Render.TEXT,false,false);n.setSingleLine(true);n.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(n);
  Object[] st=loginSummary(provider,slot);names.addView(text(this,"● "+st[0],11,(Integer)st[1],false,false));
  r.addView(names,weight());
  TextView more=text(this,"⋮",20,Render.MUTED,false,false);more.setPadding(dp(this,12),dp(this,4),dp(this,12),dp(this,4));more.setContentDescription("Options for "+Store.label(this,provider,slot));
  more.setOnClickListener(v->loginMenu(v,provider,slot));r.addView(more);
  r.setOnClickListener(v->{if(Profiles.CLAUDE.equals(provider))openLogin(provider,slot);else loginMenu(v,provider,slot);});
  return r;
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
  Render.add(h,"usage-widget",Render.TERM_GREEN);Render.add(h," v0.4",Render.TERM_MUTED);
  root.addView(termSized(h,14));
  root.addView(term("# "+Render.summary(this).replace("\n","\n# "),Render.TERM_MUTED));
  root.addView(term("\n## connections",Render.TERM_MUTED));
  for(String provider:new String[]{Profiles.CLAUDE,Profiles.CODEX}){
   for(int slot:Store.slots(this,provider))root.addView(connectionLine(provider,slot));
   if(Store.freeSlot(this,provider)>0)root.addView(action("[ + add "+(Profiles.CLAUDE.equals(provider)?"claude":"chatgpt")+" ]",Render.TERM_TEXT,()->addLogin(provider)));
  }
  root.addView(term("\n## accounts",Render.TERM_MUTED));
  JSONArray rows=Store.visibleRows(this);int hidden=Store.hiddenCount(this);
  for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;TextView t=termSized(Render.terminal(r),12);t.setPadding(0,dp(this,4),0,dp(this,4));root.addView(t);}
  if(rows.length()==0)root.addView(term(Store.accounts(this).length()==0?"(none) connect an account below":"(all hidden)",Render.TERM_MUTED));
  if(hidden>0)root.addView(action("[ "+hidden+" hidden · manage ]",Render.TERM_TEXT,this::display));
  root.addView(term("\n## actions",Render.TERM_MUTED));
  root.addView(action("[ refresh --all ]",Render.TERM_GREEN,this::refreshAll));
  root.addView(action("[ display ]",Render.TERM_TEXT,this::display));
  boolean auto=Store.prefs(this).getBoolean("auto",true);
  root.addView(action("[ codex-bg: "+(auto?"on":"off")+" ]",Render.TERM_TEXT,()->setAuto(!auto)));
  root.addView(action("[ add-widget ]",Render.TERM_TEXT,this::addWidget));
  root.addView(action("[ disconnect --purge ]",Render.LOW,this::disconnect));
  root.addView(term("\n# only usage and account info is read; no prompts are sent",Render.TERM_MUTED));
 }
 TextView term(CharSequence s,int color){TextView t=text(this,s,12,color,false,true);t.setLineSpacing(dp(this,2),1);return t;}
 TextView termSized(CharSequence s,float sp){TextView t=text(this,s,sp,Render.TERM_TEXT,false,true);t.setLineSpacing(dp(this,2),1);return t;}
 View connectionLine(String provider,int slot){
  SpannableStringBuilder s=new SpannableStringBuilder();
  Object[] st=loginSummary(provider,slot);
  Render.add(s,"● ",Profiles.CLAUDE.equals(provider)?Render.CLAUDE:Render.CODEX);
  Render.add(s,Render.pad(Profiles.CLAUDE.equals(provider)?"claude":"chatgpt",8),Render.TERM_TEXT);
  Render.add(s,Store.label(this,provider,slot),Render.TERM_BRIGHT);
  Render.add(s,"\n  "+String.valueOf(st[0]).toLowerCase(java.util.Locale.US),(Integer)st[1]==Render.GOOD?Render.TERM_GREEN:(Integer)st[1]);
  TextView t=termSized(s,12);t.setPadding(0,dp(this,4),0,dp(this,4));t.setOnClickListener(v->loginMenu(v,provider,slot));return t;
 }
 View action(String label,int color,Runnable r){TextView t=term(label,color);t.setPadding(0,dp(this,8),0,dp(this,8));tappable(t,r);return t;}

 public void onResume(){super.onResume();render(true);handler.post(poll);}public void onPause(){handler.removeCallbacks(poll);super.onPause();}
}
