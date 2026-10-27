package com.family.recorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import java.io.File;

final class Config {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("recorder", 0); }
    static String defaultPath(Context c) {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VehicleRecorder").getAbsolutePath();
    }
    static File directory(Context c) { return new File(prefs(c).getString("path", defaultPath(c))); }
    static long quota(Context c) { return prefs(c).getInt("quota", 2048) * 1024L * 1024; }
    static int seconds(Context c) { return prefs(c).getInt("seconds", 180); }
    static int bitrate(Context c) { return prefs(c).getBoolean("hd", false) ? 8000000 : 4000000; }
    static java.util.Map<String,Object> recordingValues(Context c){
        SharedPreferences p=prefs(c);java.util.Map<String,Object> m=new java.util.HashMap<>();
        m.put("path",directory(c).getAbsolutePath());m.put("quota",p.getInt("quota",2048));m.put("seconds",seconds(c));m.put("hd",p.getBoolean("hd",false));m.put("fps",p.getInt("fps",30));m.put("rotation",p.getInt("rotation",0));m.put("audio",p.getBoolean("audio",true));m.put("stabilize",p.getBoolean("stabilize",true));m.put("torch",p.getBoolean("torch",false));return m;
    }
    static long reserve(Context c) { return (Config.bitrate(c) + 128000L) * Config.seconds(c) / 8 + 4 * 1024 * 1024; }
}
