package org.family.capture;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.widget.*;
import android.text.InputType;

public class MainActivity extends Activity {
    EditText url, token, interval;
    EditText field(LinearLayout root, String label, String value) {
        TextView title = new TextView(this); title.setText(label); root.addView(title);
        EditText e = new EditText(this); e.setText(value); e.setSingleLine(true); root.addView(e); return e;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        android.content.SharedPreferences p = getSharedPreferences("config", 0);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24);
        TextView notice = new TextView(this);
        notice.setText("仅在你有权拍摄的场所使用，并告知在场人员。启动后将显示常驻通知；锁屏期间继续定时拍照。不会开机自启。远程控制仅在本机启动期间生效。当前版本不支持录像。 "); root.addView(notice);
        url = field(root,"HTTPS 服务地址（不要以 / 结尾）",p.getString("url",""));
        token = field(root,"此设备独立令牌",p.getString("token","")); token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        interval = field(root,"拍照间隔（秒，最少 10）",String.valueOf(p.getInt("interval",60))); interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        Button start = new Button(this); start.setText("同意并启动定时拍照"); root.addView(start);
        start.setOnClickListener(v -> {
            String u = url.getText().toString().trim(); String t = token.getText().toString().trim();
            int seconds;
            try { seconds = Integer.parseInt(interval.getText().toString()); } catch(Exception e) { seconds=0; }
            if (!u.startsWith("https://") || t.length()<32 || seconds<10 || seconds>86400) {
                Toast.makeText(this,"请输入 HTTPS 地址、至少32字符令牌及10–86400秒间隔",Toast.LENGTH_LONG).show(); return;
            }
            p.edit().putString("url",u.replaceAll("/+$", "")).putString("token",t).putInt("interval",seconds).apply();
            startService(new Intent(this,CaptureService.class));
        });
        Button stop = new Button(this); stop.setText("立即停止"); root.addView(stop);
        stop.setOnClickListener(v -> stopService(new Intent(this,CaptureService.class)));
        Button caps = new Button(this); caps.setText("查看摄像头能力"); root.addView(caps);
        caps.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Camera2 能力报告").setMessage(CameraEngine.report(this)).setPositiveButton("关闭",null).show());
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
    }
}
