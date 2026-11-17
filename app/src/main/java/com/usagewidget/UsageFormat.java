package com.usagewidget;
import org.json.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
final class UsageFormat {
 static final int LEVEL_NONE=-1,LEVEL_LOW=0,LEVEL_WARN=1,LEVEL_GOOD=2;
 static String key(JSONObject r){return "claude".equals(r.optString("provider"))?"utilization":"used_percent";}
 static String remaining(JSONObject w,String key){
  int left=left(w,key);
  return left<0?"Unavailable":String.format(Locale.US,"%d%% remaining",left);
 }
 /** Percent remaining (0-100), or -1 when unavailable. */
 static int left(JSONObject w,String key){
  if(w==null||w.isNull(key))return -1;
  double n=w.optDouble(key,Double.NaN);
  if(!Double.isFinite(n)||n<0||n>100)return -1;
  return (int)Math.round(100-n);
 }
 /** Green above 50% left, amber 20-50%, red below 20%. */
 static int level(int left){return left<0?LEVEL_NONE:left>50?LEVEL_GOOD:left>=20?LEVEL_WARN:LEVEL_LOW;}
 static long reset(JSONObject w){if(w==null)return 0;
  if(w.optLong("reset_at",0)>0)return w.optLong("reset_at")*1000;
  // OffsetDateTime accepts both "...Z" and "...+00:00"; Instant.parse rejects offsets on Android 13 and older.
  try{return OffsetDateTime.parse(w.getString("resets_at")).toInstant().toEpochMilli();}catch(Exception e){return 0;}
 }
 static String resetText(JSONObject w){long t=reset(w);if(t==0)return "reset unavailable";
  if(t<=System.currentTimeMillis())return "reset passed · refresh needed";
  return "resets "+DateTimeFormatter.ofPattern("EEE HH:mm",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(t));
 }
 /** Compact reset time: "14:30" today, "Fri 09:00" otherwise, "" when unknown. */
 static String resetShort(JSONObject w){long t=reset(w);if(t==0)return "";
  if(t<=System.currentTimeMillis())return "reset passed";
  ZoneId z=ZoneId.systemDefault();Instant at=Instant.ofEpochMilli(t);
  boolean today=LocalDate.now(z).equals(at.atZone(z).toLocalDate());
  return DateTimeFormatter.ofPattern(today?"HH:mm":"EEE HH:mm",Locale.getDefault()).withZone(z).format(at);
 }
 static String laneLabel(String label,JSONObject w){
  long seconds=w==null?0:w.optLong("limit_window_seconds",0);
  if(seconds>0){if(seconds==604800)return "Weekly";if(seconds==18000)return "5-hour";if(seconds%86400==0)return (seconds/86400)+"-day";return (seconds/3600)+"-hour";}
  return label;
 }
 static String lane(String label,JSONObject w,String key){
  long seconds=w==null?0:w.optLong("limit_window_seconds",0);
  if(seconds>0){if(seconds==604800)label="Weekly";else if(seconds==18000)label="Session (5h)";else if(seconds%86400==0)label=(seconds/86400)+"-day window";else label=(seconds/3600)+"h window";}
  return label+": "+remaining(w,key)+" · "+resetText(w);}
 static String age(long at){if(at==0)return "never updated";long m=Math.max(0,(System.currentTimeMillis()-at)/60000);return m<1?"updated just now":"updated "+m+"m ago";}
 static String ageShort(long at){if(at==0)return "never";long m=Math.max(0,(System.currentTimeMillis()-at)/60000);
  if(m<1)return "now";if(m<60)return m+"m";if(m<48*60)return (m/60)+"h";return (m/1440)+"d";}
 /** Pretty plan name: "default_claude_max_20x" → "Max 20x", "plus" → "Plus". */
 static String plan(String raw){
  if(raw==null||raw.isEmpty()||"null".equals(raw))return "";
  String s=raw.toLowerCase(Locale.US);
  if(s.contains("max_20x"))return "Max 20x";if(s.contains("max_5x"))return "Max 5x";
  if(s.contains("max"))return "Max";if(s.contains("enterprise"))return "Enterprise";if(s.contains("team"))return "Team";if(s.contains("pro"))return "Pro";
  s=s.replace('_',' ').trim();
  return s.isEmpty()?"":Character.toUpperCase(s.charAt(0))+s.substring(1);
 }
 /** Workspace part of "Claude · Personal" → "Personal". */
 static String workspace(JSONObject r){String n=r.optString("name","");int i=n.indexOf(" · ");return i<0?n:n.substring(i+3);}
 /** "Stale · 3h" when an earlier reading exists, "Unavailable" when none, "" when healthy. */
 static String badge(JSONObject r){
  long updated=r.optLong("updated",0);
  // Claude only refreshes while its screen is open, so readings older than 30 minutes are also stale.
  boolean old="claude".equals(r.optString("provider"))&&updated>0&&System.currentTimeMillis()-updated>30*60*1000L;
  if(r.optString("error","").isEmpty()&&!old)return "";
  return updated>0?"Stale · "+ageShort(updated):"Unavailable";
 }
 /** Error text without its "Stale · "/"Unavailable · " prefix. */
 static String reason(JSONObject r){
  String e=r.optString("error","");
  for(String p:new String[]{"Stale · ","Unavailable · "})while(e.startsWith(p))e=e.substring(p.length());
  return e;
 }
 static String detail(JSONObject r){StringBuilder s=new StringBuilder();
  JSONObject a=r.optJSONObject("session"),b=r.optJSONObject("weekly");String key=key(r);
  s.append(lane("Session",a,key)).append("\n").append(lane("Weekly",b,key));
  String extra=r.optString("extra","");if(!extra.isEmpty())s.append("\n").append(extra);
  String err=r.optString("error","");if(!err.isEmpty())s.append("\n").append(err);
  long updated=r.optLong("updated",0);
  if("claude".equals(r.optString("provider")) && updated>0 && (!err.isEmpty() || System.currentTimeMillis()-updated>30*60*1000L))s.append("\nStale since ").append(DateTimeFormatter.ofPattern("MMM d HH:mm",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(updated))).append(" · open Claude to refresh");
  s.append("\n").append(age(updated));return s.toString();
 }
 static int progress(JSONObject r){return left(r.optJSONObject("session"),key(r));}
}
