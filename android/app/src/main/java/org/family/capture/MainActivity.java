package org.family.capture;

import android.app.*;
import android.os.*;
import android.content.*;
import android.widget.*;
import android.text.*;
import android.view.View;

public class MainActivity extends Activity {
    EditText url,token,interval,photoWindow,videoSeconds,restSeconds;
    Spinner storage,task,lens;
    LinearLayout uploadFields,cycleFields,settings;
    TextView summary,status,editHint;
    Button start,dualButton;
    HandlerThread probeThread;DualProbe probe;
    final Handler handler=new Handler();boolean binding;
    final Runnable refresh=new Runnable(){public void run(){
        boolean active=isRunning();
        status.setText((active?"运行中\n":"当前未运行\n")+getSharedPreferences("runtime",0).getString("status","尚未启动"));
        enableTree(settings,!active&&probeThread==null);lens.setEnabled(storage.getSelectedItemPosition()==0&&probeThread==null);
        start.setEnabled(!active&&probeThread==null);editHint.setText(active?"参数已锁定；先停止再修改。镜头可在本地运行中切换，下次拍摄生效。":"设置完成后点击启动。视频无声音；运行时保持常驻通知。");
        handler.postDelayed(this,1000);
    }};
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    void text(LinearLayout root,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setPadding(0,dp(8),0,dp(4));root.addView(t);}
    EditText field(LinearLayout root,String label,String value){
        text(root,label,16);EditText e=new EditText(this);e.setText(value);e.setSingleLine(true);root.addView(e);return e;
    }
    EditText number(LinearLayout root,String label,int value){EditText e=field(root,label,String.valueOf(value));e.setInputType(InputType.TYPE_CLASS_NUMBER);e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){updateSummary();}public void afterTextChanged(Editable e){}});return e;}
    Spinner spinner(LinearLayout root,String... values){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values));root.addView(s);return s;}
    Button button(LinearLayout root,String title){Button b=new Button(this);b.setText(title);root.addView(b);return b;}
    boolean isLocal(){return storage.getSelectedItemPosition()==0;}
    boolean isCycle(){return isLocal()&&task.getSelectedItemPosition()==1;}
    void enableTree(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)enableTree(group.getChildAt(i),enabled);}}
    boolean isRunning(){ActivityManager m=(ActivityManager)getSystemService(ACTIVITY_SERVICE);for(ActivityManager.RunningServiceInfo r:m.getRunningServices(100))if(CaptureService.class.getName().equals(r.service.getClassName()))return true;return false;}
    void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    void updateForm(){if(binding)return;uploadFields.setVisibility(isLocal()?View.GONE:View.VISIBLE);task.setVisibility(isLocal()?View.VISIBLE:View.GONE);cycleFields.setVisibility(isCycle()?View.VISIBLE:View.GONE);lens.setEnabled(isLocal());updateSummary();}
    void updateSummary(){
        if(summary==null||interval==null||photoWindow==null||videoSeconds==null||restSeconds==null)return;
        try{int gap=Integer.parseInt(interval.getText().toString());
            if(gap<10||gap>86400)throw new IllegalArgumentException();
            if(isCycle()){
                int window=Integer.parseInt(photoWindow.getText().toString()),clip=Integer.parseInt(videoSeconds.getText().toString()),rest=Integer.parseInt(restSeconds.getText().toString());
                if(window<10||window>86400||clip<2||clip>60||rest<0||rest>86400)throw new IllegalArgumentException();
                summary.setText("每轮执行顺序\n① 拍照阶段 "+window+" 秒：首张立即拍，此后每 "+gap+" 秒一张，约 "+(1+(window-1)/gap)+" 张\n② 录制一段 "+clip+" 秒无声视频\n③ 等待 "+rest+" 秒，再回到①\n切换相机及初始化会增加实际耗时，当前拍摄完成后才进入下一阶段。");
            }else summary.setText("执行顺序\n启动后立即拍第一张，此后每 "+gap+" 秒拍一张，直到停止。\n"+(isLocal()?"全部保存在本机，不录像、不上传。":"只拍照并上传；间隔连接后由服务端配置覆盖，镜头由服务端控制。"));
        }catch(Exception e){summary.setText("请填写有效参数：拍照间隔/阶段10–86400秒，录像2–60秒，等待0–86400秒。");}
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);binding=true;SharedPreferences p=getSharedPreferences("config",0);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(8),dp(16),dp(16));
        text(root,"仅在有权拍摄且在场人员知情时使用。运行有常驻通知，可随时停止；熄屏效果以真机为准。",14);
        settings=new LinearLayout(this);settings.setOrientation(LinearLayout.VERTICAL);root.addView(settings);
        text(settings,"1 · 保存位置",20);
        storage=spinner(settings,"本地保存 · 无需网络或服务器","上传树莓派 · 仅拍照");storage.setSelection(p.getBoolean("local",true)?0:1);
        uploadFields=new LinearLayout(this);uploadFields.setOrientation(LinearLayout.VERTICAL);settings.addView(uploadFields);
        url=field(uploadFields,"HTTPS 服务地址",p.getString("url",""));token=field(uploadFields,"设备令牌",p.getString("token",""));token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        text(settings,"2 · 拍摄方式",20);
        task=spinner(settings,"只定时拍照","循环：拍照 → 短录像 → 等待");task.setSelection(p.getBoolean("cycle",false)?1:0);
        interval=number(settings,"两张照片之间的间隔（秒）",p.getInt("interval",60));
        cycleFields=new LinearLayout(this);cycleFields.setOrientation(LinearLayout.VERTICAL);settings.addView(cycleFields);
        photoWindow=number(cycleFields,"① 拍照阶段持续多久（秒）",p.getInt("photoWindow",60));
        videoSeconds=number(cycleFields,"② 接着录一段多久的视频（秒，无声）",p.getInt("videoSeconds",5));
        restSeconds=number(cycleFields,"③ 录像后休息多久再开始下一轮（秒）",p.getInt("restSeconds",10));
        Button example=button(cycleFields,"使用示例：拍30秒 → 录5秒 → 等10秒");example.setOnClickListener(v->{interval.setText("10");photoWindow.setText("30");videoSeconds.setText("5");restSeconds.setText("10");});
        summary=new TextView(this);summary.setTextSize(16);summary.setPadding(dp(12),dp(12),dp(12),dp(12));summary.setBackgroundColor(0xffedf4ed);settings.addView(summary);
        text(root,"3 · 镜头与启停",20);lens=spinner(root,"后置摄像头","前置摄像头");lens.setSelection("front".equals(p.getString("facing","back"))?1:0);
        lens.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int pos,long id){p.edit().putString("facing",pos==1?"front":"back").apply();}});
        editHint=new TextView(this);root.addView(editHint);
        start=button(root,"同意并启动当前方案");start.setOnClickListener(v->{
            if(probeThread!=null||isRunning()){toast("请先停止当前任务");return;}
            try{
                int gap=Integer.parseInt(interval.getText().toString());int window=60,clip=5,rest=10;
                if(gap<10||gap>86400){toast("拍照间隔需10–86400秒");return;}
                if(isCycle()){
                    window=Integer.parseInt(photoWindow.getText().toString());clip=Integer.parseInt(videoSeconds.getText().toString());rest=Integer.parseInt(restSeconds.getText().toString());
                    if(window<10||window>86400||clip<2||clip>60||rest<0||rest>86400){toast("请按参数范围填写，见执行顺序提示");return;}
                }
                String u=url.getText().toString().trim(),t=token.getText().toString().trim();
                if(!isLocal()&&(!u.startsWith("https://")||t.length()<32)){toast("上传模式需HTTPS地址及至少32字符令牌");return;}
                p.edit().putBoolean("local",isLocal()).putBoolean("cycle",isCycle()).putInt("interval",gap).putInt("photoWindow",window).putInt("videoSeconds",clip).putInt("restSeconds",rest).putString("url",u.replaceAll("/+$","")).putString("token",t).apply();
                startService(new Intent(this,CaptureService.class));
            }catch(NumberFormatException e){toast("请输入整数秒数");}
        });
        button(root,"立即停止").setOnClickListener(v->{stopService(new Intent(this,CaptureService.class));cancelProbe();});
        status=new TextView(this);status.setTextSize(15);root.addView(status);
        button(root,"打开本地媒体 · 查看 / 批量删除 / 导出").setOnClickListener(v->startActivity(new Intent(this,PhotosActivity.class)));
        text(root,"设备诊断（不属于拍摄参数）",20);
        Button caps=new Button(this);caps.setText("查看摄像头能力");root.addView(caps);caps.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Camera2 能力报告").setMessage(CameraEngine.report(this)+"\n最近并发媒体测试："+diagnosticText()).setPositiveButton("关闭",null).show());
        Button dual=new Button(this);dualButton=dual;dual.setText("前后摄并发拍照/录像诊断");root.addView(dual);
        dual.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("诊断会停止当前拍摄，并实际尝试前后摄并发拍照及两路5秒无声录像，文件保存在本机。请保持此页面打开，最长约55秒。是否开始？").setNegativeButton("取消",null).setPositiveButton("开始",(dialog,which)->{
            if(probeThread!=null){Toast.makeText(this,"诊断进行中",Toast.LENGTH_SHORT).show();return;}
            stopService(new Intent(this,CaptureService.class));dual.setEnabled(false);
            probe=null;probeThread=new HandlerThread("dual-probe");probeThread.start();Handler h=new Handler(probeThread.getLooper());
            h.postDelayed(()->{probe=new DualProbe(this,h,(ok,detail)->{
                getSharedPreferences("runtime",0).edit().putString("dual",detail).apply();
                h.getLooper().quitSafely();runOnUiThread(()->{probeThread=null;dual.setEnabled(true);if(!isFinishing())new AlertDialog.Builder(this).setTitle("并发诊断结果").setMessage(detail).setPositiveButton("关闭",null).show();});
            });probe.start();},1000);
        }).show());
        storage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int pos,long id){updateForm();}});
        task.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int pos,long id){updateForm();}});
        binding=false;updateForm();ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }
    private String diagnosticText(){
        String raw=getSharedPreferences("runtime",0).getString("dual","未测试");
        if(raw.contains("camera error 2")||raw.contains("错误2"))return "本次测试触发摄像头数量上限，未通过双摄并发。建议继续串行使用；若需复测，先关闭其他相机应用。\n\n"+raw.replace("camera error 2","摄像头数量上限（错误2）").replace("back:stopped","后摄：为结束测试而关闭").replace("front:","前摄：").replace("back:","后摄：");
        return raw;
    }
    private void cancelProbe(){
        if(probeThread==null)return;HandlerThread current=probeThread;Handler h=new Handler(current.getLooper());h.removeCallbacksAndMessages(null);
        h.post(()->{if(probe!=null)probe.finish(false,"诊断已取消");else{current.quitSafely();runOnUiThread(()->{probeThread=null;if(dualButton!=null)dualButton.setEnabled(true);});}});
    }
    @Override protected void onDestroy(){cancelProbe();super.onDestroy();}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){cancelProbe();handler.removeCallbacks(refresh);super.onPause();}
}
