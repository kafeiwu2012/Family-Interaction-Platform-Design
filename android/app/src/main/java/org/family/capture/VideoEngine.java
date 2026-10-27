package org.family.capture;
import android.content.Context;
import android.hardware.camera2.*;
import android.media.*;
import android.os.Handler;
import android.util.Size;
import java.io.File;
import java.util.*;

/** Silent, bounded recording. All calls and callbacks use the camera Handler. */
final class VideoEngine {
    final Context context; final Handler handler;
    CameraDevice device; CameraCaptureSession session; MediaRecorder recorder;
    boolean active,started; int generation;
    long startedAt,endedAt;
    File output,temp; CameraEngine.Result callback; Runnable deadline,stop;
    VideoEngine(Context c,Handler h){context=c;handler=h;}
    boolean busy(){return active;}
    void record(String facing,File file,int seconds,CameraEngine.Result result){
        if(active){result.done(false,"video busy");return;}
        active=true;started=false;startedAt=0;endedAt=0;output=file;temp=new File(file.getPath()+".part");callback=result;final int id=++generation;
        deadline=()->finish(false,"录像超时");handler.postDelayed(deadline,(seconds+25)*1000L);
        try{
            CameraManager m=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);String selected=null;
            int desired="front".equals(facing)?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK;
            for(String camera:m.getCameraIdList())if(Integer.valueOf(desired).equals(m.getCameraCharacteristics(camera).get(CameraCharacteristics.LENS_FACING))){selected=camera;break;}
            if(selected==null)throw new IllegalStateException("camera unavailable");
            CameraCharacteristics c=m.getCameraCharacteristics(selected);
            Size[] sizes=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(MediaRecorder.class);
            if(sizes==null||sizes.length==0)throw new IllegalStateException("video unavailable");
            Arrays.sort(sizes,(a,b)->Long.compare((long)a.getWidth()*a.getHeight(),(long)b.getWidth()*b.getHeight()));
            Size size=sizes[0];for(Size s:sizes)if(s.getWidth()<=640&&s.getHeight()<=480)size=s;
            recorder=new MediaRecorder();recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setOutputFile(temp.getPath());
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);recorder.setVideoSize(size.getWidth(),size.getHeight());
            recorder.setVideoFrameRate(15);recorder.setVideoEncodingBitRate(1500000);
            recorder.setOrientationHint(c.get(CameraCharacteristics.SENSOR_ORIENTATION));recorder.prepare();
            m.openCamera(selected,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice d){
                    if(!active||id!=generation){d.close();return;}device=d;
                    try{d.createCaptureSession(Collections.singletonList(recorder.getSurface()),new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession s){
                            if(!active||id!=generation){s.close();return;}session=s;
                            try{CaptureRequest.Builder b=d.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);b.addTarget(recorder.getSurface());
                                b.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                                s.setRepeatingRequest(b.build(),null,handler);recorder.start();started=true;startedAt=android.os.SystemClock.elapsedRealtime();
                                stop=()->finish(true,"录像已保存");handler.postDelayed(stop,seconds*1000L);
                            }catch(Exception e){finish(false,e.toString());}
                        }
                        public void onConfigureFailed(CameraCaptureSession s){s.close();if(id==generation)finish(false,"录像会话失败");}
                    },handler);}catch(Exception e){finish(false,e.toString());}
                }
                public void onDisconnected(CameraDevice d){d.close();if(id==generation)finish(false,"相机断开");}
                public void onError(CameraDevice d,int code){d.close();if(id==generation)finish(false,"camera error "+code);}
            },handler);
        }catch(Exception e){finish(false,e.toString());}
    }
    void close(){if(active)finish(false,"录像已中止，丢弃未完成片段");}
    private void finish(boolean ok,String detail){
        if(!active)return;active=false;endedAt=android.os.SystemClock.elapsedRealtime();
        if(deadline!=null)handler.removeCallbacks(deadline);if(stop!=null)handler.removeCallbacks(stop);
        if(session!=null){try{session.stopRepeating();}catch(Exception ignored){}session.close();session=null;}
        if(recorder!=null){
            if(started)try{recorder.stop();}catch(Exception e){ok=false;detail="录像停止失败";}
            recorder.reset();recorder.release();recorder=null;
        }
        if(device!=null){device.close();device=null;}
        if(ok){
            MediaMetadataRetriever r=new MediaMetadataRetriever();
            try{r.setDataSource(temp.getPath());String duration=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if(duration==null||Long.parseLong(duration)<500)throw new IllegalStateException("invalid video");
                try(java.io.RandomAccessFile sync=new java.io.RandomAccessFile(temp,"rw")){sync.getFD().sync();}
                if(!temp.renameTo(output))throw new IllegalStateException("rename failed");
            }catch(Exception e){ok=false;detail="录像文件校验失败";}finally{try{r.release();}catch(Exception ignored){}}
        }
        if(!ok)temp.delete();CameraEngine.Result result=callback;callback=null;result.done(ok,detail);
    }
}
