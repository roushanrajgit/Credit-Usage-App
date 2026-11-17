package com.usagewidget;
import android.content.Context;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Small builders for the programmatic app screens (cards and terminal styles). */
final class Ui {
 static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
 static TextView text(Context c,CharSequence s,float sp,int color,boolean bold,boolean mono){
  TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
  if(mono)t.setTypeface(Typeface.MONOSPACE,bold?Typeface.BOLD:Typeface.NORMAL);else if(bold)t.setTypeface(t.getTypeface(),Typeface.BOLD);
  return t;
 }
 static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
 static LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(-2,-2);}
 static LinearLayout.LayoutParams fill(){return new LinearLayout.LayoutParams(-1,-2);}
 static LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
 static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p,Context c,int l,int t,int r,int b){p.setMargins(dp(c,l),dp(c,t),dp(c,r),dp(c,b));return p;}
 static void pad(View v,int dp){int p=dp(v.getContext(),dp);v.setPadding(p,p,p,p);}
 /** Upper-case section label, e.g. "ACCOUNTS". */
 static TextView section(Context c,String s){TextView t=text(c,s,11,Render.MUTED,false,false);t.setLetterSpacing(0.05f);t.setPadding(dp(c,2),dp(c,16),0,dp(c,8));return t;}
 /** Provider logo on a tinted rounded tile. */
 static FrameLayout logo(Context c,JSONObject r,int sizeDp){
  FrameLayout f=new FrameLayout(c);f.setBackgroundResource(Render.tile(r));
  ImageView i=new ImageView(c);i.setImageResource(Render.logo(r));i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  int inner=dp(c,sizeDp*0.58f);f.addView(i,new FrameLayout.LayoutParams(inner,inner,Gravity.CENTER));
  f.setLayoutParams(new LinearLayout.LayoutParams(dp(c,sizeDp),dp(c,sizeDp)));
  return f;
 }
 static FrameLayout logo(Context c,String provider,int sizeDp){
  try{return logo(c,new JSONObject().put("provider",provider),sizeDp);}catch(org.json.JSONException e){throw new IllegalStateException(e);}
 }
 static TextView pill(Context c,String s,int color,int bg){
  TextView t=text(c,s,10,color,false,false);t.setBackgroundResource(bg);
  t.setPadding(dp(c,7),dp(c,1),dp(c,7),dp(c,1));t.setSingleLine(true);return t;
 }
 /** Make any view a tappable row with ripple feedback. */
 static void tappable(View v,Runnable r){
  android.util.TypedValue tv=new android.util.TypedValue();
  if(v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground,tv,true)&&v.getBackground()==null)v.setBackgroundResource(tv.resourceId);
  v.setClickable(true);v.setFocusable(true);v.setOnClickListener(x->r.run());
 }
}
