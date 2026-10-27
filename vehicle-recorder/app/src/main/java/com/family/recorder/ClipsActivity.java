package com.family.recorder;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.util.*;

public final class ClipsActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);refresh();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void refresh(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,12,20,12);setContentView(root);
        TextView title=new TextView(this);title.setText("录像库 · 当前目标路径\n"+Config.directory(this)+"\n已用 "+LoopStore.used(Config.directory(this))/1048576+" MB / "+Config.quota(this)/1048576+" MB · 锁定片段也计入配额");root.addView(title);
        Button back=new Button(this);back.setText("返回记录仪");root.addView(back);back.setOnClickListener(v->finish());
        ArrayList<File> clips=new ArrayList<>();for(File f:LoopStore.files(Config.directory(this)))if(f.getName().endsWith(".mp4"))clips.add(f);Collections.reverse(clips);
        ArrayList<String> labels=new ArrayList<>();for(File f:clips)labels.add((LoopStore.locked(f)?"🔒 ":"")+f.getName()+"  ·  "+String.format(Locale.CHINA,"%.1f MB",f.length()/1048576.0));
        ListView list=new ListView(this);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,labels));
        list.setEmptyView(empty(root));
        list.setOnItemClickListener((a,v,n,id)->{
            File f=clips.get(n);new AlertDialog.Builder(this).setTitle(f.getName()).setItems(new String[]{"播放录像",LoopStore.locked(f)?"解除锁定":"锁定录像","删除录像"},(d,item)->{
                if(item==0){try{Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(ClipProvider.uri(f),"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(ActivityNotFoundException e){toast("请安装支持 MP4 的视频播放器");}}
                if(item==1){try{File marker=new File(f.getPath()+".lock");if(marker.exists()){if(!marker.delete())throw new IOException();}else if(!marker.createNewFile())throw new IOException();refresh();}catch(IOException e){toast("锁定状态修改失败");}}
                if(item==2){if(LoopStore.locked(f)){toast("请先解除锁定");return;}new AlertDialog.Builder(this).setMessage("删除此录像？此操作无法撤销。").setNegativeButton("取消",null).setPositiveButton("删除",(x,y)->{if(f.delete())refresh();else toast("删除失败");}).show();}
            }).show();
        });
    }
    private TextView empty(LinearLayout root){TextView t=new TextView(this);t.setText("暂无已完成的录像，当前片段将在停止或分段后显示。");root.addView(t);return t;}
}
