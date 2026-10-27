package com.family.recorder;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;
final class ClipNames {
    private static final Pattern OWNED=Pattern.compile("(?:VR_[0-9]+_[FB]|(?:前摄像头|后摄像头)_[0-9]{8}_[0-9]{6}_[0-9]{3}(?:_[0-9]+)?)\\.(?:mp4|part)");
    static boolean owned(String name){return OWNED.matcher(name).matches();}
    static File next(File dir,boolean front,long millis){
        String base=(front?"前摄像头":"后摄像头")+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.CHINA).format(new Date(millis));
        for(int i=0;;i++){String stem=base+(i==0?"":"_"+i);File f=new File(dir,stem+".part");if(!f.exists()&&!new File(dir,stem+".mp4").exists()&&!new File(dir,stem+".part.lock").exists()&&!new File(dir,stem+".mp4.lock").exists())return f;}
    }
}
