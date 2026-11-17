package com.usagewidget;
import org.json.*;
public class UsageFormatTest {
 static int checks;
 static void eq(Object actual,Object expected){checks++;if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args)throws Exception {
  eq(UsageFormat.remaining(null,"used_percent"),"Unavailable");
  eq(UsageFormat.remaining(new JSONObject(),"used_percent"),"Unavailable");
  eq(UsageFormat.remaining(new JSONObject().put("used_percent",0),"used_percent"),"100% remaining");
  eq(UsageFormat.remaining(new JSONObject().put("used_percent",100),"used_percent"),"0% remaining");
  eq(UsageFormat.remaining(new JSONObject().put("utilization",35),"utilization"),"65% remaining");
  eq(UsageFormat.remaining(new JSONObject().put("utilization",-1),"utilization"),"Unavailable");
  eq(UsageFormat.remaining(new JSONObject().put("utilization",101),"utilization"),"Unavailable");
  eq(UsageFormat.reset(new JSONObject().put("resets_at","2026-11-17T12:00:00Z")),1794916800000L);
  eq(UsageFormat.reset(new JSONObject().put("reset_at",1794916800L)),1794916800000L);
  eq(UsageFormat.reset(new JSONObject().put("resets_at","unknown")),0L);
  eq(UsageFormat.progress(new JSONObject().put("provider","claude")),-1);
  eq(UsageFormat.progress(new JSONObject().put("provider","codex").put("session",new JSONObject().put("used_percent",40))),60);
  if(!UsageFormat.lane("Weekly",new JSONObject().put("limit_window_seconds",2592000),"used_percent").startsWith("30-day window"))throw new AssertionError("wrong window label");
  System.out.println((checks+1)+" usage formatting checks passed");
 }
}
