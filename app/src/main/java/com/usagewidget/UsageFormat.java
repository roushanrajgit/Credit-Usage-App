package com.usagewidget;
import org.json.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
final class UsageFormat {
 static String remaining(JSONObject w,String key){
  if(w==null||w.isNull(key))return "Unavailable";
  double n=w.optDouble(key,Double.NaN);
  if(!Double.isFinite(n)||n<0||n>100)return "Unavailable";
  return String.format(Locale.US,"%.0f%% remaining",100-n);
 }
 static long reset(JSONObject w){if(w==null)return 0;
  if(w.optLong("reset_at",0)>0)return w.optLong("reset_at")*1000;
  // OffsetDateTime accepts both "...Z" and "...+00:00"; Instant.parse rejects offsets on Android 13 and older.
  try{return OffsetDateTime.parse(w.getString("resets_at")).toInstant().toEpochMilli();}catch(Exception e){return 0;}
 }
 static String resetText(JSONObject w){long t=reset(w);if(t==0)return "reset unavailable";
  if(t<=System.currentTimeMillis())return "reset passed · refresh needed";
  return "resets "+DateTimeFormatter.ofPattern("EEE HH:mm",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(t));
 }
 static String lane(String label,JSONObject w,String key){
  long seconds=w==null?0:w.optLong("limit_window_seconds",0);
  if(seconds>0){if(seconds==604800)label="Weekly";else if(seconds==18000)label="Session (5h)";else if(seconds%86400==0)label=(seconds/86400)+"-day window";else label=(seconds/3600)+"h window";}
  return label+": "+remaining(w,key)+" · "+resetText(w);}
 static String age(long at){if(at==0)return "never updated";long m=Math.max(0,(System.currentTimeMillis()-at)/60000);return m<1?"updated just now":"updated "+m+"m ago";}
 static String detail(JSONObject r){StringBuilder s=new StringBuilder();
  JSONObject a=r.optJSONObject("session"),b=r.optJSONObject("weekly");String key=r.optString("provider").equals("claude")?"utilization":"used_percent";
  s.append(lane("Session",a,key)).append("\n").append(lane("Weekly",b,key));
  String extra=r.optString("extra","");if(!extra.isEmpty())s.append("\n").append(extra);
  String err=r.optString("error","");if(!err.isEmpty())s.append("\n").append(err);
  long updated=r.optLong("updated",0);
  if("claude".equals(r.optString("provider")) && updated>0 && (!err.isEmpty() || System.currentTimeMillis()-updated>30*60*1000L))s.append("\nStale since ").append(DateTimeFormatter.ofPattern("MMM d HH:mm",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(updated))).append(" · open Claude to refresh");
  s.append("\n").append(age(updated));return s.toString();
 }
 static int progress(JSONObject r){JSONObject a=r.optJSONObject("session");if(a==null)return -1;String k=r.optString("provider").equals("claude")?"utilization":"used_percent";double n=a.optDouble(k,Double.NaN);return Double.isFinite(n)&&n>=0&&n<=100?(int)Math.round(100-n):-1;}
}
