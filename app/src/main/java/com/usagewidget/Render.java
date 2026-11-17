package com.usagewidget;
import android.content.Context;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import org.json.*;
import java.util.Locale;

/** Shared look for the app and widget: brand colours, level colours and the terminal-style text. */
final class Render {
 static final int CLAUDE=0xFFD97757,CLAUDE_SOFT=0xFFF0A889,CODEX=0xFF3ECF9E,CODEX_SOFT=0xFF6FE0B8;
 static final int GOOD=0xFF4CC38A,WARN=0xFFE8B04A,WARN_SOFT=0xFFF2C46B,LOW=0xFFE5604D;
 static final int TEXT=0xFFE8EAF0,MUTED=0xFF8A93A3,TRACK=0xFF2A313C,BG=0xFF0C1017,SURFACE=0xFF171C25;
 static final int TERM_BG=0xFF0B0E0C,TERM_TEXT=0xFFC9D1C9,TERM_BRIGHT=0xFFF0EDE6,TERM_GREEN=0xFF5BD08A,TERM_MUTED=0xFF7A857C,TERM_EMPTY=0xFF2A332C;

 static boolean claude(JSONObject r){return "claude".equals(r.optString("provider"));}
 static int brand(JSONObject r){return claude(r)?CLAUDE:CODEX;}
 static int brandSoft(JSONObject r){return claude(r)?CLAUDE_SOFT:CODEX_SOFT;}
 static int logo(JSONObject r){return claude(r)?R.drawable.ic_claude:R.drawable.ic_codex;}
 static int tile(JSONObject r){return claude(r)?R.drawable.tile_claude:R.drawable.tile_codex;}
 static int levelColor(int left){switch(UsageFormat.level(left)){case UsageFormat.LEVEL_GOOD:return GOOD;case UsageFormat.LEVEL_WARN:return WARN;case UsageFormat.LEVEL_LOW:return LOW;default:return MUTED;}}

 /** Latest successful reading among the given rows (0 if none). */
 static long latest(JSONArray rows){long t=0;for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null)t=Math.max(t,r.optLong("updated",0));}return t;}

 /** "2 shown · 1 hidden · updated 3m ago" */
 static String summary(Context c){
  JSONArray visible=Store.visibleRows(c);int hidden=Store.hiddenCount(c);long at=latest(visible);
  StringBuilder s=new StringBuilder();
  s.append(visible.length()).append(visible.length()==1?" account":" accounts");
  if(hidden>0)s.append(" · ").append(hidden).append(" hidden");
  if(at>0)s.append(" · ").append(UsageFormat.age(at));
  String status=Store.prefs(c).getString("status","");
  if(status.startsWith("codex:"))s.append("\nCodex: ").append(status.substring(6).trim());
  return s.toString();
 }

 static String emptyText(Context c){
  if(Store.rows(c).length()==0)return "Open Usage Widget to connect your accounts.";
  return "All accounts hidden · open the app to choose.";
 }

 /** Terminal-style block for one account, coloured with spans (works in widgets and TextViews). */
 static CharSequence terminal(JSONObject r){
  SpannableStringBuilder s=new SpannableStringBuilder();
  String slug=r.optString("provider")+"/"+UsageFormat.workspace(r).toLowerCase(Locale.US).trim().replaceAll("\\s+","-");
  add(s,"● ",brand(r));add(s,slug,TERM_BRIGHT);
  String plan=UsageFormat.plan(r.optString("plan"));
  if(!plan.isEmpty()){add(s,"  ",TERM_TEXT);add(s,plan.toLowerCase(Locale.US).replace(' ','-'),brand(r));}
  String badge=UsageFormat.badge(r);
  if(!badge.isEmpty()){add(s,"  ",TERM_TEXT);add(s,badge.toUpperCase(Locale.US).replace(" · "," "),WARN);}
  String key=UsageFormat.key(r);
  lane(s,UsageFormat.laneLabel("5-hour",r.optJSONObject("session")),r.optJSONObject("session"),key);
  lane(s,UsageFormat.laneLabel("Weekly",r.optJSONObject("weekly")),r.optJSONObject("weekly"),key);
  String extra=r.optString("extra","");
  if(!extra.isEmpty()){add(s,"\n  ",TERM_TEXT);add(s,extra.toLowerCase(Locale.US),TERM_TEXT);}
  String reason=UsageFormat.reason(r);
  if(!reason.isEmpty()){add(s,"\n  ! ",WARN);add(s,reason,TERM_MUTED);}
  return s;
 }
 private static void lane(SpannableStringBuilder s,String label,JSONObject w,String key){
  if(w==null)return;
  int left=UsageFormat.left(w,key);
  String short_=label.equals("Weekly")?"week":label.equals("5-hour")?"5h":label.replace("-day","d").replace("-hour","h");
  add(s,"\n  "+pad(short_,5),TERM_TEXT);
  int filled=left<0?0:Math.round(left/10f);
  add(s,repeat('█',filled),levelColor(left));add(s,repeat('░',10-filled),TERM_EMPTY);
  add(s,left<0?"   --":String.format(Locale.US," %3d%%",left),TERM_TEXT);
  String reset=UsageFormat.resetShort(w);
  if(!reset.isEmpty())add(s,"  ↻"+reset,TERM_MUTED);
 }
 static void add(SpannableStringBuilder s,String text,int color){
  int start=s.length();s.append(text);
  s.setSpan(new ForegroundColorSpan(color),start,s.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
 }
 static String pad(String s,int n){StringBuilder b=new StringBuilder(s);while(b.length()<n)b.append(' ');return b.toString();}
 static String repeat(char ch,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(ch);return b.toString();}
}
