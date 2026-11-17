package com.usagewidget;
import android.app.Application;
import android.os.Build;
import android.webkit.WebView;

/** Gives each login process (":c2", ":x3", ...) its own WebView storage before any WebView or cookie use. */
public class App extends Application {
 @Override public void onCreate(){
  super.onCreate();
  if(Build.VERSION.SDK_INT>=28){
   String name=getProcessName();int i=name==null?-1:name.indexOf(':');
   if(i>=0)WebView.setDataDirectorySuffix(name.substring(i+1));
  }
 }
}
