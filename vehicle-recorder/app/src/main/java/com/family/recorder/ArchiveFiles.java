package com.family.recorder;

import java.io.*;
import java.util.*;

/** Batch archive operations only accept completed, owned videos directly inside the chosen source. */
final class ArchiveFiles {
    interface Progress { void update(int done,int total); }
    interface Cancel { boolean requested(); }
    static final class DeleteResult {
        int deleted,protectedFiles,missing,rejected,failed,cancelled;
    }
    static final class Source implements Closeable {
        final File file;
        final FileInputStream input;
        final long bytes;
        Source(File f,FileInputStream stream,long length){file=f;input=stream;bytes=length;}
        @Override public void close() throws IOException {input.close();}
    }
    private static File checked(File root,File file) throws IOException {
        File f=file.getCanonicalFile();
        if(f.getParentFile()==null||!f.getParentFile().equals(root.getCanonicalFile())||!ClipNames.owned(f.getName())||!f.getName().endsWith(".mp4"))throw new IOException("仅支持所选目录下已完成的本程序录像");
        return f;
    }
    static DeleteResult delete(File root,List<File> files,Progress progress){
        DeleteResult result=new DeleteResult();int done=0;
        for(File file:files){
            if(Thread.currentThread().isInterrupted()){result.cancelled=files.size()-done;break;}
            synchronized(LoopStore.class){
                try{
                    File f=checked(root,file);
                    if(!f.isFile())result.missing++;
                    else if(LoopStore.locked(f))result.protectedFiles++;
                    else if(f.delete())result.deleted++;
                    else result.failed++;
                }catch(IOException|SecurityException e){result.rejected++;}
            }
            done++;if(progress!=null)progress.update(done,files.size());
        }
        return result;
    }
    static Source open(File root,File file) throws IOException {
        synchronized(LoopStore.class){
            File f=checked(root,file);if(!f.isFile())throw new FileNotFoundException("录像已不存在，可能被循环清理");
            FileInputStream input=new FileInputStream(f);
            try{long size=input.getChannel().size();if(size<=0)throw new IOException("录像为空");return new Source(f,input,size);}catch(IOException e){input.close();throw e;}
        }
    }
    static long copy(Source source,OutputStream output) throws IOException {return copy(source,output,null);}
    static long copy(Source source,OutputStream output,Cancel cancel) throws IOException {
        byte[] buffer=new byte[64*1024];long bytes=0;int n;
        while((n=source.input.read(buffer))!=-1){
            if(Thread.currentThread().isInterrupted()||(cancel!=null&&cancel.requested()))throw new InterruptedIOException("导出已取消");
            output.write(buffer,0,n);bytes+=n;
        }
        if(Thread.currentThread().isInterrupted()||(cancel!=null&&cancel.requested()))throw new InterruptedIOException("导出已取消");
        output.flush();if(bytes!=source.bytes)throw new IOException("源录像长度发生变化，导出未完成");return bytes;
    }
    static File newDirectory(File parent,String prefix) throws IOException {
        File p=parent.getCanonicalFile();if(!p.isDirectory()&&!p.mkdirs())throw new IOException("无法创建导出目录");
        for(int i=0;i<1000;i++){File dir=new File(p,prefix+(i==0?"":"_"+i));if(dir.mkdir())return dir;if(!dir.exists())throw new IOException("无法创建导出子目录");}
        throw new IOException("导出目录重名过多");
    }
    static void copyToDirectory(File root,File file,File destination) throws IOException {copyToDirectory(root,file,destination,null);}
    static void copyToDirectory(File root,File file,File destination,Cancel cancel) throws IOException {
        try(Source source=open(root,file)){
            File target=new File(destination,source.file.getName());if(target.exists())throw new IOException("目标文件已存在，未覆盖");
            File temporary=File.createTempFile(".export-",".partial",destination);boolean saved=false;
            try{
                try(FileOutputStream output=new FileOutputStream(temporary)){copy(source,output,cancel);}
                if(target.exists()||!temporary.renameTo(target))throw new IOException("无法完成导出文件");saved=true;
            }finally{if(!saved)temporary.delete();}
        }
    }
}
