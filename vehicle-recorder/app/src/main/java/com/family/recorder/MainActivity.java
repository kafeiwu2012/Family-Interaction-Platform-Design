package com.family.recorder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private final Handler ui=new Handler();
    private final ExecutorService stats=Executors.newSingleThreadExecutor();
    private TextureView video;
    private TextView status,format,name,storage,lock;
    private Button record,menu,orientation,scaling;
    private ScrollView panel;
    private boolean visible,dim,pendingStart,expanded;
    private int updates;
    private final Runnable collapse=()->setExpanded(false);
    private final Runnable tick=new Runnable(){public void run(){
        long seconds=RecorderService.recording?(SystemClock.elapsedRealtime()-RecorderService.started)/1000:0;
        status.setText(String.format(Locale.CHINA,"%s  %02d:%02d",RecorderService.recording?"● REC":"○ 待机",seconds/60,seconds%60));status.setTextColor(RecorderService.recording?Ui.RED:Ui.MUTED);if(!RecorderService.active)status.setText("○ "+RecorderService.status);
        menu.setText(expanded?"×\n收起":"☰\n菜单");menu.setContentDescription(expanded?"收起操作菜单":"展开操作菜单");
        format.setText(RecorderService.format+"   ·   "+(portrait()?"竖屏":"横屏")+"\n"+RecorderService.status);
        name.setText(RecorderService.currentName.isEmpty()?"打开软件自动录制 · 熄屏持续运行":RecorderService.currentName);
        lock.setText(RecorderService.protectedClip?"◇ 当前片段已保护":"循环录像 · 未锁定旧片段自动覆盖");lock.setTextColor(RecorderService.protectedClip?Ui.GREEN:Ui.MUTED);
        record.setText(RecorderService.active?"■ 停止保存":"● 开始录像");record.setTextColor(RecorderService.active?Ui.RED:Ui.GREEN);
        orientation.setText(portrait()?"切换为横屏":"切换为竖屏");scaling.setText(Config.prefs(MainActivity.this).getBoolean("preview_fill",true)?"显示完整画面":"预览铺满屏幕");
        if(expanded&&updates++%5==0){final java.io.File dir=Config.directory(MainActivity.this);final long quota=Config.quota(MainActivity.this);stats.execute(()->{
            long used=LoopStore.used(dir),free=dir.getUsableSpace();ui.post(()->{if(!isFinishing())storage.setText(String.format(Locale.CHINA,"循环空间 %.0f / %.0f MB  ·  可用 %.1f GB",used/1048576.0,quota/1048576.0,free/1073741824.0));});
        });}
        ui.postDelayed(this,1000);
    }};
    private final Runnable autoStart=()->{if(!RecorderService.active)startWithPermissions();};
    @Override public void onCreate(Bundle state){super.onCreate(state);
        setRequestedOrientation(portrait()?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        Ui.fullscreen(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Ui.BG);setContentView(root);
        video=new TextureView(this);video.setSurfaceTextureListener(this);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        status=Ui.text(this,"正在准备录像…",12,Ui.RED);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);status.setPadding(d(12),0,d(12),0);status.setBackground(Ui.shape(0xBB0B1220,d(14)));FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,d(32),Gravity.TOP|Gravity.START);hp.setMargins(d(14),d(16),d(80),0);root.addView(status,hp);
        panel=new ScrollView(this);panel.setFillViewport(false);panel.setBackground(Ui.shape(0xF20B1220,d(18)));panel.setVisibility(View.GONE);root.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.END));
        LinearLayout content=Ui.column(this);content.setPadding(d(16),d(14),d(16),d(14));panel.addView(content);
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);content.addView(title);
        ImageView mark=new ImageView(this);mark.setImageResource(R.mipmap.ic_launcher);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(d(30),d(30));ip.setMargins(0,0,d(9),0);title.addView(mark,ip);title.addView(Ui.heading(this,"行车记录仪"));
        format=Ui.text(this,"",12,Ui.MUTED);format.setPadding(0,d(8),0,d(8));content.addView(format);
        LinearLayout row=controlsRow(content);
        record=action(row,"● 开始录像",Ui.GREEN,()->{if(RecorderService.active){ui.removeCallbacks(autoStart);Ui.command(this,"stop");}else startWithPermissions();});
        action(row,"◇ 保护片段",Ui.GREEN,()->{if(RecorderService.recording)Ui.command(this,"lock");else Ui.toast(this,"正在准备录像或已停止");});
        row=controlsRow(content);action(row,"⇄ 切换镜头",Ui.TEXT,()->{if(RecorderService.active)Ui.command(this,"switch");else{int facing=Config.prefs(this).getInt("facing",0)==0?1:0;Config.prefs(this).edit().putInt("facing",facing).apply();RecorderService.status="已选择"+(facing==0?"后摄像头":"前摄像头");}});
        orientation=action(row,"切换为竖屏",Ui.TEXT,this::switchOrientation);
        row=controlsRow(content);action(row,"录像库",Ui.TEXT,()->{setExpanded(false);startActivity(new Intent(this,ClipsActivity.class));});action(row,"设置",Ui.TEXT,()->{setExpanded(false);SettingsPanel.show(this);});
        row=controlsRow(content);scaling=action(row,"显示完整画面",Ui.TEXT,()->{boolean fill=!Config.prefs(this).getBoolean("preview_fill",true);Config.prefs(this).edit().putBoolean("preview_fill",fill).apply();RecorderService.refreshPreviewStyle();Ui.toast(this,fill?"预览已铺满，边缘可能裁切；录像保留完整画面":"已显示完整画面，可能留黑边");});
        action(row,"暗屏",Ui.TEXT,()->{dim=!dim;WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=dim?.01f:-1;getWindow().setAttributes(p);setExpanded(false);Ui.toast(this,"按电源键真正熄屏，录像保持运行");});
        storage=Ui.text(this,"展开菜单时更新存储统计",11,Ui.MUTED);storage.setPadding(0,d(8),0,0);content.addView(storage);
        name=Ui.text(this,"",11,Ui.TEXT);content.addView(name);lock=Ui.text(this,"",11,Ui.MUTED);content.addView(lock);
        TextView help=Ui.text(this,"8 秒无操作自动收起 · 横竖屏切换会保存片段后继续",10,Ui.MUTED);help.setPadding(0,d(8),0,0);content.addView(help);
        menu=Ui.button(this,"☰\n菜单",Ui.GREEN,()->setExpanded(!expanded));menu.setTextSize(12);menu.setElevation(d(8));GradientDrawable badge=Ui.shape(0xF2172338,d(26));badge.setStroke(d(2),Ui.GREEN);menu.setBackground(badge);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(d(52),d(52),Gravity.TOP|Gravity.END);bp.setMargins(0,d(10),d(12),0);root.addView(menu,bp);
        updateLayout();publishDisplay();
        if(state==null&&!RecorderService.active&&Config.prefs(this).getBoolean("auto_start",true))ui.postDelayed(autoStart,350);
    }
    private boolean portrait(){return "portrait".equals(Config.prefs(this).getString("layout_mode","landscape"));}
    private int d(float v){return Ui.dp(this,v);}
    private LinearLayout controlsRow(LinearLayout parent){LinearLayout row=new LinearLayout(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,d(46));p.setMargins(0,d(5),0,0);parent.addView(row,p);return row;}
    private Button action(LinearLayout parent,String text,int color,Runnable run){Button b=Ui.button(this,text,color,()->{scheduleCollapse();run.run();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(d(3),0,d(3),0);parent.addView(b,p);return b;}
    private void setExpanded(boolean show){expanded=show;panel.setVisibility(show?View.VISIBLE:View.GONE);menu.setText(show?"×\n收起":"☰\n菜单");menu.setContentDescription(show?"收起操作菜单":"展开操作菜单");updates=0;if(show)scheduleCollapse();else ui.removeCallbacks(collapse);}
    private void scheduleCollapse(){ui.removeCallbacks(collapse);if(expanded)ui.postDelayed(collapse,8000);}
    private void switchOrientation(){boolean nextPortrait=!portrait();Config.prefs(this).edit().putString("layout_mode",nextPortrait?"portrait":"landscape").apply();setExpanded(false);setRequestedOrientation(nextPortrait?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);ui.postDelayed(()->{updateLayout();publishDisplay();},250);}
    private void updateLayout(){int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;status.setMaxWidth(width-d(100));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.min(width-d(28),d(400)),Math.max(d(130),Math.min(d(440),height-d(90))),Gravity.BOTTOM|Gravity.END);p.setMargins(d(14),d(70),d(14),d(14));panel.setLayoutParams(p);}
    private void publishDisplay(){RecorderService.setDisplayRotation(OrientationMath.displayDegrees(getWindowManager().getDefaultDisplay().getRotation()));}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);Ui.fullscreen(this);updateLayout();publishDisplay();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus){Ui.fullscreen(this);publishDisplay();}}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){if(expanded&&(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN||event.getActionMasked()==android.view.MotionEvent.ACTION_MOVE||event.getActionMasked()==android.view.MotionEvent.ACTION_UP))scheduleCollapse();return super.dispatchTouchEvent(event);}
    @Override public void onBackPressed(){if(expanded)setExpanded(false);else super.onBackPressed();}
    private void startWithPermissions(){pendingStart=true;if(permissions()){pendingStart=false;publishDisplay();Ui.command(this,"start");}}
    private boolean permissions(){if(Build.VERSION.SDK_INT<23)return true;ArrayList<String> needed=new ArrayList<>();for(String p:new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)needed.add(p);if(Config.prefs(this).getBoolean("audio",true)&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.RECORD_AUDIO);if(needed.isEmpty())return true;requestPermissions(needed.toArray(new String[0]),10);return false;}
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] grants){super.onRequestPermissionsResult(request,p,grants);if(request!=10){Ui.toast(this,"麦克风权限处理完成，请再次保存设置");return;}boolean granted=grants.length>0;for(int g:grants)granted&=g==PackageManager.PERMISSION_GRANTED;if(granted&&pendingStart){pendingStart=false;publishDisplay();Ui.command(this,"start");}else{pendingStart=false;RecorderService.status="权限不足，请授权或在设置中关闭录音";Ui.toast(this,RecorderService.status);}}
    @Override protected void onResume(){super.onResume();visible=true;Ui.fullscreen(this);if(expanded)scheduleCollapse();publishDisplay();if(video.isAvailable())attach(video.getSurfaceTexture(),video.getWidth(),video.getHeight());ui.post(tick);}
    @Override protected void onPause(){visible=false;RecorderService.setPreviewSurface(null,0,0);ui.removeCallbacks(tick);ui.removeCallbacks(collapse);super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);stats.shutdownNow();super.onDestroy();}
    private void attach(SurfaceTexture t,int w,int h){if(visible){publishDisplay();RecorderService.setPreviewSurface(new Surface(t),w,h);}}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){attach(t,w,h);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){attach(t,w,h);}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){RecorderService.setPreviewSurface(null,0,0);return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t){}
}
