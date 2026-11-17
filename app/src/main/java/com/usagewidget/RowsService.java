package com.usagewidget;
import android.content.*;
import android.widget.*;
import android.view.View;
import org.json.*;
public class RowsService extends RemoteViewsService {
 public RemoteViewsFactory onGetViewFactory(Intent i){return new RemoteViewsFactory(){
  JSONArray rows=new JSONArray();boolean terminal;
  void load(){Context c=getApplicationContext();rows=Store.visibleRows(c);terminal=Store.terminal(c);}
  public void onCreate(){load();}public void onDataSetChanged(){load();}public void onDestroy(){}
  public int getCount(){return rows.length();}
  public RemoteViews getViewAt(int n){
   JSONObject r=rows.optJSONObject(n);if(r==null)return null;
   if(terminal){RemoteViews v=new RemoteViews(getPackageName(),R.layout.row_terminal);v.setTextViewText(R.id.line,Render.terminal(r));return v;}
   return card(r);
  }
  RemoteViews card(JSONObject r){
   RemoteViews v=new RemoteViews(getPackageName(),R.layout.row);
   v.setInt(R.id.logoTile,"setBackgroundResource",Render.tile(r));
   v.setImageViewResource(R.id.logo,Render.logo(r));
   v.setTextViewText(R.id.name,r.optString("name"));
   String badge=UsageFormat.badge(r),plan=UsageFormat.plan(r.optString("plan"));
   if(!badge.isEmpty()){v.setViewVisibility(R.id.pill,View.VISIBLE);v.setTextViewText(R.id.pill,badge);v.setTextColor(R.id.pill,Render.WARN_SOFT);v.setInt(R.id.pill,"setBackgroundResource",R.drawable.pill_stale);}
   else if(!plan.isEmpty()){v.setViewVisibility(R.id.pill,View.VISIBLE);v.setTextViewText(R.id.pill,plan);v.setTextColor(R.id.pill,Render.brandSoft(r));v.setInt(R.id.pill,"setBackgroundResource",Render.claude(r)?R.drawable.pill_claude:R.drawable.pill_codex);}
   else v.setViewVisibility(R.id.pill,View.GONE);
   String key=UsageFormat.key(r);
   lane(v,R.id.lane1,R.id.l1label,R.id.l1value,new int[]{R.id.l1good,R.id.l1warn,R.id.l1low},"5-hour",r.optJSONObject("session"),key);
   lane(v,R.id.lane2,R.id.l2label,R.id.l2value,new int[]{R.id.l2good,R.id.l2warn,R.id.l2low},"Weekly",r.optJSONObject("weekly"),key);
   String extra=r.optString("extra","");
   v.setViewVisibility(R.id.extra,extra.isEmpty()?View.GONE:View.VISIBLE);
   if(!extra.isEmpty()){v.setTextViewText(R.id.extra,extra);v.setTextColor(R.id.extra,Render.brandSoft(r));}
   String reason=UsageFormat.reason(r);
   v.setViewVisibility(R.id.note,reason.isEmpty()?View.GONE:View.VISIBLE);
   if(!reason.isEmpty())v.setTextViewText(R.id.note,reason);
   return v;
  }
  /** One lane: label, "72% left · 14:30", and a bar in green/amber/red (three pre-tinted bars; only one visible). */
  void lane(RemoteViews v,int laneId,int labelId,int valueId,int[] bars,String label,JSONObject w,String key){
   if(w==null){v.setViewVisibility(laneId,View.GONE);return;}
   v.setViewVisibility(laneId,View.VISIBLE);
   int left=UsageFormat.left(w,key);String reset=UsageFormat.resetShort(w);
   v.setTextViewText(labelId,UsageFormat.laneLabel(label,w));
   v.setTextViewText(valueId,(left<0?"Unavailable":left+"% left")+(reset.isEmpty()?"":" · "+reset));
   v.setTextColor(valueId,left<0?Render.MUTED:Render.TEXT);
   int level=UsageFormat.level(left);
   for(int k=0;k<3;k++){
    boolean show=(k==0&&level==UsageFormat.LEVEL_GOOD)||(k==1&&level==UsageFormat.LEVEL_WARN)||(k==2&&level==UsageFormat.LEVEL_LOW);
    v.setViewVisibility(bars[k],show?View.VISIBLE:View.GONE);
    if(show)v.setProgressBar(bars[k],100,left,false);
   }
  }
  public RemoteViews getLoadingView(){return null;}public int getViewTypeCount(){return 2;}public long getItemId(int p){return p;}public boolean hasStableIds(){return false;}
 };}
}
