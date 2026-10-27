package org.family.capture;

import android.app.*;
import android.os.*;
import android.content.*;
import android.widget.*;
import android.text.InputType;

public class MainActivity extends Activity {
    EditText url, token, interval;
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
        notice.setText("仅在有权拍摄的场所使用，并告知在场人员。运行时显示常驻通知；本机可以随时停止。熄屏效果需真机测试。不会开机自启，当前不支持录像。"); root.addView(notice);
        local=new CheckBox(this); local.setText("本地测试：无需服务器，不上传照片"); local.setChecked(p.getBoolean("local",true)); root.addView(local);
        url=field(root,"HTTPS 服务地址（上传模式使用）",p.getString("url",""));
        token=field(root,"设备令牌（上传模式使用）",p.getString("token","")); token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        local.setOnCheckedChangeListener((b,checked)->{url.setEnabled(!checked);token.setEnabled(!checked);});
        url.setEnabled(!local.isChecked()); token.setEnabled(!local.isChecked());
        interval=field(root,"拍照间隔（10–86400秒）",String.valueOf(p.getInt("interval",60))); interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        TextView label=new TextView(this); label.setText("本地镜头选择（运行中切换，下一张生效）"); root.addView(label);
        lens=new Spinner(this); lens.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"后置摄像头","前置摄像头"}));
        lens.setSelection("front".equals(p.getString("facing","back"))?1:0); root.addView(lens);
        lens.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> a) {}
            public void onItemSelected(android.widget.AdapterView<?> a,android.view.View v,int pos,long id) { p.edit().putString("facing",pos==1?"front":"back").apply(); }
        });
        Button start=new Button(this); start.setText("同意并启动定时拍照");root.addView(start);
        start.setOnClickListener(v->{
            int seconds; try {seconds=Integer.parseInt(interval.getText().toString());}catch(Exception e){seconds=0;}
            String u=url.getText().toString().trim(),t=token.getText().toString().trim();
            if(seconds<10||seconds>86400||(!local.isChecked()&&(!u.startsWith("https://")||t.length()<32))) {
                Toast.makeText(this,"间隔需10–86400秒；上传模式还需HTTPS地址及至少32字符令牌",Toast.LENGTH_LONG).show();return;
            }
            p.edit().putBoolean("local",local.isChecked()).putString("url",u.replaceAll("/+$","")).putString("token",t).putInt("interval",seconds).apply();
            startService(new Intent(this,CaptureService.class));
            Toast.makeText(this,"模式或间隔变更需先停止再启动",Toast.LENGTH_SHORT).show();
        });
        Button stop=new Button(this); stop.setText("立即停止");root.addView(stop);stop.setOnClickListener(v->stopService(new Intent(this,CaptureService.class)));
        status=new TextView(this);root.addView(status);
        Button photos=new Button(this);photos.setText("查看本地测试照片");root.addView(photos);photos.setOnClickListener(v->startActivity(new Intent(this,PhotosActivity.class)));
        Button caps=new Button(this);caps.setText("查看摄像头能力");root.addView(caps);caps.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Camera2 能力报告").setMessage(CameraEngine.report(this)).setPositiveButton("关闭",null).show());
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
