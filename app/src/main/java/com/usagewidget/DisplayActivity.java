package com.usagewidget;
import android.app.Activity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.Set;
import static com.usagewidget.Ui.*;

/** Display settings: choose cards or terminal style, and which discovered accounts to show. */
public class DisplayActivity extends Activity {
 LinearLayout root;
 public void onCreate(Bundle b){super.onCreate(b);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Render.BG);root=column(this);scroll.addView(root);setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(this,16),dp(this,16)+insets.getSystemWindowInsetTop(),dp(this,16),dp(this,20)+insets.getSystemWindowInsetBottom());return insets;});
  render();
 }
 void render(){
  root.removeAllViews();
  LinearLayout head=row(this);
  TextView back=text(this,"←",22,Render.TEXT,false,false);back.setContentDescription("Back");pad(back,8);tappable(back,this::finish);head.addView(back);
  TextView title=text(this,"Display",20,Render.TEXT,true,false);title.setPadding(dp(this,6),0,0,0);head.addView(title);
  root.addView(head);

  root.addView(section(this,"STYLE · APP AND WIDGET"));
  LinearLayout options=row(this);options.setGravity(Gravity.TOP);
  boolean terminal=Store.terminal(this);
  options.addView(styleOption(Store.STYLE_CARDS,"Cards",!terminal,cardsPreview()),margins(weight(),this,0,0,4,0));
  options.addView(styleOption(Store.STYLE_TERMINAL,"Terminal",terminal,terminalPreview()),margins(weight(),this,4,0,0,0));
  root.addView(options);

  root.addView(section(this,"ACCOUNTS · SHOW ON WIDGET AND APP"));
  JSONArray rows=Store.rows(this);Set<String> hidden=Store.hidden(this);
  if(rows.length()==0){TextView t=text(this,"No accounts discovered yet. Connect Claude or Codex from the main screen.",13,Render.MUTED,false,false);t.setPadding(dp(this,4),0,0,0);root.addView(t);}
  for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null)root.addView(accountToggle(r,!hidden.contains(r.optString("id"))));}
  TextView note=text(this,"New accounts appear switched on. Hidden accounts still refresh, so switching one back on shows current values. Disconnect & delete data resets these choices.",12,Render.MUTED,false,false);
  note.setPadding(dp(this,4),dp(this,14),dp(this,4),0);root.addView(note);
 }
 View styleOption(String style,String label,boolean selected,View preview){
  LinearLayout o=column(this);o.setBackgroundResource(selected?R.drawable.option_selected:R.drawable.option_idle);pad(o,8);
  o.addView(preview,new LinearLayout.LayoutParams(-1,dp(this,70)));
  LinearLayout l=row(this);l.setPadding(dp(this,2),dp(this,8),0,0);
  l.addView(text(this,label,13,selected?Render.TEXT:Render.MUTED,selected,false),weight());
  if(selected)l.addView(text(this,"✓",14,Render.GOOD,true,false));
  o.addView(l);
  o.setContentDescription(label+" style"+(selected?", selected":""));
  o.setOnClickListener(v->{Store.setStyle(this,style);UsageWidget.update(this);render();});
  return o;
 }
 View cardsPreview(){
  LinearLayout p=column(this);p.setBackgroundResource(R.drawable.row_card);pad(p,8);
  p.addView(bar(0.6f,Render.TRACK,5));
  p.addView(bar(0.7f,Render.GOOD,4));p.addView(bar(0.4f,Render.WARN,4));p.addView(bar(0.85f,Render.GOOD,4));
  return p;
 }
 View bar(float fraction,int color,int heightDp){
  LinearLayout holder=row(this);holder.setPadding(0,dp(this,3),0,dp(this,3));
  View fill=new View(this);fill.setBackgroundColor(color);
  holder.addView(fill,new LinearLayout.LayoutParams(0,dp(this,heightDp),fraction));
  holder.addView(new View(this),new LinearLayout.LayoutParams(0,dp(this,heightDp),1-fraction));
  return holder;
 }
 View terminalPreview(){
  SpannableStringBuilder s=new SpannableStringBuilder();
  Render.add(s,"$ status\n",Render.TERM_GREEN);
  Render.add(s,"● ",Render.CLAUDE);Render.add(s,"claude ",Render.TERM_TEXT);Render.add(s,"███░ 72%\n",Render.TERM_GREEN);
  Render.add(s,"● ",Render.CODEX);Render.add(s,"codex  ",Render.TERM_TEXT);Render.add(s,"████ 88%",Render.TERM_GREEN);
  TextView t=text(this,s,9,Render.TERM_TEXT,false,true);t.setBackgroundColor(Render.TERM_BG);pad(t,8);
  return t;
 }
 View accountToggle(JSONObject r,boolean visible){
  LinearLayout row=row(this);row.setPadding(dp(this,4),dp(this,10),dp(this,4),dp(this,10));
  row.addView(logo(this,r,30));
  LinearLayout names=column(this);names.setPadding(dp(this,10),0,dp(this,8),0);
  names.addView(text(this,r.optString("name"),14,visible?Render.TEXT:Render.MUTED,false,false));
  String plan=UsageFormat.plan(r.optString("plan"));
  if(!plan.isEmpty())names.addView(text(this,plan,12,Render.MUTED,false,false));
  row.addView(names,weight());
  Switch s=new Switch(this);s.setChecked(visible);s.setContentDescription("Show "+r.optString("name"));
  String id=r.optString("id");
  s.setOnCheckedChangeListener((v,on)->{Store.setHidden(this,id,!on);UsageWidget.update(this);render();});
  row.addView(s);
  return row;
 }
}
