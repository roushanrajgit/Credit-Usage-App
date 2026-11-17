package com.usagewidget;
import android.content.*;
import org.json.*;
import java.util.*;

/**
 * Local settings and cached readings. Only the main process writes here; login processes (":c2", ":x3", ...)
 * report through SyncProvider, because SharedPreferences are not shared safely between processes.
 */
final class Store {
 static final String STYLE_CARDS="cards",STYLE_TERMINAL="terminal";
 static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("usage",0); }
 static JSONArray rows(Context c) { try{return new JSONArray(prefs(c).getString("rows","[]"));}catch(Exception e){return new JSONArray();} }
 static void save(Context c,JSONArray rows){prefs(c).edit().putString("rows",rows.toString()).apply();}

 // ---- Per-login keys: slot 1 keeps the original key names ("claudeConnected"), slot N appends N. ----
 static String key(String provider,String field,int slot){return provider+field+(slot<=1?"":String.valueOf(slot));}
 static boolean connected(Context c,String provider,int slot){return prefs(c).getBoolean(key(provider,"Connected",slot),false);}
 static List<Integer> slots(Context c,String provider){List<Integer> out=new ArrayList<>();for(int s=1;s<=Profiles.maxSlots();s++)if(connected(c,provider,s))out.add(s);return out;}
 /** First slot without a login, or -1 when all are used. */
 static int freeSlot(Context c,String provider){for(int s=1;s<=Profiles.maxSlots();s++)if(!connected(c,provider,s))return s;return -1;}
 static String email(Context c,String provider,int slot){return prefs(c).getString(key(provider,"Email",slot),"");}
 /** Name shown for a login: your rename, else its email, else "Claude account 2". */
 static String label(Context c,String provider,int slot){
  String l=prefs(c).getString(key(provider,"Label",slot),"");if(!l.isEmpty())return l;
  String e=email(c,provider,slot);if(!e.isEmpty())return e;
  return Profiles.title(provider)+" account "+slot;
 }
 static void setLabel(Context c,String provider,int slot,String label){prefs(c).edit().putString(key(provider,"Label",slot),label==null?"":label.trim()).apply();}
 static String loginStatus(Context c,String provider,int slot){return prefs(c).getString(key(provider,"Status",slot),"");}
 static int rowSlot(JSONObject r){return Math.max(1,r.optInt("slot",1));}

 /** Remove one login's flags, names and cached rows (its sign-in data is wiped separately). */
 static void removeLogin(Context c,String provider,int slot){
  synchronized(UsageClient.class){
   JSONArray all=rows(c),keep=new JSONArray();
   for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r!=null&&!(provider.equals(r.optString("provider"))&&rowSlot(r)==slot))keep.put(r);}
   save(c,keep);
   prefs(c).edit().remove(key(provider,"Connected",slot)).remove(key(provider,"Email",slot)).remove(key(provider,"Label",slot)).remove(key(provider,"Status",slot)).apply();
  }
 }

 /** Display style shared by the app and the widget. Defaults to cards. */
 static String style(Context c){return STYLE_TERMINAL.equals(prefs(c).getString("style",STYLE_CARDS))?STYLE_TERMINAL:STYLE_CARDS;}
 static boolean terminal(Context c){return STYLE_TERMINAL.equals(style(c));}
 static void setStyle(Context c,String style){prefs(c).edit().putString("style",STYLE_TERMINAL.equals(style)?STYLE_TERMINAL:STYLE_CARDS).apply();}

 /** Account ids (e.g. "claude:<uuid>", "codex:<id>") the user chose to hide. New accounts are visible by default. */
 static Set<String> hidden(Context c){return new HashSet<>(prefs(c).getStringSet("hidden",Collections.<String>emptySet()));}
 static void setHidden(Context c,String id,boolean hide){
  Set<String> ids=hidden(c);
  if(hide)ids.add(id);else ids.remove(id);
  prefs(c).edit().putStringSet("hidden",ids).apply();
 }
 /**
  * Each workspace once per person: the same account signed in twice appears a single time, using the most recent
  * successful reading. Sorted by provider, then login slot.
  */
 static JSONArray accounts(Context c){
  JSONArray all=rows(c);Map<String,JSONObject> best=new LinkedHashMap<>();
  for(int i=0;i<all.length();i++){
   JSONObject r=all.optJSONObject(i);if(r==null)continue;
   // Usage is per person, so only merge the same workspace seen by the same person (same email). Two
   // different people sharing a Team workspace keep separate cards.
   String provider=r.optString("provider");int slot=rowSlot(r);String who=email(c,provider,slot);
   String k=r.optString("id")+"|"+(who.isEmpty()?"slot"+slot:who.toLowerCase(Locale.US));
   JSONObject prev=best.get(k);
   if(prev==null||r.optLong("updated",0)>prev.optLong("updated",0))best.put(k,r);
  }
  List<JSONObject> list=new ArrayList<>(best.values());
  Collections.sort(list,(a,b)->{int p=a.optString("provider").compareTo(b.optString("provider"));return p!=0?p:Integer.compare(rowSlot(a),rowSlot(b));});
  JSONArray out=new JSONArray();for(JSONObject r:list)out.put(r);return out;
 }
 /** Accounts the user has not hidden. Hidden accounts are still fetched and cached, only not shown. */
 static JSONArray visibleRows(Context c){
  Set<String> hide=hidden(c);JSONArray all=accounts(c),out=new JSONArray();
  for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r!=null&&!hide.contains(r.optString("id")))out.put(r);}
  return out;
 }
 static int hiddenCount(Context c){return accounts(c).length()-visibleRows(c).length();}
}
