package com.usagewidget;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
public class RefreshJob extends JobService {
 private Future<?> task;private final ExecutorService executor=Executors.newSingleThreadExecutor();
 public boolean onStartJob(JobParameters p){task=executor.submit(()->{UsageClient.refresh(getApplicationContext());jobFinished(p,false);});return true;}
 public boolean onStopJob(JobParameters p){if(task!=null)task.cancel(true);return true;}
 public void onDestroy(){executor.shutdownNow();super.onDestroy();}
 static void schedule(Context c){JobScheduler js=c.getSystemService(JobScheduler.class);if(Store.prefs(c).getBoolean("auto",true))js.schedule(new JobInfo.Builder(30,new ComponentName(c,RefreshJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30*60*1000L).setPersisted(true).build());else js.cancel(30);}
 static void now(Context c){c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(31,new ComponentName(c,RefreshJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setOverrideDeadline(0).build());}
}
