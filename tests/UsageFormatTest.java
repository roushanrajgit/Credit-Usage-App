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
  eq(UsageFormat.reset(new JSONObject().put("resets_at","2026-11-17T12:00:00.000000+00:00")),1794916800000L);
  eq(UsageFormat.reset(new JSONObject().put("resets_at","2026-11-17T17:30:00+05:30")),1794916800000L);
  eq(UsageFormat.reset(new JSONObject().put("resets_at","unknown")),0L);
  eq(UsageFormat.progress(new JSONObject().put("provider","claude")),-1);
  eq(UsageFormat.progress(new JSONObject().put("provider","codex").put("session",new JSONObject().put("used_percent",40))),60);
  if(!UsageFormat.lane("Weekly",new JSONObject().put("limit_window_seconds",2592000),"used_percent").startsWith("30-day window"))throw new AssertionError("wrong window label");
  // Codex: same account under its id and "default" → one entry; null name falls back to structure.
  JSONObject personal=new JSONObject().put("account_id","acc-1").put("name",JSONObject.NULL).put("structure","personal").put("plan_type","plus");
  JSONObject team=new JSONObject().put("account_id","acc-2").put("name","Acme").put("structure","workspace");
  JSONObject accounts=new JSONObject()
   .put("acc-1",new JSONObject().put("account",personal))
   .put("default",new JSONObject().put("account",new JSONObject(personal.toString())))
   .put("acc-2",new JSONObject().put("account",team))
   .put("old",new JSONObject().put("account",new JSONObject().put("account_id","acc-3").put("is_deactivated",true)))
   .put("broken",new JSONObject().put("account",new JSONObject().put("account_id",JSONObject.NULL)));
  java.util.List<JSONObject> found=UsageFormat.codexAccounts(accounts);
  eq(found.size(),2);
  eq(UsageFormat.codexName(personal),"Personal");
  eq(UsageFormat.codexName(team),"Acme");
  eq(UsageFormat.codexName(new JSONObject()),"Account");
  eq(UsageFormat.str(personal,"name"),"");
  System.out.println((checks+1)+" usage formatting checks passed");
 }
}
