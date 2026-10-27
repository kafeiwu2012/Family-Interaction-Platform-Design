package org.family.capture;
import android.content.Context;
import android.hardware.Camera;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.*;

/** API22 legacy-camera recorder. Runs on the same single camera Handler as still capture. */
@SuppressWarnings("deprecation")
final class VideoEngine {
    final Handler handler;Camera camera;MediaRecorder recorder;OffscreenPreview preview;
    boolean active,started;File output,temp;CameraEngine.Result callback;
    Runnable stop;String stage="";String config="";
    VideoEngine(Context context,Handler handler){this.handler=handler;}
    void record(String facing,File file,int seconds,CameraEngine.Result result){
        if(active){result.done(false,"录像繁忙");return;}active=true;started=false;output=file;temp=new File(file.getPath()+".part");callback=result;
        try{
            stage="打开Camera1相机";int selected=-1;Camera.CameraInfo info=new Camera.CameraInfo();
            int wanted="front".equals(facing)?Camera.CameraInfo.CAMERA_FACING_FRONT:Camera.CameraInfo.CAMERA_FACING_BACK;
            for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==wanted){selected=i;break;}}
            if(selected<0)throw new IOException("未找到指定镜头");
            camera=Camera.open(selected);camera.setErrorCallback((code,c)->finish(false,"Camera1设备错误 "+code));
            Camera.Parameters params=camera.getParameters();
            List<Camera.Size> sizes=params.getSupportedVideoSizes();if(sizes==null||sizes.isEmpty())sizes=params.getSupportedPreviewSizes();
            if(sizes==null||sizes.isEmpty())throw new IOException("无可用录像尺寸");
            Camera.Size size=sizes.get(0);for(Camera.Size s:sizes)if((long)s.width*s.height<(long)size.width*size.height)size=s;
            for(Camera.Size s:sizes)if(s.width<=640&&s.height<=480&&(long)s.width*s.height>=(long)size.width*size.height)size=s;
            int fps=30;int[] qualities={CamcorderProfile.QUALITY_480P,CamcorderProfile.QUALITY_LOW};
            for(int quality:qualities)if(CamcorderProfile.hasProfile(selected,quality)){fps=CamcorderProfile.get(selected,quality).videoFrameRate;break;}
            List<int[]> ranges=params.getSupportedPreviewFpsRange();int[] range=null;
            if(ranges!=null)for(int[] r:ranges)if(r[0]<=fps*1000&&r[1]>=fps*1000){range=r;break;}
            if(range==null&&ranges!=null&&!ranges.isEmpty()){range=ranges.get(ranges.size()-1);fps=Math.max(1,range[1]/1000);}
            if(range!=null)params.setPreviewFpsRange(range[0],range[1]);
            Camera.Size ps=params.getPreferredPreviewSizeForVideo();if(ps==null)ps=size;
            params.setPreviewSize(ps.width,ps.height);params.setRecordingHint(true);
            stage="配置预览与帧率";camera.setParameters(params);
            preview=new OffscreenPreview(handler,ps.width,ps.height);camera.setPreviewTexture(preview.texture);camera.startPreview();
            stage="准备MediaRecorder";camera.unlock();recorder=new MediaRecorder();recorder.setCamera(camera);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);recorder.setVideoSize(size.width,size.height);
            recorder.setVideoFrameRate(fps);recorder.setVideoEncodingBitRate(1500000);recorder.setOrientationHint(info.orientation);
            recorder.setOutputFile(temp.getPath());recorder.setOnErrorListener((r,a,b)->finish(false,"MediaRecorder错误 "+a+"/"+b));
            config="Camera1 "+size.width+"×"+size.height+"@"+fps;
            recorder.prepare();stage="启动录像";recorder.start();started=true;
            stop=()->finish(true,"录像已保存 · "+config);handler.postDelayed(stop,seconds*1000L);
        }catch(Exception e){finish(false,stage+"失败 · "+e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    void close(){if(active)finish(false,"用户停止，未完成的录像片段已丢弃");}
    private void finish(boolean ok,String detail){
        if(!active)return;active=false;if(stop!=null)handler.removeCallbacks(stop);
        // Finalize the MP4 while camera and preview are still alive; then release hardware.
        if(recorder!=null){
            recorder.setOnErrorListener(null);
            if(started)try{recorder.stop();}catch(RuntimeException e){ok=false;detail="MediaRecorder.stop失败 · "+config+" · "+e.getMessage();}
            try{recorder.reset();}catch(RuntimeException ignored){}recorder.release();recorder=null;
        }
        if(camera!=null){try{camera.reconnect();camera.stopPreview();}catch(Exception ignored){}camera.release();camera=null;}
        if(preview!=null){preview.close();preview=null;}
        if(ok){MediaMetadataRetriever meta=new MediaMetadataRetriever();try{
            try(FileInputStream in=new FileInputStream(temp)){meta.setDataSource(in.getFD());}
            String value=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if(value==null||Long.parseLong(value)<500)throw new IOException("视频时长无效");
            try(RandomAccessFile sync=new RandomAccessFile(temp,"rw")){sync.getFD().sync();}
            if(!temp.renameTo(output))throw new IOException("文件提交失败");
        }catch(Exception e){ok=false;detail="录像文件校验失败 · "+e.getMessage();}finally{try{meta.release();}catch(Exception ignored){}}}
        if(!ok)temp.delete();CameraEngine.Result cb=callback;callback=null;cb.done(ok,detail);
    }
}
