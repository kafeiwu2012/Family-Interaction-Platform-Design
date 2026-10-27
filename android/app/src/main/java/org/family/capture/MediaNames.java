package org.family.capture;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
final class MediaNames {
    static String create(String facing,String ext){
        return ("front".equals(facing)?"前摄":"后摄")+"_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS_Z",Locale.US).format(new Date())+"_"+UUID.randomUUID().toString().substring(0,8)+"."+ext;
    }
    static String display(File f){
        String n=f.getName();
        if(n.matches("[0-9]{10,16}_(front|back)_.*\\.jpg")){
            try {String[] p=n.split("_");return (p[1].equals("front")?"前摄":"后摄")+"_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS_Z",Locale.US).format(new Date(Long.parseLong(p[0])))+"_"+p[2];}catch(Exception ignored){}
        }
        return n;
    }
}
