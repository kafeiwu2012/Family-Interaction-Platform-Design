package com.family.recorder;
import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
final class Ui {
    static final int BG=0xFF0B1220,CARD=0xFF172338,TEXT=0xFFF0F5FF,MUTED=0xFFA3B4CC,GREEN=0xFF42E3BD,RED=0xFFFF616B;
    static int dp(Context c,float v){return (int)(v*c.getResources().getDisplayMetrics().density+.5f);}
    static GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    static LinearLayout column(Context c){LinearLayout p=new LinearLayout(c);p.setOrientation(LinearLayout.VERTICAL);return p;}
    static TextView heading(Context c,String value){TextView t=text(c,value,16,TEXT);t.setTypeface(null,Typeface.BOLD);return t;}
    static Button button(Context c,String value,int color,Runnable action){Button b=new Button(c);b.setText(value);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(color);b.setGravity(Gravity.CENTER);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(c,8),dp(c,4),dp(c,8),dp(c,4));b.setBackground(shape(CARD,dp(c,12)));b.setOnClickListener(v->action.run());return b;}
    static void fullscreen(Activity a){a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);}
    static void command(Context c,String action){android.content.Intent i=new android.content.Intent(c,RecorderService.class).setAction(action);if(android.os.Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
    static void toast(Context c,String value){Toast.makeText(c,value,Toast.LENGTH_LONG).show();}
}
