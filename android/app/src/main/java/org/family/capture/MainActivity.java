package org.family.capture;

import android.app.*;
import android.os.*;
import android.content.*;
import android.widget.*;
import android.text.InputType;

public class MainActivity extends Activity {
    EditText url, token, interval, photoWindow, videoSeconds, restSeconds;
    CheckBox cycle;
    HandlerThread probeThread;
    DualProbe probe;
    Button dualButton;
    CheckBox local;
    Spinner lens;
    TextView status;
    final Handler handler=new Handler();
    final Runnable refresh=new Runnable(){ public void run() {
        status.setText(getSharedPreferences("runtime",0).getString("status","尚未启动"));
        handler.postDelayed(this,1000);
    }};
    EditText field(LinearLayout root,String label,String value) {
        TextView title=new TextView(this); title.setText(label); root.addView(title);
        EditText e=new EditText(this); e.setText(value); e.setSingleLine(true); root.addView(e); return e;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences p=getSharedPreferences("config",0);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24);
        TextView notice=new TextView(this);
        notice.setText("仅在有权拍摄的场所使用，并告知在场人员。运行时显示常驻通知；本机可以随时停止。熄屏效果需真机测试。不会开机自启。支持本地无声短录像，镜头默认串行使用。"); root.addView(notice);
        local=new CheckBox(this); local.setText("本地测试：无需服务器，不上传照片"); local.setChecked(p.getBoolean("local",true)); root.addView(local);
        url=field(root,"HTTPS 服务地址（上传模式使用）",p.getString("url",""));
        token=field(root,"设备令牌（上传模式使用）",p.getString("token","")); token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        local.setOnCheckedChangeListener((b,checked)->{url.setEnabled(!checked);token.setEnabled(!checked);});
        url.setEnabled(!local.isChecked()); token.setEnabled(!local.isChecked());
        interval=field(root,"拍照间隔（10–86400秒）",String.valueOf(p.getInt("interval",60))); interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        cycle=new CheckBox(this);cycle.setText("本地循环：拍照时段 → 短录像 → 等待 → 重复");cycle.setChecked(p.getBoolean("cycle",false));root.addView(cycle);
        photoWindow=field(root,"每轮拍照时段（10–86400秒）",String.valueOf(p.getInt("photoWindow",60)));
        videoSeconds=field(root,"每轮录像时长（2–60秒，默认5秒，无声音）",String.valueOf(p.getInt("videoSeconds",5)));
        restSeconds=field(root,"录像完成后等待（0–86400秒）",String.valueOf(p.getInt("restSeconds",10)));
        photoWindow.setInputType(InputType.TYPE_CLASS_NUMBER);videoSeconds.setInputType(InputType.TYPE_CLASS_NUMBER);restSeconds.setInputType(InputType.TYPE_CLASS_NUMBER);
        TextView label=new TextView(this); label.setText("本地镜头选择（运行中切换，下一张生效）"); root.addView(label);
        lens=new Spinner(this); lens.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"后置摄像头","前置摄像头"}));
        lens.setSelection("front".equals(p.getString("facing","back"))?1:0); root.addView(lens);
        lens.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> a) {}
            public void onItemSelected(android.widget.AdapterView<?> a,android.view.View v,int pos,long id) { p.edit().putString("facing",pos==1?"front":"back").apply(); }
        });
        Button start=new Button(this); start.setText("同意并启动定时拍照");root.addView(start);
        start.setOnClickListener(v->{
            if(probeThread!=null){Toast.makeText(this,"请等待并发诊断结束",Toast.LENGTH_SHORT).show();return;}
            int seconds,window,clip,rest;
            try {seconds=Integer.parseInt(interval.getText().toString());window=Integer.parseInt(photoWindow.getText().toString());clip=Integer.parseInt(videoSeconds.getText().toString());rest=Integer.parseInt(restSeconds.getText().toString());}catch(Exception e){Toast.makeText(this,"请输入有效整数",Toast.LENGTH_LONG).show();return;}
            if(window<10||window>86400||clip<2||clip>60||rest<0||rest>86400){Toast.makeText(this,"拍照时段10–86400秒，录像2–60秒，等待0–86400秒",Toast.LENGTH_LONG).show();return;}
            if(cycle.isChecked()&&!local.isChecked()){Toast.makeText(this,"短录像循环目前仅支持本地模式",Toast.LENGTH_LONG).show();return;}
            String u=url.getText().toString().trim(),t=token.getText().toString().trim();
            if(seconds<10||seconds>86400||(!local.isChecked()&&(!u.startsWith("https://")||t.length()<32))) {
                Toast.makeText(this,"间隔需10–86400秒；上传模式还需HTTPS地址及至少32字符令牌",Toast.LENGTH_LONG).show();return;
            }
            p.edit().putBoolean("cycle",cycle.isChecked()).putInt("photoWindow",window).putInt("videoSeconds",clip).putInt("restSeconds",rest).putBoolean("local",local.isChecked()).putString("url",u.replaceAll("/+$","")).putString("token",t).putInt("interval",seconds).apply();
            startService(new Intent(this,CaptureService.class));
            Toast.makeText(this,"模式或间隔变更需先停止再启动",Toast.LENGTH_SHORT).show();
        });
        Button stop=new Button(this); stop.setText("立即停止");root.addView(stop);stop.setOnClickListener(v->{stopService(new Intent(this,CaptureService.class));cancelProbe();});
        status=new TextView(this);root.addView(status);
        Button photos=new Button(this);photos.setText("本地照片/视频 · 批量管理/导出");root.addView(photos);photos.setOnClickListener(v->startActivity(new Intent(this,PhotosActivity.class)));
        Button caps=new Button(this);caps.setText("查看摄像头能力");root.addView(caps);caps.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Camera2 能力报告").setMessage(CameraEngine.report(this)+"\n最近并发媒体测试："+getSharedPreferences("runtime",0).getString("dual","未测试")).setPositiveButton("关闭",null).show());
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
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }
    private void cancelProbe(){
        if(probeThread==null)return;
        HandlerThread current=probeThread;Handler h=new Handler(current.getLooper());
        h.removeCallbacksAndMessages(null);
        h.post(()->{if(probe!=null)probe.finish(false,"诊断已取消");else{current.quitSafely();runOnUiThread(()->{probeThread=null;if(dualButton!=null)dualButton.setEnabled(true);});}});
    }
    @Override protected void onDestroy(){cancelProbe();super.onDestroy();}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){cancelProbe();handler.removeCallbacks(refresh);super.onPause();}
}
