package com.usagewidget;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Ring gauge showing percent remaining, coloured green/amber/red. */
final class GaugeView extends View {
 private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG),arc=new Paint(Paint.ANTI_ALIAS_FLAG),label=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final RectF box=new RectF();
 private int left=-1;
 GaugeView(Context c,int left){
  super(c);this.left=left;
  float d=c.getResources().getDisplayMetrics().density;
  track.setStyle(Paint.Style.STROKE);track.setStrokeWidth(6*d);track.setColor(Render.TRACK);
  arc.setStyle(Paint.Style.STROKE);arc.setStrokeWidth(6*d);arc.setStrokeCap(Paint.Cap.ROUND);arc.setColor(Render.levelColor(left));
  label.setColor(Render.TEXT);label.setTextAlign(Paint.Align.CENTER);label.setTextSize(13*c.getResources().getDisplayMetrics().scaledDensity);label.setFakeBoldText(true);
  setContentDescription(left<0?"Unavailable":left+" percent remaining");
 }
 @Override protected void onDraw(Canvas c){
  float inset=track.getStrokeWidth()/2+1;
  box.set(inset,inset,getWidth()-inset,getHeight()-inset);
  c.drawArc(box,0,360,false,track);
  if(left>0)c.drawArc(box,-90,360f*left/100f,false,arc);
  String t=left<0?"—":left+"%";
  Paint.FontMetrics fm=label.getFontMetrics();
  c.drawText(t,getWidth()/2f,getHeight()/2f-(fm.ascent+fm.descent)/2,label);
 }
}
