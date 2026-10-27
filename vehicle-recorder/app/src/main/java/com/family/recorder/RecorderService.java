package com.family.recorder;

import android.app.*;
import android.content.*;
import android.view.Surface;
import android.hardware.Camera;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.*;

// Permission requests are centralized in the non-exported Activity-to-Service entry.
@android.annotation.SuppressLint("MissingPermission")
@SuppressWarnings("deprecation")
public final class RecorderService extends Service {
    static volatile boolean recording;
    static volatile String status="准备就绪";
    static volatile boolean active;
    static volatile String currentName="", format="720p", previewMode="GPU";
    private static volatile RecorderService instance;
    private static volatile int displayDegrees=90;
    static void setDisplayRotation(int degrees){
        int next=OrientationMath.normalize(degrees);if(next==displayDegrees)return;displayDegrees=next;
        RecorderService service=instance;if(service!=null)service.work.post(()->{
            service.work.removeCallbacks(service.reorient);
            if(service.running)service.work.postDelayed(service.reorient,500);
        });
    }
    static void refreshPreviewStyle(){RecorderService service=instance;if(service!=null)service.work.post(()->{if(service.preview!=null)service.preview.setStyle(service.clipRotation,Config.prefs(service).getBoolean("preview_fill",true));});}
    private static Surface displaySurface;
    private static int displayWidth,displayHeight;
    static synchronized void setPreviewSurface(Surface surface,int width,int height){
        Surface previous=displaySurface;displaySurface=surface;displayWidth=width;displayHeight=height;
        RecorderService service=instance;
        if(service!=null){service.work.post(()->{
            if(service.preview!=null)service.preview.setOutput(displaySurface,displayWidth,displayHeight);
            if(previous!=null&&previous!=displaySurface)previous.release();
        });}else if(previous!=null&&previous!=surface)previous.release();
    }
    static volatile long started;
    static volatile boolean protectedClip;
    private HandlerThread thread;
    private Handler work;
    private Camera camera;
    private MediaRecorder recorder;
    private OffscreenPreview preview;
    private PowerManager.WakeLock wake;
    private File current;
    private boolean running;
    private long generation;
    private int facing,sensorRotation,clipRotation;
    private File recordingDirectory;
    private String saveWarning;
    private final Runnable diskGuard=new Runnable(){public void run(){
        if(!running)return;
        if(recordingDirectory.getUsableSpace()<16L*1024*1024){shutdown("存储剩余不足 16 MB，已停止并保存");return;}
        work.postDelayed(this,2000);
    }};
    private int desiredRotation(){return OrientationMath.rotation(sensorRotation,facing==Camera.CameraInfo.CAMERA_FACING_FRONT,displayDegrees,Config.prefs(this).getInt("rotation",0));}
    private final Runnable reorient=new Runnable(){public void run(){
        if(!running||desiredRotation()==clipRotation)return;
        long remaining=1500-(SystemClock.elapsedRealtime()-started);
        if(remaining>0){work.postDelayed(this,remaining);return;}
        finishClip();beginClip();
    }};
    private final Runnable rotate=() -> { if(running) { finishClip(); beginClip(); } };
    @Override public void onCreate() {
        super.onCreate(); thread=new HandlerThread("vehicle-camera");thread.start();work=new Handler(thread.getLooper());instance=this;
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VehicleRecorder:recording");
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel("recording","录像状态",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        startForeground(7,notification());
        final String action=intent==null?"stop":intent.getAction();
        work.post(() -> {
            if("start".equals(action) && !running) {
                active=true;running=true; facing=Config.prefs(this).getInt("facing",Camera.CameraInfo.CAMERA_FACING_BACK);wake.acquire();beginClip();
            } else if("stop".equals(action)) shutdown("已停止");
            else if("switch".equals(action) && running) {
                facing=facing==0?1:0; finishClip();Config.prefs(this).edit().putInt("facing",facing).apply(); beginClip();
            } else if("configure".equals(action)) {
                boolean resume=running;
                Bundle values=intent.getBundleExtra("settings");
                Map<String,Object> next=new HashMap<>();if(values!=null)for(String key:values.keySet())next.put(key,values.get(key));
                boolean restart=resume&&SettingsPolicy.requiresRestart(Config.recordingValues(this),next);
                if(restart)finishClip();
                android.content.SharedPreferences.Editor editor=Config.prefs(this).edit();
                if(values!=null)for(String key:values.keySet()){
                    Object value=values.get(key);if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
                }
                editor.apply();
                if(resume){if(restart)beginClip();else if(preview!=null)preview.setFrameRate(Config.prefs(this).getInt("preview_fps",30));}else{status="设置已保存";stopForeground(true);stopSelf(id);}
            } else if("lock".equals(action) && current!=null) {
                try { if(!new File(current.getPath()+".lock").createNewFile() && !LoopStore.locked(current)) throw new IOException("无法锁定"); protectedClip=true;status="录像中 · 当前片段已锁定"; }
                catch(IOException e) { status="锁定失败："+e.getMessage(); }
            }
        });return START_NOT_STICKY;
    }
    private Notification notification() {
        Intent stop=new Intent(this,RecorderService.class).setAction("stop");
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent end=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"recording"):new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_stat_recorder).setContentTitle("行车记录仪正在运行").setContentText("熄屏后继续录像 · 点击返回").setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"停止并保存",end).build();
    }
    private void beginClip() {
        if(!running)return;
        try {
            final long clipGeneration=++generation;
            long reserve=Config.reserve(this);
            File dir=Config.directory(this);recordingDirectory=dir; LoopStore.prepare(dir,Config.quota(this),reserve,64L*1024*1024);
            int selected=-1;
            Camera.CameraInfo info=new Camera.CameraInfo();
            for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==facing){selected=i;break;}}
            if(selected<0)throw new IOException("没有可用的目标摄像头");
            camera=Camera.open(selected);Camera.getCameraInfo(selected,info);sensorRotation=info.orientation;clipRotation=desiredRotation();
            camera.setErrorCallback((error,c)-> work.post(()->{if(generation==clipGeneration)shutdown("摄像头异常："+error);}));
            Camera.Parameters p=camera.getParameters();
            if(p.getSupportedFocusModes()!=null && p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            if(p.isVideoStabilizationSupported())p.setVideoStabilization(Config.prefs(this).getBoolean("stabilize",true));
            if(p.getSupportedFlashModes()!=null && p.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_TORCH))
                p.setFlashMode(Config.prefs(this).getBoolean("torch",false)?Camera.Parameters.FLASH_MODE_TORCH:Camera.Parameters.FLASH_MODE_OFF);
            int quality=Config.prefs(this).getBoolean("hd",false)?CamcorderProfile.QUALITY_1080P:CamcorderProfile.QUALITY_720P;
            if(!CamcorderProfile.hasProfile(selected,quality))quality=CamcorderProfile.QUALITY_LOW;
            CamcorderProfile profile=CamcorderProfile.get(selected,quality);
            List<Camera.Size> sizes=p.getSupportedPreviewSizes();Camera.Size best=sizes.get(0);
            double aspect=profile.videoFrameWidth/(double)profile.videoFrameHeight;
            for(Camera.Size s:sizes) if(Math.abs(s.width/(double)s.height-aspect)<Math.abs(best.width/(double)best.height-aspect) || (Math.abs(s.width/(double)s.height-aspect)==Math.abs(best.width/(double)best.height-aspect)&&Math.abs(s.width-640)<Math.abs(best.width-640)))best=s;
            p.setPreviewSize(best.width,best.height);
            int fps=Config.prefs(this).getInt("fps",30);fps=Math.min(fps,profile.videoFrameRate);
            int[] range=null;for(int[] r:p.getSupportedPreviewFpsRange()){
                if(r[0]<=fps*1000&&r[1]>=fps*1000&&(range==null||(r[1]-r[0])<(range[1]-range[0])))range=r;
            }
            if(range!=null)p.setPreviewFpsRange(range[0],range[1]);
            p.setRecordingHint(true);camera.setParameters(p);
            preview=new OffscreenPreview(work,best.width,best.height,clipRotation,Config.prefs(this).getInt("preview_fps",30));
            preview.setStyle(clipRotation,Config.prefs(this).getBoolean("preview_fill",true));
            preview.setOutput(displaySurface,displayWidth,displayHeight);
            camera.setPreviewTexture(preview.input);camera.startPreview();camera.unlock();
            recorder=new MediaRecorder();recorder.setCamera(camera);
            boolean audio=Config.prefs(this).getBoolean("audio",true);
            if(audio)recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);recorder.setVideoSize(profile.videoFrameWidth,profile.videoFrameHeight);
            recorder.setVideoFrameRate(fps);recorder.setVideoEncodingBitRate(Config.bitrate(this));
            if(audio){recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioSamplingRate(44100);recorder.setAudioChannels(1);recorder.setAudioEncodingBitRate(96000);}
            // Rotation is immutable within an MP4 segment; manual display changes start a new segment.
            recorder.setOrientationHint(clipRotation);
            current=ClipNames.next(dir,facing==Camera.CameraInfo.CAMERA_FACING_FRONT,System.currentTimeMillis());
            currentName=current.getName().replace(".part",".mp4");
            recorder.setOutputFile(current.getAbsolutePath());recorder.setMaxDuration(Config.seconds(this)*1000);
            recorder.setMaxFileSize(reserve);
            recorder.setOnInfoListener((m,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)work.post(()->{if(running && generation==clipGeneration)rotate.run();});});
            recorder.setOnErrorListener((m,what,extra)->work.post(()->{if(generation==clipGeneration)shutdown("编码异常："+what);}));
            recorder.prepare();recorder.start();recording=true;protectedClip=false;started=SystemClock.elapsedRealtime();
            format=(clipRotation%180==0?profile.videoFrameWidth:profile.videoFrameHeight)+"×"+(clipRotation%180==0?profile.videoFrameHeight:profile.videoFrameWidth)+" / "+fps+"fps";
            status="录像中 · "+(facing==0?"后摄像头":"前摄像头");
            work.postDelayed(rotate,Config.seconds(this)*1000L+1000);work.removeCallbacks(diskGuard);work.postDelayed(diskGuard,2000);
        } catch(Exception e){shutdown("录制停止："+e.getMessage());}
    }
    private void finishClip() {
        saveWarning=null; generation++; work.removeCallbacks(reorient); work.removeCallbacks(diskGuard); work.removeCallbacks(rotate); boolean valid=false;
        if(recorder!=null){recorder.setOnInfoListener(null);recorder.setOnErrorListener(null);try{recorder.stop();valid=true;}catch(RuntimeException e){status=saveWarning="片段过短或编码失败，当前片段未保存";}recorder.release();recorder=null;}
        if(camera!=null){try{camera.reconnect();camera.stopPreview();}catch(Exception ignored){}camera.release();camera=null;}
        if(preview!=null){preview.close();preview=null;}
        if(current!=null){
            File marker=new File(current.getPath()+".lock");
            if(valid){File dest=new File(current.getParentFile(),current.getName().replace(".part",".mp4"));
            boolean safe=true;
            if(marker.exists())try{File targetMarker=new File(dest.getPath()+".lock");safe=targetMarker.exists()||targetMarker.createNewFile();}catch(IOException e){safe=false;}
            if(safe && current.renameTo(dest)){
                marker.delete();
                MediaScannerConnection.scanFile(this,new String[]{dest.getPath()},new String[]{"video/mp4"},null);
            }else status=saveWarning="保存重命名失败，原片段及锁标记保留";
            }else{current.delete();marker.delete();}current=null;
        }
        recording=false;protectedClip=false;
    }
    private void shutdown(String message){running=false;active=false;finishClip();status=saveWarning==null?message:message+"；"+saveWarning;if(wake.isHeld())wake.release();stopForeground(true);stopSelf();}
    @Override public void onDestroy(){if(instance==this)instance=null;work.post(()->{running=false;active=false;finishClip();if(wake.isHeld())wake.release();thread.quitSafely();});super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
