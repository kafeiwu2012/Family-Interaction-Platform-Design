package com.family.recorder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    static volatile boolean visible;
    private final Handler ui=new Handler();
    private ImageView image;
    private TextView status,clock,space;
    private Button start;
    private boolean dim;
    private final Runnable tick=new Runnable(){public void run(){
        clock.setText(new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss",Locale.CHINA).format(new Date()));
        long seconds=RecorderService.recording?(SystemClock.elapsedRealtime()-RecorderService.started)/1000:0;
        status.setText((RecorderService.recording?"● REC  ":"○ ")+RecorderService.status+String.format(Locale.CHINA,"  %02d:%02d",seconds/60,seconds%60));
        status.setTextColor(RecorderService.recording?0xFFFF706B:0xFFE7EDF6);
        image.setImageBitmap(RecorderService.frame);
        space.setText("循环空间 "+Config.prefs(MainActivity.this).getInt("quota",2048)+" MB  ·  "+(Config.seconds(MainActivity.this)/60)+" 分钟/段\n"+Config.directory(MainActivity.this));
        start.setText(RecorderService.recording?"■ 停止并保存":"● 开始录像");ui.postDelayed(this,500);
    }};
    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,12,20,12);root.setBackgroundColor(0xFF0C1420);setContentView(root);
        LinearLayout header=new LinearLayout(this);root.addView(header);TextView title=label("行车记录仪  /  ROAD RECORDER",18);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));clock=label("",16);header.addView(clock);
        FrameLayout stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));stage.setBackgroundColor(Color.BLACK);
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
        status=label("",16);status.setPadding(16,10,16,10);stage.addView(status,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        TextView hint=label("横屏安装 · 电源键熄屏后继续录制",13);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);hp.setMargins(10,10,12,12);stage.addView(hint,hp);
        space=label("",12);root.addView(space);
        LinearLayout bar=new LinearLayout(this);root.addView(bar);
        start=button(bar,"● 开始录像",()->{if(RecorderService.recording)command("stop");else if(permissions())command("start");});
        button(bar,"切换镜头",()->{
            if(RecorderService.recording)command("switch");else{int v=Config.prefs(this).getInt("facing",0)==0?1:0;Config.prefs(this).edit().putInt("facing",v).apply();RecorderService.status="已选择"+(v==0?"后置":"前置")+"摄像头";}
        });
        button(bar,"锁定片段",()->{if(RecorderService.recording)command("lock");else toast("请先开始录像");});
        button(bar,"录像库",()->startActivity(new Intent(this,ClipsActivity.class)));
        button(bar,"设置",this::settings);
        button(bar,"暗屏",()->{dim=!dim;WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=dim?0.01f:-1;getWindow().setAttributes(p);toast("电源键可真正熄屏；暗屏按钮仅调低亮度");});
    }
    private TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xFFE7EDF6);return t;}
    private Button button(LinearLayout p,String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextSize(13);p.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->r.run());return b;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void command(String a){Intent i=new Intent(this,RecorderService.class).setAction(a);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    private boolean permissions(){
        if(Build.VERSION.SDK_INT<23)return true;
        ArrayList<String> needed=new ArrayList<>();
        for(String p:new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)needed.add(p);
        if(Config.prefs(this).getBoolean("audio",true)&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.RECORD_AUDIO);
        if(needed.isEmpty())return true;requestPermissions(needed.toArray(new String[0]),10);return false;
    }
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] grants){super.onRequestPermissionsResult(request,p,grants);boolean granted=grants.length>0;for(int g:grants)granted &= g==PackageManager.PERMISSION_GRANTED;if(granted)toast("授权成功，请点击开始录像");else toast("需要相机及存储权限；可关闭录音后重试");}
    @Override protected void onResume(){super.onResume();visible=true;ui.post(tick);}
    @Override protected void onPause(){visible=false;ui.removeCallbacks(tick);super.onPause();}
    private Spinner spinner(LinearLayout parent,String title,String[] values,int selected){parent.addView(label(title,14));Spinner s=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(adapter);s.setSelection(selected);parent.addView(s);return s;}
    private CheckBox check(LinearLayout p,String title,String key,boolean fallback){CheckBox c=new CheckBox(this);c.setText(title);c.setChecked(Config.prefs(this).getBoolean(key,fallback));p.addView(c);return c;}
    private void settings(){
        if(RecorderService.recording){toast("请先停止录像再修改设置");return;}
        android.content.SharedPreferences pref=Config.prefs(this);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(24,12,24,12);ScrollView scroll=new ScrollView(this);scroll.addView(form);
        form.addView(label("目标路径（保存后加入预存路径）",14));EditText path=new EditText(this);path.setSingleLine();path.setText(Config.directory(this).getAbsolutePath());form.addView(path);
        Button paths=new Button(this);paths.setText("选择预存路径 / 浏览目录");form.addView(paths);paths.setOnClickListener(v->{
            ArrayList<String> choices=new ArrayList<>();choices.add(Config.defaultPath(this));choices.add(new File(getExternalFilesDir(null),"VehicleRecorder").getAbsolutePath());
            File[] external=getExternalFilesDirs(null);for(File f:external)if(f!=null&&!choices.contains(new File(f,"VehicleRecorder").getAbsolutePath()))choices.add(new File(f,"VehicleRecorder").getAbsolutePath());
            for(String s:pref.getString("history","").split("\n"))if(!s.isEmpty()&&!choices.contains(s))choices.add(s);
            choices.add("浏览存储目录…");new AlertDialog.Builder(this).setTitle("选择目标路径").setItems(choices.toArray(new String[0]),(d,n)->{if(n==choices.size()-1)browse(android.os.Environment.getExternalStorageDirectory(),path);else path.setText(choices.get(n));}).show();
        });
        form.addView(label("循环容量 MB（含锁定片段，最少 256）",14));EditText quota=new EditText(this);quota.setInputType(2);quota.setText(String.valueOf(pref.getInt("quota",2048)));form.addView(quota);
        int sec=Config.seconds(this);Spinner duration=spinner(form,"每段时长",new String[]{"1 分钟","3 分钟","5 分钟"},sec==60?0:sec==300?2:1);
        Spinner quality=spinner(form,"画质（不支持时自动降级）",new String[]{"720p · 推荐 R7sm","1080p · 更高功耗"},pref.getBoolean("hd",false)?1:0);
        Spinner rotation=spinner(form,"录像旋转角度（按安装方向选择）",new String[]{"0°","90°","180°","270°"},pref.getInt("rotation",0)/90);
        CheckBox audio=check(form,"录制声音","audio",true), stabilize=check(form,"电子防抖（设备支持时）","stabilize",true),torch=check(form,"后置常亮补光（增加发热）","torch",false);
        form.addView(label("锁定片段不自动删除。路径不可写、锁定占满或存储不足时停止并提示。熄屏后台需允许 ColorOS 自启动及后台运行。",13));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("记录仪设置").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{
            try{
                int mb=Integer.parseInt(quota.getText().toString());if(mb<256||mb>1048576)throw new IOException("容量应在 256～1048576 MB 之间");
                int seconds=new int[]{60,180,300}[duration.getSelectedItemPosition()];long reserve=((quality.getSelectedItemPosition()==1?8000000L:4000000L)+128000)*seconds/8+4*1024*1024;
                if(reserve>mb*1024L*1024)throw new IOException("容量不足以容纳此画质的一个片段");
                File dir=new File(path.getText().toString().trim());if(!dir.isAbsolute())throw new IOException("请输入绝对路径");
                if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建目录，请检查存储权限");
                File probe=File.createTempFile("write-test",null,dir);if(!probe.delete())throw new IOException("无法删除测试文件");
                String old=pref.getString("history","");LinkedHashSet<String> history=new LinkedHashSet<>();history.add(dir.getCanonicalPath());Collections.addAll(history,old.split("\n"));history.remove("");StringBuilder saved=new StringBuilder();int n=0;for(String s:history){if(n++>=8)break;saved.append(s).append('\n');}
                pref.edit().putString("path",dir.getCanonicalPath()).putString("history",saved.toString()).putInt("quota",mb).putInt("seconds",seconds).putBoolean("hd",quality.getSelectedItemPosition()==1).putInt("rotation",rotation.getSelectedItemPosition()*90).putBoolean("audio",audio.isChecked()).putBoolean("stabilize",stabilize.isChecked()).putBoolean("torch",torch.isChecked()).apply();dialog.dismiss();toast("设置已保存");
            }catch(Exception e){toast("保存失败："+e.getMessage());}
        }));dialog.show();
    }
    private void browse(File dir,EditText target){
        ArrayList<File> dirs=new ArrayList<>();if(dir.getParentFile()!=null)dirs.add(dir.getParentFile());File[] children=dir.listFiles(File::isDirectory);if(children!=null){Arrays.sort(children,Comparator.comparing(File::getName));Collections.addAll(dirs,children);}String[] names=new String[dirs.size()];for(int i=0;i<names.length;i++)names[i]=(i==0&&dir.getParentFile()!=null?"↑ ":"")+dirs.get(i).getName();
        new AlertDialog.Builder(this).setTitle(dir.getAbsolutePath()).setItems(names,(d,n)->browse(dirs.get(n),target)).setPositiveButton("使用此目录",(d,n)->target.setText(new File(dir,"VehicleRecorder").getAbsolutePath())).setNegativeButton("取消",null).show();
    }
}
