package com.family.recorder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

final class SettingsPanel {
    static void show(Activity a){
        SharedPreferences pref=Config.prefs(a);Dialog dialog=new Dialog(a);LinearLayout root=Ui.column(a);root.setPadding(d(a,20),d(a,14),d(a,20),d(a,14));root.setBackgroundColor(Ui.BG);
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
        header.addView(Ui.heading(a,"记录仪设置"),new LinearLayout.LayoutParams(0,d(a,32),1));header.addView(Ui.text(a,RecorderService.active?"●  录像继续中":"个性化配置",12,Ui.GREEN));
        TextView note=Ui.text(a,"浏览菜单不打断录像；保存画质、路径等设置时自动保存当前片段并继续。",11,Ui.MUTED);root.addView(note);
        LinearLayout tabs=new LinearLayout(a);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,d(a,40));tp.setMargins(0,d(a,10),0,d(a,8));root.addView(tabs,tp);
        FrameLayout content=new FrameLayout(a);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout[] panels=new LinearLayout[3];ScrollView[] pages=new ScrollView[3];Button[] tabButtons=new Button[3];
        for(int i=0;i<3;i++){pages[i]=new ScrollView(a);panels[i]=Ui.column(a);pages[i].addView(panels[i]);content.addView(pages[i],new FrameLayout.LayoutParams(-1,-1));final int index=i;tabButtons[i]=Ui.button(a,new String[]{"录像画质","循环存储","运行偏好"}[i],Ui.TEXT,()->{for(int k=0;k<3;k++){pages[k].setVisibility(k==index?View.VISIBLE:View.GONE);tabButtons[k].setTextColor(k==index?Ui.GREEN:Ui.MUTED);}});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(0,0,d(a,6),0);tabs.addView(tabButtons[i],p);pages[i].setVisibility(i==0?View.VISIBLE:View.GONE);}tabButtons[0].setTextColor(Ui.GREEN);
        section(a,panels[0],"画面与编码","720p 适合 R7sm；1080p 会增加编码和存储负担。");
        Spinner quality=select(a,panels[0],"视频清晰度",new String[]{"720p · 推荐","1080p · 高清"},pref.getBoolean("hd",false)?1:0);
        Spinner fps=select(a,panels[0],"录像帧率",new String[]{"30 fps · 流畅","24 fps · 均衡"},pref.getInt("fps",30)==24?1:0);
        Spinner preview=select(a,panels[0],"预览帧率",new String[]{"30 fps · 流畅预览","15 fps · 节能预览"},pref.getInt("preview_fps",30)==15?1:0);
        Spinner rotation=select(a,panels[0],"方向校正",new String[]{"0°","90°","180°","270°"},pref.getInt("rotation",0)/90);
        Switch audio=toggle(a,panels[0],"同步录音",pref.getBoolean("audio",true));
        Switch stabilize=toggle(a,panels[0],"电子防抖 · 硬件支持时生效",pref.getBoolean("stabilize",true));
        Switch torch=toggle(a,panels[0],"后置常亮补光",pref.getBoolean("torch",false));
        section(a,panels[1],"循环存储","只覆盖最旧的未锁定录像；锁定录像同样计入容量。");
        panels[1].addView(Ui.text(a,"目标目录",12,Ui.MUTED));EditText path=new EditText(a);path.setTextColor(Ui.TEXT);path.setText(Config.directory(a).getAbsolutePath());path.setSingleLine();panels[1].addView(path);
        Button paths=Ui.button(a,"选择预存路径 / 浏览目录",Ui.GREEN,()->choosePath(a,path));panels[1].addView(paths,new LinearLayout.LayoutParams(-1,d(a,40)));
        panels[1].addView(Ui.text(a,"循环容量（MB，至少 256）",12,Ui.MUTED));EditText quota=new EditText(a);quota.setTextColor(Ui.TEXT);quota.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);quota.setText(String.valueOf(pref.getInt("quota",2048)));panels[1].addView(quota);
        Spinner duration=select(a,panels[1],"片段时长",new String[]{"1 分钟","3 分钟","5 分钟"},Config.seconds(a)==60?0:Config.seconds(a)==300?2:1);
        section(a,panels[1],"清晰文件名","后摄像头_20261027_131205_123.mp4\n前摄像头_20261027_131505_456.mp4\n使用手机本地日期时间；旧版本文件继续可播放和循环管理。");
        section(a,panels[2],"启动与后台","自动录像仅在打开软件时触发，手动停止后不会因返回主界面再次启动。");
        Switch auto=toggle(a,panels[2],"打开软件自动开始录像",pref.getBoolean("auto_start",true));
        section(a,panels[2],"横竖屏与全屏","默认横屏，可在主界面菜单手动切换为竖屏。选择会被记住，不随手机晃动自动旋转。菜单 8 秒无操作自动收起，右上角圆形齿轮按钮可随时展开。默认预览铺满，可能裁切边缘；可切为完整画面，录像保留完整画幅。切换横竖屏会保存当前片段后继续。方向异常时在录像画质中设置校正角度。 ");
        section(a,panels[2],"熄屏持续录制","请在 ColorOS 中允许后台运行及自启动，并锁定最近任务。按电源键熄屏后继续录制；暗屏按钮只降低亮度。");
        section(a,panels[2],"版本 1.2.2","GPU 直接预览 · 无位图回读\n建议先录制 1 分钟并回放，确认画面方向、声音和流畅度。");
        LinearLayout actions=new LinearLayout(a);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,d(a,42));ap.setMargins(0,d(a,10),0,0);root.addView(actions,ap);
        Button cancel=Ui.button(a,"取消",Ui.MUTED,dialog::dismiss);actions.addView(cancel,new LinearLayout.LayoutParams(0,-1,1));
        Button save=Ui.button(a,"保存并应用",Ui.GREEN,()->{});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-1,2);sp.setMargins(d(a,8),0,0,0);actions.addView(save,sp);
        save.setOnClickListener(v->{
            if(audio.isChecked()&&Build.VERSION.SDK_INT>=23&&a.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);return;}
            final Bundle values=new Bundle();
            try{
                int mb=Integer.parseInt(quota.getText().toString().trim()),seconds=new int[]{60,180,300}[duration.getSelectedItemPosition()];
                if(mb<256||mb>1048576)throw new IOException("容量应在 256～1048576 MB 之间");
                long reserve=((quality.getSelectedItemPosition()==1?8000000L:4000000L)+128000)*seconds/8+4*1024*1024;
                if(reserve>mb*1048576L)throw new IOException("容量不足以容纳一个片段，请缩短时长或增加容量");
                values.putString("path",path.getText().toString().trim());values.putInt("quota",mb);values.putInt("seconds",seconds);values.putBoolean("hd",quality.getSelectedItemPosition()==1);values.putInt("fps",fps.getSelectedItemPosition()==1?24:30);values.putInt("preview_fps",preview.getSelectedItemPosition()==1?15:30);values.putInt("rotation",rotation.getSelectedItemPosition()*90);values.putBoolean("audio",audio.isChecked());values.putBoolean("stabilize",stabilize.isChecked());values.putBoolean("torch",torch.isChecked());values.putBoolean("auto_start",auto.isChecked());
            }catch(Exception e){Ui.toast(a,"无法保存："+e.getMessage());return;}
            save.setEnabled(false);save.setText("检查目录…");
            new Thread(()->{
                try{
                    File dir=new File(values.getString("path"));if(!dir.isAbsolute())throw new IOException("请输入绝对路径");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建目录，请检查存储权限");
                    File probe=File.createTempFile("write-test",null,dir);if(!probe.delete())throw new IOException("目录不可删除测试文件");values.putString("path",dir.getCanonicalPath());
                    LinkedHashSet<String> history=new LinkedHashSet<>();history.add(dir.getCanonicalPath());history.add(Config.directory(a).getCanonicalPath());Collections.addAll(history,pref.getString("history","").split("\n"));history.remove("");StringBuilder saved=new StringBuilder();int n=0;for(String s:history){if(n++==8)break;saved.append(s).append('\n');}values.putString("history",saved.toString());
                    a.runOnUiThread(()->{if(!dialog.isShowing()||a.isFinishing())return;Intent i=new Intent(a,RecorderService.class).setAction("configure").putExtra("settings",values);if(Build.VERSION.SDK_INT>=26)a.startForegroundService(i);else a.startService(i);dialog.dismiss();Ui.toast(a,"设置已提交，将自动保存片段后应用");});
                }catch(Exception e){a.runOnUiThread(()->{if(dialog.isShowing()){save.setEnabled(true);save.setText("保存并应用");Ui.toast(a,"无法保存："+e.getMessage());}});}
            },"validate-storage").start();
        });
        dialog.setContentView(root);dialog.show();Window w=dialog.getWindow();if(w!=null){w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels,d(a,560)),-1);w.setGravity(Gravity.END);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    }
    private static int d(Activity a,int n){return Ui.dp(a,n);}
    private static void section(Activity a,LinearLayout p,String title,String detail){TextView h=Ui.heading(a,title);h.setPadding(0,d(a,12),0,d(a,4));p.addView(h);TextView t=Ui.text(a,detail,11,Ui.MUTED);t.setPadding(0,0,0,d(a,10));p.addView(t);}
    private static Spinner select(Activity a,LinearLayout p,String label,String[] choices,int selected){LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(d(a,10),0,d(a,6),0);row.setBackground(Ui.shape(Ui.CARD,d(a,10)));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,d(a,44));rp.setMargins(0,0,0,d(a,7));p.addView(row,rp);row.addView(Ui.text(a,label,13,Ui.TEXT),new LinearLayout.LayoutParams(0,-1,1));Spinner s=new Spinner(a);s.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,choices));s.setSelection(selected);row.addView(s,new LinearLayout.LayoutParams(d(a,210),-1));return s;}
    private static Switch toggle(Activity a,LinearLayout p,String title,boolean value){Switch s=new Switch(a);s.setText(title);s.setTextSize(13);s.setTextColor(Ui.TEXT);s.setChecked(value);s.setPadding(d(a,10),0,d(a,10),0);p.addView(s,new LinearLayout.LayoutParams(-1,d(a,44)));return s;}
    private static void choosePath(Activity a,EditText target){ArrayList<String> choices=new ArrayList<>();choices.add(Config.defaultPath(a));for(File f:a.getExternalFilesDirs(null))if(f!=null)choices.add(new File(f,"VehicleRecorder").getAbsolutePath());for(String s:Config.prefs(a).getString("history","").split("\n"))if(!s.isEmpty()&&!choices.contains(s))choices.add(s);choices.add("浏览目录…");new AlertDialog.Builder(a).setTitle("预存目标路径").setItems(choices.toArray(new String[0]),(dialog,n)->{if(n==choices.size()-1)browse(a,android.os.Environment.getExternalStorageDirectory(),target);else target.setText(choices.get(n));}).show();}
    private static void browse(Activity a,File dir,EditText target){ArrayList<File> dirs=new ArrayList<>();if(dir.getParentFile()!=null)dirs.add(dir.getParentFile());File[] children=dir.listFiles(File::isDirectory);if(children!=null){Arrays.sort(children,(x,y)->x.getName().compareTo(y.getName()));Collections.addAll(dirs,children);}String[] labels=new String[dirs.size()];for(int i=0;i<labels.length;i++)labels[i]=(i==0&&dir.getParentFile()!=null?"↑ ":"")+dirs.get(i).getName();new AlertDialog.Builder(a).setTitle(dir.getAbsolutePath()).setItems(labels,(dialog,n)->browse(a,dirs.get(n),target)).setPositiveButton("使用此目录",(dialog,n)->target.setText(new File(dir,"VehicleRecorder").getAbsolutePath())).setNegativeButton("取消",null).show();}
}
