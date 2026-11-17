package com.usagewidget;
import android.content.*;
import android.widget.*;
import android.view.View;
import org.json.*;
public class RowsService extends RemoteViewsService {
 public RemoteViewsFactory onGetViewFactory(Intent i){return new RemoteViewsFactory(){
  JSONArray rows=new JSONArray();public void onCreate(){rows=Store.rows(getApplicationContext());}public void onDataSetChanged(){rows=Store.rows(getApplicationContext());}public void onDestroy(){}
  public int getCount(){return rows.length();}public RemoteViews getViewAt(int n){JSONObject r=rows.optJSONObject(n);if(r==null)return null;RemoteViews v=new RemoteViews(getPackageName(),R.layout.row);v.setTextViewText(R.id.name,r.optString("name")+ (r.optString("plan").isEmpty()?"":" · "+r.optString("plan")));v.setTextViewText(R.id.detail,UsageFormat.detail(r));int progress=UsageFormat.progress(r);v.setViewVisibility(R.id.bar,progress<0?View.GONE:View.VISIBLE);if(progress>=0)v.setProgressBar(R.id.bar,100,progress,false);return v;}
  public RemoteViews getLoadingView(){return null;}public int getViewTypeCount(){return 1;}public long getItemId(int p){return p;}public boolean hasStableIds(){return false;}
 };}
}
