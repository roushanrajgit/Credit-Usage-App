package com.usagewidget;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.*;
public class UsageWidget extends AppWidgetProvider {
 public void onUpdate(Context c,AppWidgetManager m,int[] ids){RefreshJob.schedule(c);update(c);}
 public void onReceive(Context c,Intent i){super.onReceive(c,i);if("com.usagewidget.REFRESH".equals(i.getAction())){Store.prefs(c).edit().putString("status","Refresh queued · waiting for network / Android").apply();update(c);RefreshJob.now(c);}}
 static void update(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);for(int id:m.getAppWidgetIds(new ComponentName(c,UsageWidget.class))){
  RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);
  v.setTextViewText(R.id.status,Store.status(c));
  Intent service=new Intent(c,RowsService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);service.setData(android.net.Uri.parse("usagewidget://rows/"+id));v.setRemoteAdapter(R.id.rows,service);v.setEmptyView(R.id.rows,R.id.empty);
  PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  v.setOnClickPendingIntent(R.id.title,open);v.setOnClickPendingIntent(R.id.empty,open);
  v.setOnClickPendingIntent(R.id.refresh,PendingIntent.getActivity(c,1,new Intent(c,MainActivity.class).putExtra("refresh",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
  m.updateAppWidget(id,v);m.notifyAppWidgetViewDataChanged(id,R.id.rows);
 }}
}
