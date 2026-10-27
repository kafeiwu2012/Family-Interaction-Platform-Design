package org.family.capture;
import android.content.Context;
import android.os.*;
import java.io.File;
import java.util.*;

/** Explicit local diagnostic using two configured JPEG streams, then two silent recorders. */
final class DualProbe {
    final Context context;final Handler handler;final CameraEngine.Result result;
    final CameraEngine[] cameras=new CameraEngine[2];final VideoEngine[] videos=new VideoEngine[2];
    final List<Runnable> ready=new ArrayList<>();
    boolean finished,videoScheduled;int photoDone,videoDone;boolean photosOk=true,videosOk=true;
    String photoDetail="",videoDetail="";Runnable timeout;
    DualProbe(Context c,Handler h,CameraEngine.Result r){context=c;handler=h;result=r;}
    void start(){
        timeout=()->finish(false,"诊断超时；未能确认并发拍照/录像");handler.postDelayed(timeout,55000);
        File folder=new File(context.getFilesDir(),"local-photos");folder.mkdirs();
        if(folder.getUsableSpace()<96L*1024*1024){finish(false,"空间不足，无法诊断");return;}
        for(int i=0;i<2;i++)cameras[i]=new CameraEngine(context,handler);
        for(int i=0;i<2&&!finished;i++){
            final String facing=i==0?"back":"front";
            cameras[i].capture(facing,new File(folder,MediaNames.create(facing,"jpg")),(ok,detail)->{
                if(finished)return;photosOk&=ok;photoDetail+=facing+":"+detail+"; ";photoDone++;
                if(!ok) { // Release the peer promptly rather than waiting on the barrier timeout.
                    for(CameraEngine c:cameras)c.close();
                }
                if(photoDone==2&&!videoScheduled){videoScheduled=true;handler.postDelayed(this::testVideo,1000);}
            },shoot->{ready.add(shoot);if(ready.size()==2){for(Runnable run:ready)run.run();}});
        }
    }
    void testVideo(){
        if(finished)return;
        File folder=new File(context.getFilesDir(),"local-photos");
        for(int i=0;i<2;i++)videos[i]=new VideoEngine(context,handler);
        for(int i=0;i<2&&!finished;i++){
            String facing=i==0?"back":"front";
            videos[i].record(facing,new File(folder,MediaNames.create(facing,"mp4")),5,(ok,detail)->{
                if(finished)return;videosOk&=ok;videoDetail+=facing+":"+detail+"; ";videoDone++;
                if(videoDone==2){
                    long overlap=Math.min(videos[0].endedAt,videos[1].endedAt)-Math.max(videos[0].startedAt,videos[1].startedAt);
                    boolean concurrent=videosOk&&videos[0].startedAt>0&&videos[1].startedAt>0&&overlap>=1000;
                    finish(photosOk&&concurrent,"前后摄并发拍照："+(photosOk?"本次两路JPEG均成功（会话就绪后一起提交）":"本次未通过")+"\n"+photoDetail+
                        "\n前后摄并发无声录像："+(concurrent?"本次两路有效文件，重叠约"+overlap/1000+"秒":"本次未通过或没有足够重叠")+"\n"+videoDetail+
                        "\n结果仅适用于本次配置/固件，不代表精确同步曝光，也不保证录像中拍照。成功文件可在本地媒体列表核验。正常拍摄继续串行；请分别检查两路画面及视频时长。");
                }
            });
        }
    }
    void finish(boolean ok,String text){
        if(finished)return;finished=true;if(timeout!=null)handler.removeCallbacks(timeout);
        for(CameraEngine c:cameras)if(c!=null)c.close();for(VideoEngine v:videos)if(v!=null)v.close();result.done(ok,text);
    }
}
