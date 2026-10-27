package com.family.recorder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private final Handler ui=new Handler();
    private final ExecutorService stats=Executors.newSingleThreadExecutor();
    private final SimpleDateFormat time=new SimpleDateFormat("yyyy.MM.dd  HH:mm:ss",Locale.CHINA);
    private TextureView video;
    private TextView status,clock,format,name,storage,elapsed,lock;
    private Button record;
    private boolean visible,dim,pendingStart;
    private int updates;
    private final Runnable tick=new Runnable(){public void run(){
        clock.setText(time.format(new Date()));
        long seconds=RecorderService.recording?(SystemClock.elapsedRealtime()-RecorderService.started)/1000:0;
        status.setText((RecorderService.recording?"●  REC   ":"○  ")+RecorderService.status);status.setTextColor(RecorderService.recording?Ui.RED:Ui.MUTED);
        elapsed.setText(String.format(Locale.CHINA,"%02d:%02d",seconds/60,seconds%60));
        format.setText(RecorderService.format+"   •   GPU 实时预览");
        name.setText(RecorderService.currentName.isEmpty()?"启动后自动录制 · 熄屏持续运行":RecorderService.currentName);
        lock.setText(RecorderService.protectedClip?"当前片段已保护":"循环录制 · 自动覆盖最旧未锁定片段");
        lock.setTextColor(RecorderService.protectedClip?Ui.GREEN:Ui.MUTED);
        record.setText(RecorderService.active?"■\n停止保存":"●\n开始录像");record.setTextColor(RecorderService.active?Ui.RED:Ui.GREEN);
        if(updates++%5==0){final java.io.File dir=Config.directory(MainActivity.this);final long quota=Config.quota(MainActivity.this);stats.execute(()->{
            long used=LoopStore.used(dir),free=dir.getUsableSpace();ui.post(()->{if(!isFinishing())storage.setText(String.format(Locale.CHINA,"循环空间  %.0f / %.0f MB   ·   磁盘可用 %.1f GB",used/1048576.0,quota/1048576.0,free/1073741824.0));});
        });}
        ui.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state){super.onCreate(state);Ui.fullscreen(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Ui.BG);setContentView(root);
        video=new TextureView(this);video.setSurfaceTextureListener(this);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(d(18),d(10),d(18),d(10));top.setBackground(Ui.shape(0xD90B1220,0));root.addView(top,new FrameLayout.LayoutParams(-1,d(58),Gravity.TOP));
        LinearLayout brand=Ui.column(this);top.addView(brand,new LinearLayout.LayoutParams(0,-1,1));brand.addView(Ui.heading(this,"行车记录仪"));brand.addView(Ui.text(this,"ROAD RECORDER  /  R7sm",10,Ui.MUTED));
        LinearLayout info=Ui.column(this);info.setGravity(Gravity.END);top.addView(info);clock=Ui.text(this,"",14,Ui.TEXT);info.addView(clock);format=Ui.text(this,"GPU 实时预览",11,Ui.GREEN);info.addView(format);
        LinearLayout hud=new LinearLayout(this);hud.setGravity(Gravity.CENTER_VERTICAL);hud.setPadding(d(12),d(6),d(12),d(6));hud.setBackground(Ui.shape(0xBD0B1220,d(12)));FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,d(38),Gravity.TOP|Gravity.START);hp.setMargins(d(18),d(72),d(120),0);root.addView(hud,hp);
        status=Ui.text(this,"正在准备录像…",12,Ui.RED);hud.addView(status);elapsed=Ui.text(this,"00:00",16,Ui.TEXT);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-2,-1);ep.setMargins(d(14),0,0,0);hud.addView(elapsed,ep);
        LinearLayout controls=Ui.column(this);controls.setPadding(d(8),d(8),d(8),d(8));controls.setBackground(Ui.shape(0xDB0B1220,d(18)));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(d(94),-1,Gravity.END);cp.setMargins(0,d(70),d(12),d(92));root.addView(controls,cp);
        record=control(controls,"●\n开始录像",Ui.GREEN,()->{if(RecorderService.active)Ui.command(this,"stop");else startWithPermissions();});
        control(controls,"⇄  切换",Ui.TEXT,()->{if(RecorderService.active)Ui.command(this,"switch");else{int f=Config.prefs(this).getInt("facing",0)==0?1:0;Config.prefs(this).edit().putInt("facing",f).apply();RecorderService.status="已选择"+(f==0?"后摄像头":"前摄像头");}});
        control(controls,"◇  保护",Ui.GREEN,()->{if(RecorderService.recording)Ui.command(this,"lock");else Ui.toast(this,"正在准备录像或已停止");});
        LinearLayout bottom=Ui.column(this);bottom.setPadding(d(18),d(9),d(14),d(9));bottom.setBackground(Ui.shape(0xEC0B1220,0));root.addView(bottom,new FrameLayout.LayoutParams(-1,d(80),Gravity.BOTTOM));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);bottom.addView(row,new LinearLayout.LayoutParams(-1,d(32)));
        storage=Ui.text(this,"正在读取存储空间…",12,Ui.TEXT);row.addView(storage,new LinearLayout.LayoutParams(0,-1,1));
        action(row,"录像库",()->startActivity(new Intent(this,ClipsActivity.class)));action(row,"设置",()->SettingsPanel.show(this));action(row,"暗屏",()->{dim=!dim;WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=dim?.01f:-1;getWindow().setAttributes(p);Ui.toast(this,"按电源键可真正熄屏，录像保持运行");});
        name=Ui.text(this,"",11,Ui.TEXT);bottom.addView(name);lock=Ui.text(this,"",10,Ui.MUTED);bottom.addView(lock);
        if(state==null&&!RecorderService.active&&Config.prefs(this).getBoolean("auto_start",true))startWithPermissions();
    }
    private int d(float value){return Ui.dp(this,value);}
    private Button control(LinearLayout row,String text,int color,Runnable run){Button b=Ui.button(this,text,color,run);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);p.setMargins(0,d(3),0,d(3));row.addView(b,p);return b;}
    private void action(LinearLayout row,String text,Runnable run){Button b=Ui.button(this,text,Ui.TEXT,run);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(d(68),d(30));p.setMargins(d(6),0,0,0);row.addView(b,p);}
    private void startWithPermissions(){pendingStart=true;if(permissions()){pendingStart=false;Ui.command(this,"start");}}
    private boolean permissions(){
        if(Build.VERSION.SDK_INT<23)return true;ArrayList<String> needed=new ArrayList<>();
        for(String p:new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)needed.add(p);
        if(Config.prefs(this).getBoolean("audio",true)&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.RECORD_AUDIO);
        if(needed.isEmpty())return true;requestPermissions(needed.toArray(new String[0]),10);return false;
    }
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] grants){super.onRequestPermissionsResult(request,p,grants);if(request!=10){Ui.toast(this,"麦克风权限处理完成，请再次保存设置");return;}boolean granted=grants.length>0;for(int g:grants)granted&=g==PackageManager.PERMISSION_GRANTED;if(granted&&pendingStart){pendingStart=false;Ui.command(this,"start");}else{pendingStart=false;RecorderService.status="权限不足，请授权或在设置中关闭录音";Ui.toast(this,RecorderService.status);}}
    @Override protected void onResume(){super.onResume();visible=true;Ui.fullscreen(this);if(video.isAvailable())attach(video.getSurfaceTexture(),video.getWidth(),video.getHeight());ui.post(tick);}
    @Override protected void onPause(){visible=false;RecorderService.setPreviewSurface(null,0,0);ui.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);stats.shutdownNow();super.onDestroy();}
    private void attach(SurfaceTexture t,int w,int h){if(visible)RecorderService.setPreviewSurface(new Surface(t),w,h);}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){attach(t,w,h);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){attach(t,w,h);}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){RecorderService.setPreviewSurface(null,0,0);return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t){}
}
