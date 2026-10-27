package com.family.recorder;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class ClipsActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayList<File> all=new ArrayList<>(),shown=new ArrayList<>();
    private TextView summary,empty;
    private ProgressBar capacity;
    private BaseAdapter adapter;
    private int filter;
    private final Button[] tabs=new Button[4];
    @Override public void onCreate(Bundle b){super.onCreate(b);Ui.fullscreen(this);
        LinearLayout root=Ui.column(this);root.setPadding(d(20),d(12),d(20),d(12));root.setBackgroundColor(Ui.BG);setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,d(42)));
        header.addView(Ui.heading(this,"录像库"),new LinearLayout.LayoutParams(0,-1,1));header.addView(Ui.button(this,"返回预览",Ui.GREEN,this::finish),new LinearLayout.LayoutParams(d(100),d(34)));
        summary=Ui.text(this,"正在读取录像…",12,Ui.MUTED);root.addView(summary);
        capacity=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);capacity.setMax(1000);root.addView(capacity,new LinearLayout.LayoutParams(-1,d(5)));
        LinearLayout filters=new LinearLayout(this);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,d(38));fp.setMargins(0,d(10),0,d(10));root.addView(filters,fp);
        String[] names={"全部录像","已保护","前摄像头","后摄像头"};for(int i=0;i<4;i++){final int index=i;tabs[i]=Ui.button(this,names[i],i==0?Ui.GREEN:Ui.MUTED,()->{filter=index;applyFilter();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(0,0,d(7),0);filters.addView(tabs[i],p);}
        FrameLayout body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));ListView list=new ListView(this);list.setDividerHeight(d(8));list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.BG));body.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=Ui.text(this,"暂无已完成的录像\n录制中的片段保存后会显示在这里",14,Ui.MUTED);empty.setGravity(Gravity.CENTER);body.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        adapter=new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
            public View getView(int position,View recycled,ViewGroup parent){File f=shown.get(position);LinearLayout card=new LinearLayout(ClipsActivity.this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(d(14),d(10),d(14),d(10));card.setBackground(Ui.shape(Ui.CARD,d(12)));
                TextView icon=Ui.text(ClipsActivity.this,front(f)?"前":"后",18,Ui.GREEN);icon.setGravity(Gravity.CENTER);icon.setBackground(Ui.shape(0xFF223C4A,d(10)));card.addView(icon,new LinearLayout.LayoutParams(d(42),d(42)));
                LinearLayout detail=Ui.column(ClipsActivity.this);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);dp.setMargins(d(14),0,d(10),0);card.addView(detail,dp);detail.addView(Ui.text(ClipsActivity.this,f.getName(),14,Ui.TEXT));detail.addView(Ui.text(ClipsActivity.this,String.format(Locale.CHINA,"%.1f MB   ·   %s",f.length()/1048576.0,front(f)?"前摄像头":"后摄像头"),11,Ui.MUTED));card.addView(Ui.text(ClipsActivity.this,LoopStore.locked(f)?"◇ 已保护   ›":"播放 / 管理   ›",12,LoopStore.locked(f)?Ui.GREEN:Ui.MUTED));return card;}
        };list.setAdapter(adapter);list.setOnItemClickListener((p,v,n,id)->actions(shown.get(n)));
    }
    private int d(int value){return Ui.dp(this,value);}
    private static boolean front(File f){return f.getName().startsWith("前摄像头_")||f.getName().matches("VR_[0-9]+_F\\.mp4");}
    @Override protected void onResume(){super.onResume();reload();}
    private void reload(){final File dir=Config.directory(this);final long quota=Config.quota(this);io.execute(()->{File[] files=LoopStore.files(dir);long used=0;ArrayList<File> clips=new ArrayList<>();for(File f:files){used+=f.length();if(f.getName().endsWith(".mp4"))clips.add(f);}Collections.reverse(clips);final long bytes=used;runOnUiThread(()->{if(isFinishing())return;all.clear();all.addAll(clips);summary.setText(String.format(Locale.CHINA,"%d 个片段   ·   %.0f / %.0f MB   ·   %s",clips.size(),bytes/1048576.0,quota/1048576.0,dir));capacity.setProgress((int)Math.min(1000,bytes*1000/Math.max(1,quota)));applyFilter();});});}
    private void applyFilter(){shown.clear();for(File f:all)if(filter==0||(filter==1&&LoopStore.locked(f))||(filter==2&&front(f))||(filter==3&&!front(f)))shown.add(f);for(int i=0;i<4;i++)tabs[i].setTextColor(i==filter?Ui.GREEN:Ui.MUTED);adapter.notifyDataSetChanged();}
    private void actions(File f){new AlertDialog.Builder(this).setTitle(f.getName()).setItems(new String[]{"▶ 播放录像",LoopStore.locked(f)?"解除保护":"◇ 保护此片段","删除录像"},(dialog,item)->{
        if(item==0){try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(ClipProvider.uri(f),"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(ActivityNotFoundException e){Ui.toast(this,"请安装支持 MP4 的视频播放器");}}
        if(item==1){try{synchronized(LoopStore.class){if(!f.exists())throw new IOException("片段已被循环清理");File marker=new File(f.getPath()+".lock");if(marker.exists()){if(!marker.delete())throw new IOException("无法解除保护");}else if(!marker.createNewFile())throw new IOException("无法保护");}reload();}catch(IOException e){Ui.toast(this,e.getMessage());}}
        if(item==2){if(LoopStore.locked(f)){Ui.toast(this,"请先解除保护");return;}new AlertDialog.Builder(this).setMessage("删除此录像？此操作无法撤销。").setNegativeButton("取消",null).setPositiveButton("删除",(x,y)->{synchronized(LoopStore.class){if(LoopStore.locked(f)){Ui.toast(this,"片段已保护，请先解除保护");return;}if(f.delete())reload();else Ui.toast(this,"删除失败");}}).show();}
    }).show();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
