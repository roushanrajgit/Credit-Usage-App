package com.usagewidget;
import android.content.*;
import org.json.*;
final class Store {
 static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("usage",0); }
 static JSONArray rows(Context c) { try{return new JSONArray(prefs(c).getString("rows","[]"));}catch(Exception e){return new JSONArray();} }
 static void save(Context c,JSONArray rows){prefs(c).edit().putString("rows",rows.toString()).apply();}
 static String status(Context c){String base=prefs(c).getString("status","Connect accounts to get started");String claude=prefs(c).getString("claudeStatus",prefs(c).getBoolean("claudeConnected",false)?"Claude: open connection to sync usage":"");return base+(claude.isEmpty()?"":"\n"+claude);}
}
