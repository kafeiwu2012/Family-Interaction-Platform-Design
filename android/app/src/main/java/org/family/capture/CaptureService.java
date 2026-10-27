package org.family.capture;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class CaptureService extends Service {
    private HandlerThread thread;
    private Handler handler;
    private CameraEngine camera;
    private PowerManager.WakeLock wake;
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private volatile boolean running=false;
    private volatile String status="启动中", facing="back";
    private volatile boolean enabled=true;
    private volatile int seconds=60;
    private String base,token;
    private File queue;
    private boolean localMode;
    private long nextCapture=0;
    private static final long MAX_BYTES=256L*1024*1024;
    @Override public void onCreate() {
        super.onCreate();
        thread=new HandlerThread("camera"); thread.start(); handler=new Handler(thread.getLooper()); camera=new CameraEngine(this,handler);
        queue=new File(getFilesDir(),"queue"); queue.mkdirs();
        File[] parts=queue.listFiles((d,n)->n.endsWith(".part")); if(parts!=null) for(File f:parts) f.delete();
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"FamilyCapture:active");
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(running) return START_NOT_STICKY;
        SharedPreferences p=getSharedPreferences("config",0); base=p.getString("url",""); token=p.getString("token",""); seconds=p.getInt("interval",60);
        localMode=p.getBoolean("local",true);
        facing=p.getString("facing","back");
        queue=new File(getFilesDir(),localMode?"local-photos":"queue"); queue.mkdirs();
        if(seconds<10 || seconds>86400) seconds=60;
        if(!localMode && (!base.startsWith("https://") || token.length()<32)) { stopSelf(); return START_NOT_STICKY; }
        running=true; startForeground(1,notification("定时相机运行中")); wake.setReferenceCounted(false); wake.acquire(10*60*1000L);
        notifyStatus(localMode?"本地测试已启动，不上传":"上传模式已启动");
        handler.post(tick); if(!localMode) network.execute(this::networkLoop);
        return START_NOT_STICKY;
    }
    private Notification notification(String text) {
        Intent stop=new Intent(this,CaptureService.class).setAction("STOP");
        PendingIntent action=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("家庭定时相机 · "+facing)
            .setContentText(text).setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"停止",action).build();
    }
    private void notifyStatus(String text) {
        status=text;
        if(running) getSharedPreferences("runtime",0).edit().putString("status",(localMode?"本地测试 · ":"上传模式 · ")+text+" · "+new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.US).format(new java.util.Date())).apply();
        if(running) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1,notification(text));
    }
    private long queuedBytes() { long total=0; File[] files=queue.listFiles(); if(files!=null) for(File f:files) total+=f.length(); return total; }
    private final Runnable tick=new Runnable(){ public void run() {
        if(!running) return;
        // Renew a bounded wake lock only while this visible foreground service runs.
        wake.acquire(10*60*1000L);
        if(localMode) facing=getSharedPreferences("config",0).getString("facing","back");
        long now=SystemClock.elapsedRealtime();
        if(enabled && now>=nextCapture) {
            nextCapture=now+seconds*1000L;
            if(queuedBytes()>MAX_BYTES || queue.getUsableSpace()<32L*1024*1024) notifyStatus(localMode?"本地空间不足，暂停拍摄；请删除测试照片":"存储不足，暂停拍摄；等待上传释放空间");
            else {
                String selected=facing;
                File out=new File(queue,System.currentTimeMillis()+"_"+selected+"_"+UUID.randomUUID()+".jpg");
                camera.capture(selected,out,(ok,detail)->notifyStatus((ok?(localMode?"已保存本地 ":"已拍摄 "):"拍摄失败 ")+selected+" · "+detail));
            }
        }
        handler.postDelayed(this,1000);
    }};
    private HttpURLConnection connection(String path,String method) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
        c.setInstanceFollowRedirects(false); c.setRequestMethod(method); c.setConnectTimeout(10000); c.setReadTimeout(15000);
        c.setRequestProperty("Authorization","Bearer "+token); return c;
    }
    private String read(InputStream in) throws IOException {
        try(InputStream stream=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[4096]; int n; while((n=stream.read(buf))!=-1) { if(out.size()+n>65536) throw new IOException("response too large"); out.write(buf,0,n); }
            return out.toString("UTF-8");
        }
    }
    private void poll() throws Exception {
        HttpURLConnection c=connection("/api/config","GET");
        try {
            if(c.getResponseCode()!=200) throw new IOException("config HTTP "+c.getResponseCode());
            JSONObject j=new JSONObject(read(c.getInputStream())); String requested=j.getString("camera");
            if(!requested.equals("front") && !requested.equals("back")) throw new IOException("invalid camera");
            int interval=j.getInt("interval"); if(interval<10 || interval>86400) throw new IOException("invalid interval");
            facing=requested; enabled=j.getBoolean("enabled"); seconds=interval;
        } finally { c.disconnect(); }
    }
    private void upload(File file) throws Exception {
        HttpURLConnection c=connection("/api/upload/"+file.getName(),"PUT");
        try {
            c.setDoOutput(true); c.setRequestProperty("Content-Type","image/jpeg"); c.setFixedLengthStreamingMode(file.length());
            try(InputStream in=new FileInputStream(file); OutputStream out=c.getOutputStream()) {
                byte[] b=new byte[32768]; int n; while(running && (n=in.read(b))!=-1) out.write(b,0,n);
                if(!running) throw new IOException("stopped");
            }
            if(c.getResponseCode()!=201) throw new IOException("upload HTTP "+c.getResponseCode());
            if(!file.delete()) throw new IOException("local delete failed");
        } finally { c.disconnect(); }
    }
    private void heartbeat() throws Exception {
        HttpURLConnection c=connection("/api/status","POST");
        try {
            JSONObject j=new JSONObject(); j.put("camera",facing); j.put("enabled",enabled); j.put("interval",seconds);
            j.put("status",status); j.put("queue_bytes",queuedBytes()); j.put("capabilities",CameraEngine.report(this));
            byte[] body=j.toString().getBytes("UTF-8"); c.setDoOutput(true); c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()) { out.write(body); }
            if(c.getResponseCode()!=200) throw new IOException("status HTTP "+c.getResponseCode());
        } finally { c.disconnect(); }
    }
    private void networkLoop() {
        int failures=0;
        while(running) {
            try {
                poll(); heartbeat();
                File[] files=queue.listFiles((d,n)->n.endsWith(".jpg"));
                if(files!=null) {
                    Arrays.sort(files);
                    for(int i=0;running && i<Math.min(files.length,3);i++) upload(files[i]);
                }
                failures=0;
            } catch(Exception e) { failures=Math.min(failures+1,6); notifyStatus("网络异常，保留文件待补传 · "+e.getClass().getSimpleName()); }
            try { Thread.sleep(failures==0?5000:Math.min(60000,1000L*(1<<failures))); }
            catch(InterruptedException e) { return; }
        }
    }
    @Override public void onDestroy() {
        getSharedPreferences("runtime",0).edit().putString("status","已停止").apply();
        running=false; handler.removeCallbacksAndMessages(null);
        handler.post(()->{ camera.close(); thread.quitSafely(); });
        network.shutdownNow(); if(wake.isHeld()) wake.release(); stopForeground(true); super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
