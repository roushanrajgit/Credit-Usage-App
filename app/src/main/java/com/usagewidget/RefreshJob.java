package com.usagewidget;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

/** Codex background refresh. Slot 1 runs here; slots 2-3 run the subclasses in Slots, each in its own process. */
public class RefreshJob extends JobService {
 private Future<?> task;private final ExecutorService executor=Executors.newSingleThreadExecutor();
 public boolean onStartJob(JobParameters p){final int slot=Profiles.currentSlot();task=executor.submit(()->{UsageClient.fetch(getApplicationContext(),slot);jobFinished(p,false);});return true;}
 public boolean onStopJob(JobParameters p){if(task!=null)task.cancel(true);return true;}
 public void onDestroy(){executor.shutdownNow();super.onDestroy();}

 // Job ids: periodic 30/32/34 and one-shot 31/33/35 for Codex slots 1/2/3.
 static int periodicId(int slot){return 28+slot*2;}
 static int nowId(int slot){return 29+slot*2;}
 static ComponentName component(Context c,int slot){return new ComponentName(c,Profiles.codexJob(slot));}

 /** Main process: keep one periodic job per connected Codex login (slot 1 always, as before). */
 static void schedule(Context c){
  JobScheduler js=c.getSystemService(JobScheduler.class);boolean auto=Store.prefs(c).getBoolean("auto",true);
  for(int slot=1;slot<=Profiles.MAX;slot++){
   boolean wanted=auto&&slot<=Profiles.maxSlots()&&(slot==1||Store.connected(c,"codex",slot));
   if(wanted)js.schedule(new JobInfo.Builder(periodicId(slot),component(c,slot)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30*60*1000L).setPersisted(true).build());
   else js.cancel(periodicId(slot));
  }
 }
 /** Main process: refresh every connected Codex login now (slot 1 always, as before). */
 static void now(Context c){for(int slot=1;slot<=Profiles.maxSlots();slot++)if(slot==1||Store.connected(c,"codex",slot))now(c,slot);}
 static void now(Context c,int slot){c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(nowId(slot),component(c,slot)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setOverrideDeadline(0).build());}
}
