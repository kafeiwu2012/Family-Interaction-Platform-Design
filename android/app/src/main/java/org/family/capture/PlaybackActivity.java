package org.family.capture;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** A file descriptor lets the media service read app-private recordings on Android 5.1. */
public class PlaybackActivity extends LightActivity implements SurfaceHolder.Callback {
    private MediaPlayer player;
    private SurfaceView surface;
    private TextView info;
    private Button toggle;
    private SeekBar seek;
    private File file;
    private boolean visible,ready,seeking;
    private int position;
    private final Handler handler=new Handler();
    private final Runnable prepareTimeout=()->{if(player!=null&&!ready)fail("视频准备超时。请导出该MP4核验文件，或记录错误后重试。");};
    private final Runnable update=new Runnable(){public void run(){
        if(player!=null&&ready){try{
            if(!seeking)seek.setProgress(player.getCurrentPosition());
            info.setText(clock(player.getCurrentPosition())+" / "+clock(player.getDuration()));
            toggle.setText(player.isPlaying()?"暂停":"播放");
        }catch(IllegalStateException ignored){}}
        if(visible)handler.postDelayed(this,500);
    }};
    static String clock(int millis){int s=millis/1000;return String.format(java.util.Locale.US,"%02d:%02d",s/60,s%60);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);setTitle("视频播放");
        if(state!=null)position=state.getInt("position",0);
        String name=getIntent().getStringExtra("name");
        if(name==null||!name.equals(new File(name).getName())||!name.endsWith(".mp4")){finish();return;}
        file=new File(new File(getFilesDir(),"local-photos"),name);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(12,12,12,12);
        TextView title=new TextView(this);title.setText(MediaNames.display(file));title.setTextSize(12);root.addView(title);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(android.graphics.Color.BLACK);
        surface=new SurfaceView(this);frame.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));surface.getHolder().addCallback(this);
        frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->fit());
        info=new TextView(this);info.setText("正在准备视频…");root.addView(info);
        seek=new SeekBar(this);root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){seeking=true;}
            public void onStopTrackingTouch(SeekBar s){seeking=false;if(player!=null&&ready)try{player.seekTo(s.getProgress());}catch(IllegalStateException ignored){}}
            public void onProgressChanged(SeekBar s,int p,boolean fromUser){}
        });
        LinearLayout controls=new LinearLayout(this);
        toggle=new Button(this);toggle.setText("播放");toggle.setEnabled(false);controls.addView(toggle,new LinearLayout.LayoutParams(0,-2,1));
        toggle.setOnClickListener(v->{if(player!=null&&ready)try{if(player.isPlaying())player.pause();else{if(player.getCurrentPosition()>=player.getDuration()-100)player.seekTo(0);player.start();}}catch(IllegalStateException e){fail("播放状态异常，请返回重试");}});
        Button back=new Button(this);back.setText("返回列表");controls.addView(back,new LinearLayout.LayoutParams(0,-2,1));back.setOnClickListener(v->finish());root.addView(controls);setContentView(root);
    }
    private void prepare(){
        if(!visible||player!=null||!surface.getHolder().getSurface().isValid())return;
        try{
            player=new MediaPlayer();final MediaPlayer current=player;ready=false;toggle.setEnabled(false);info.setText("正在准备视频…");
            player.setDisplay(surface.getHolder());
            // Passing a descriptor avoids asking mediaserver to open a private /data/data path.
            try(FileInputStream in=new FileInputStream(file)){player.setDataSource(in.getFD(),0,file.length());}
            player.setOnPreparedListener(p->{if(p!=player)return;handler.removeCallbacks(prepareTimeout);if(p.getDuration()<=0){fail("视频时长无效，片段可能损坏；文件已保留，可导出核验。");return;}ready=true;seek.setMax(p.getDuration());toggle.setEnabled(true);p.seekTo(position);p.start();fit();});
            player.setOnVideoSizeChangedListener((p,w,h)->fit());
            player.setOnCompletionListener(p->{toggle.setText("重播");position=0;});
            player.setOnErrorListener((p,what,extra)->{if(p==current)fail("无法播放：错误 "+what+" / "+extra+"。请导出该MP4在其他播放器核验；若仍失败，片段可能损坏或编码不兼容。");return true;});
            player.prepareAsync();handler.postDelayed(prepareTimeout,20000);
        }catch(Exception e){fail("打开视频失败："+e.getClass().getSimpleName()+"。文件仍保留，可批量导出排查。");}
    }
    private void fit(){
        if(player==null||!ready)return;
        try{int w=player.getVideoWidth(),h=player.getVideoHeight();View parent=(View)surface.getParent();int pw=parent.getWidth(),ph=parent.getHeight();if(w<=0||h<=0||pw<=0||ph<=0)return;
            float ratio=Math.min((float)pw/w,(float)ph/h);FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)surface.getLayoutParams();int width=Math.max(1,(int)(w*ratio)),height=Math.max(1,(int)(h*ratio));if(p.width!=width||p.height!=height){p.width=width;p.height=height;p.gravity=Gravity.CENTER;surface.setLayoutParams(p);}
        }catch(IllegalStateException ignored){}
    }
    private void fail(String text){release();info.setText(text);}
    private void release(){handler.removeCallbacks(prepareTimeout);ready=false;toggle.setEnabled(false);if(player!=null){MediaPlayer old=player;player=null;old.setOnErrorListener(null);old.setOnPreparedListener(null);old.setDisplay(null);old.release();}}
    @Override public void surfaceCreated(SurfaceHolder holder){prepare();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){savePosition();release();}
    private void savePosition(){if(player!=null&&ready)try{position=player.getCurrentPosition();}catch(IllegalStateException ignored){}}
    @Override protected void onResume(){super.onResume();visible=true;prepare();handler.post(update);}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(update);savePosition();release();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){savePosition();out.putInt("position",position);super.onSaveInstanceState(out);}
}
