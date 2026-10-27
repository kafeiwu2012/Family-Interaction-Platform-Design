package org.family.capture;
import java.io.*;
import java.util.*;
import java.util.zip.*;
/** Streaming export: never deletes source files or buffers whole media files. */
final class MediaArchive {
    static void write(ZipOutputStream zip,List<String> paths) throws IOException {
        byte[] buffer=new byte[32768];Set<String> used=new HashSet<>();
        for(String path:paths){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("export interrupted");
            File file=new File(path);String base=MediaNames.display(file),name=base;int suffix=1;
            while(!used.add(name))name=(suffix++)+"_"+base;
            zip.putNextEntry(new ZipEntry(name));
            try(InputStream in=new FileInputStream(file)){
                int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("export interrupted");zip.write(buffer,0,n);}
            }zip.closeEntry();
        }
    }
}
