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
    static long reserve(Context c) { return (Config.bitrate(c) + 128000L) * Config.seconds(c) / 8 + 4 * 1024 * 1024; }
}
