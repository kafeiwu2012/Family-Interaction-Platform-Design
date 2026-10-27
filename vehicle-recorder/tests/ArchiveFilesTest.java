package com.family.recorder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class ArchiveFilesTest {
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static File clip(File root,String name,byte[] bytes)throws Exception{File f=new File(root,name);Files.write(f.toPath(),bytes);return f;}
    private static void clean(File f){if(f.isDirectory())for(File child:f.listFiles())clean(child);f.delete();}
    public static void main(String[] args)throws Exception{
        File temp=Files.createTempDirectory("archive-test").toFile(),root=new File(temp,"recordings"),parent=new File(temp,"exports");root.mkdir();parent.mkdir();
        try{
            byte[] bytes=new byte[180000];new Random(42).nextBytes(bytes);
            File locked=clip(root,"前摄像头_20261027_130000_001.mp4",bytes);new File(locked.getPath()+".lock").createNewFile();
            File ordinary=clip(root,"VR_123_B.mp4",bytes),partial=clip(root,"VR_124_B.part",bytes),foreign=clip(root,"holiday.mp4",bytes),outside=clip(temp,"VR_125_B.mp4",bytes);
            ArchiveFiles.DeleteResult result=ArchiveFiles.delete(root,Arrays.asList(locked,partial,foreign,outside),null);
            require(result.protectedFiles==1&&result.rejected==3&&locked.exists()&&partial.exists()&&foreign.exists()&&outside.exists(),"batch deletion protects locks, partials, foreign names and outside-directory videos");
            File exported=ArchiveFiles.newDirectory(parent,"录像导出_测试");ArchiveFiles.copyToDirectory(root,locked,exported);
            require(Arrays.equals(Files.readAllBytes(new File(exported,locked.getName()).toPath()),bytes),"export bytes intact");require(locked.exists()&&LoopStore.locked(locked),"export preserves original and protection marker");
            File another=ArchiveFiles.newDirectory(parent,"录像导出_测试");require(!another.equals(exported),"duplicate export folder cannot overwrite a prior batch");
            AtomicInteger checks=new AtomicInteger();boolean cancelled=false;
            try{ArchiveFiles.copyToDirectory(root,ordinary,another,()->checks.incrementAndGet()>=2);}catch(InterruptedIOException e){cancelled=true;}
            require(cancelled&&another.list().length==0&&ordinary.exists(),"cancel cleans incomplete local files without deleting source");
            File collision=clip(another,ordinary.getName(),new byte[]{7,8,9});boolean refused=false;
            try{ArchiveFiles.copyToDirectory(root,ordinary,another);}catch(IOException e){refused=true;}
            require(refused&&Arrays.equals(Files.readAllBytes(collision.toPath()),new byte[]{7,8,9}),"existing export never overwritten");
            boolean failed=false;
            try(ArchiveFiles.Source source=ArchiveFiles.open(root,ordinary)){
                try{ArchiveFiles.copy(source,new OutputStream(){public void write(int n)throws IOException{throw new IOException("disk full");}public void write(byte[] b,int off,int n)throws IOException{throw new IOException("disk full");}});}catch(IOException e){failed=true;}
            }
            require(failed&&ordinary.exists()&&ordinary.length()==bytes.length,"write failure leaves original untouched");
            try(ArchiveFiles.Source source=ArchiveFiles.open(root,ordinary)){
                ArchiveFiles.DeleteResult removed=ArchiveFiles.delete(root,Collections.singletonList(ordinary),null);require(removed.deleted==1,"unprotected completed file can be deleted");
                ByteArrayOutputStream copy=new ByteArrayOutputStream();ArchiveFiles.copy(source,copy);require(Arrays.equals(copy.toByteArray(),bytes),"already-open export survives source unlink while cycling");
            }
            ArchiveFiles.DeleteResult gone=ArchiveFiles.delete(root,Collections.singletonList(ordinary),null);require(gone.missing==1,"missing files counted accurately");
            try{ArchiveFiles.open(root,outside);throw new AssertionError("out of directory export allowed");}catch(IOException expected){}
            System.out.println("PASS: safe batch deletion, exact export copies, no overwrite, cancellation cleanup, write failure and loop-deletion race");
        }finally{clean(temp);}
    }
}
