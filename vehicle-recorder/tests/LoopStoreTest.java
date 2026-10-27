package com.family.recorder;
import java.io.*;
import java.nio.file.*;

public class LoopStoreTest {
    private static File clip(File dir,String name,int size,long time)throws Exception{
        File f=new File(dir,name);try(RandomAccessFile r=new RandomAccessFile(f,"rw")){r.setLength(size);}f.setLastModified(time);return f;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        File dir=Files.createTempDirectory("vehicle-store-test").toFile();
        try{
            File a=clip(dir,"VR_100_B.mp4",100,1000), b=clip(dir,"VR_200_F.mp4",100,2000), other=clip(dir,"holiday.mp4",100,0);
            LoopStore.prepare(dir,250,100,0);
            require(!a.exists()&&b.exists()&&other.exists(),"oldest owned segment alone must be evicted");
            File lock=new File(b.getPath()+".lock");lock.createNewFile();
            File c=clip(dir,"VR_300_B.part",100,3000);
            LoopStore.prepare(dir,250,100,0);
            require(b.exists()&&!c.exists(),"locked segment protected; abandoned partial counted and evicted");
            boolean refused=false;try{LoopStore.prepare(dir,150,100,0);}catch(IOException e){refused=true;}
            require(refused&&b.exists(),"locked saturation must stop safely");
            lock.delete();
            refused=false;try{LoopStore.prepare(dir,50,100,0);}catch(IOException e){refused=true;}
            require(refused&&b.exists(),"oversized reservation must fail before deleting anything");
            refused=false;try{LoopStore.prepare(dir,1000,100,Long.MAX_VALUE/2);}catch(IOException e){refused=true;}
            require(refused&&other.exists(),"physical disk floor must be enforced, unrelated files preserved");
            require(!LoopStore.owned(new File(dir,"VR_bad_B.mp4")),"restrict ownership pattern");
            System.out.println("PASS: oldest-first eviction, lock protection, partial accounting, quota/full disk failure, unrelated files");
        }finally{for(File f:dir.listFiles())f.delete();dir.delete();}
    }
}
