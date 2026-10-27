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
    @Override public void onCreate(Bundle state){super.onCreate(state);showList();}
    private void showList(){
        generation++;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        File dir=new File(getFilesDir(),"local-photos");
        File[] files=dir.listFiles((d,n)->n.endsWith(".jpg"));if(files==null)files=new File[0];
        Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));
        final File[] items=files;
        TextView hint=new TextView(this);hint.setText("本地测试照片："+files.length+" 张（点击查看，长按删除）。返回后重新进入可刷新。卸载应用会删除照片。");root.addView(hint);
        ListView list=new ListView(this);String[] names=new String[files.length];for(int i=0;i<files.length;i++) names[i]=files[i].getName();
        list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names));root.addView(list);
        list.setOnItemClickListener((a,v,pos,id)->showImage(items[pos]));
        list.setOnItemLongClickListener((a,v,pos,id)->{
            new AlertDialog.Builder(this).setMessage("删除这张本地照片？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{
                if(!items[pos].delete())Toast.makeText(this,"删除失败",Toast.LENGTH_SHORT).show();showList();
            }).show();return true;
        });setContentView(root);
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
