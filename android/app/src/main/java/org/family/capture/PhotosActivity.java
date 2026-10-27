package org.family.capture;

import android.app.*;
import android.os.Bundle;
import android.graphics.*;
import android.widget.*;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.*;

public class PhotosActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Bitmap shown;
    private int generation;
    @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null&&state.getStringArrayList("export")!=null)exportPaths=state.getStringArrayList("export");showList();}
    private ListView list;
    private File[] items=new File[0];
    private java.util.ArrayList<String> exportPaths=new java.util.ArrayList<>();
    private boolean exporting;
    private java.util.ArrayList<File> selected(){
        java.util.ArrayList<File> result=new java.util.ArrayList<>();
        for(int i=0;i<items.length;i++)if(list.isItemChecked(i))result.add(items[i]);
        return result;
    }
    private void showList(){
        generation++;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        File dir=new File(getFilesDir(),"local-photos");
        File[] files=dir.listFiles((d,n)->n.endsWith(".jpg")||n.endsWith(".mp4"));if(files==null)files=new File[0];
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));items=files;
        TextView hint=new TextView(this);hint.setText("本地照片/视频："+files.length+" 个。点击多选，长按预览。导出为ZIP，原文件保留。");root.addView(hint);
        LinearLayout buttons=new LinearLayout(this);
        Button all=new Button(this);all.setText("全选/取消");buttons.addView(all);
        Button delete=new Button(this);delete.setText("删除所选");buttons.addView(delete);
        Button export=new Button(this);export.setText("导出所选");buttons.addView(export);root.addView(buttons);
        list=new ListView(this);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        String[] names=new String[files.length];for(int i=0;i<files.length;i++)names[i]=MediaNames.display(files[i]);
        list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_multiple_choice,names));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        all.setOnClickListener(v->{boolean check=list.getCheckedItemCount()!=items.length;for(int i=0;i<items.length;i++)list.setItemChecked(i,check);});
        delete.setOnClickListener(v->{
            if(exporting){toast("导出中，请等待完成再删除");return;}
            java.util.ArrayList<File> picked=selected();if(picked.isEmpty()){toast("请先选择文件");return;}
            new AlertDialog.Builder(this).setMessage("永久删除所选 "+picked.size()+" 个文件？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{
                worker.execute(()->{int failed=0;for(File f:picked)if(!f.delete())failed++;final int errors=failed;runOnUiThread(()->{if(!isFinishing()){toast("删除完成，失败 "+errors+" 个");showList();}});});
            }).show();
        });
        export.setOnClickListener(v->{
            if(exporting){toast("正在导出，请等待");return;}
            java.util.ArrayList<File> picked=selected();if(picked.isEmpty()){toast("请先选择文件");return;}
            exportPaths.clear();for(File f:picked)exportPaths.add(f.getPath());
            android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("application/zip");
            intent.putExtra(android.content.Intent.EXTRA_TITLE,"相机导出_"+new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",java.util.Locale.US).format(new java.util.Date())+".zip");
            try{startActivityForResult(intent,7);}catch(android.content.ActivityNotFoundException e){toast("系统缺少文件保存组件");}
        });
        list.setOnItemLongClickListener((a,v,pos,id)->{
            if(items[pos].getName().endsWith(".mp4"))showVideo(items[pos]);else showImage(items[pos]);return true;
        });setContentView(root);
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putStringArrayList("export",exportPaths);}
    @Override protected void onActivityResult(int request,int result,android.content.Intent data){
        super.onActivityResult(request,result,data);
        if(request!=7||result!=RESULT_OK||data==null||data.getData()==null)return;
        final android.net.Uri uri=data.getData();final java.util.ArrayList<String> paths=new java.util.ArrayList<>(exportPaths);exporting=true;toast("正在导出，请保持此页面打开");
        worker.execute(()->{
            String message;
            try(java.io.OutputStream raw=getContentResolver().openOutputStream(uri,"w")){
                if(raw==null)throw new java.io.IOException("无法打开目标");
                try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(raw)){
                    MediaArchive.write(zip,paths);
                }message="已导出 "+paths.size()+" 个文件，原文件保留";
            }catch(Exception e){message="导出失败："+e.getClass().getSimpleName()+"；原文件保留";try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}}
            final String text=message;runOnUiThread(()->{exporting=false;if(!isFinishing())toast(text);});
        });
    }
    private void showVideo(File file){
        VideoView video=new VideoView(this);video.setVideoPath(file.getPath());
        MediaController controller=new MediaController(this);controller.setAnchorView(video);video.setMediaController(controller);
        video.setOnPreparedListener(p->video.start());video.setOnErrorListener((p,a,b)->{toast("视频无法播放");return true;});
        new AlertDialog.Builder(this).setTitle(MediaNames.display(file)).setView(video).setPositiveButton("关闭",null).setOnDismissListener(d->video.stopPlayback()).show();
    }
    private void showImage(File file){
        final int attempt=++generation;
        worker.execute(()->{
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),opts);
            opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>1200||opts.outHeight/opts.inSampleSize>1600)opts.inSampleSize*=2;
            opts.inJustDecodeBounds=false;
            Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),opts);
            runOnUiThread(()->{
                if(isFinishing()||attempt!=generation){if(bitmap!=null)bitmap.recycle();return;}
                if(bitmap==null){Toast.makeText(this,"照片无法解码",Toast.LENGTH_LONG).show();return;}
                shown=bitmap;ImageView view=new ImageView(this);view.setAdjustViewBounds(true);view.setImageBitmap(bitmap);
                new AlertDialog.Builder(this).setTitle(file.getName()).setView(view).setPositiveButton("关闭",null).setOnDismissListener(d->{view.setImageDrawable(null);bitmap.recycle();if(shown==bitmap)shown=null;}).show();
            });
        });
    }
    @Override protected void onDestroy(){generation++;worker.shutdownNow();super.onDestroy();}
}
