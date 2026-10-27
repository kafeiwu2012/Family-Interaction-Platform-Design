package com.family.recorder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class ClipNamesTest {
    private static void require(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        TimeZone prior=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));File dir=Files.createTempDirectory("names-test").toFile();
        try{
            Calendar c=new GregorianCalendar(2026,Calendar.OCTOBER,27,13,12,5);c.set(Calendar.MILLISECOND,123);long now=c.getTimeInMillis();
            File rear=ClipNames.next(dir,false,now),front=ClipNames.next(dir,true,now);
            require(rear.getName().equals("后摄像头_20261027_131205_123.part"),"rear name includes readable local date and time");
            require(front.getName().equals("前摄像头_20261027_131205_123.part"),"front name explicit");
            File saved=new File(dir,rear.getName().replace(".part",".mp4"));saved.createNewFile();
            File second=ClipNames.next(dir,false,now);require(second.getName().endsWith("_123_1.part"),"same timestamp cannot overwrite completed video");
            second.createNewFile();require(ClipNames.next(dir,false,now).getName().endsWith("_123_2.part"),"partial collision protected");
            require(ClipNames.owned(saved.getName())&&ClipNames.owned(second.getName())&&ClipNames.owned("VR_12345_F.mp4"),"new and legacy formats recognized");
            require(!ClipNames.owned("holiday.mp4")&&!ClipNames.owned("后摄像头_notes.mp4")&&!ClipNames.owned("前摄像头_20261027_131205_123.mp4.lock"),"foreign files and markers not videos");
            try(RandomAccessFile f=new RandomAccessFile(saved,"rw")){f.setLength(100);}new File(saved.getPath()+".lock").createNewFile();
            LoopStore.prepare(dir,200,100,0);require(saved.exists(),"new-format locked video participates in quota without deletion");
            System.out.println("PASS: camera/date names, collision prevention, legacy ownership and new-format lock protection");
        }finally{for(File f:dir.listFiles())f.delete();dir.delete();TimeZone.setDefault(prior);}
    }
}
