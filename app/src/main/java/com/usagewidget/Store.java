package com.usagewidget;
import android.content.*;
import org.json.*;
import java.util.*;
final class Store {
 static final String STYLE_CARDS="cards",STYLE_TERMINAL="terminal";
 static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("usage",0); }
 static JSONArray rows(Context c) { try{return new JSONArray(prefs(c).getString("rows","[]"));}catch(Exception e){return new JSONArray();} }
 static void save(Context c,JSONArray rows){prefs(c).edit().putString("rows",rows.toString()).apply();}
 static String status(Context c){String base=prefs(c).getString("status","Connect accounts to get started");String claude=prefs(c).getString("claudeStatus",prefs(c).getBoolean("claudeConnected",false)?"Claude: open connection to sync usage":"");return base+(claude.isEmpty()?"":"\n"+claude);}

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
 /** Rows the user has not hidden. Hidden accounts are still fetched and cached, only not shown. */
 static JSONArray visibleRows(Context c){
  Set<String> hide=hidden(c);JSONArray all=rows(c),out=new JSONArray();
  for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r!=null&&!hide.contains(r.optString("id")))out.put(r);}
  return out;
 }
 static int hiddenCount(Context c){return rows(c).length()-visibleRows(c).length();}
}
