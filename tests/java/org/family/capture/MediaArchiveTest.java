package org.family.capture;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public final class MediaArchiveTest {
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        Path dir=Files.createTempDirectory("media-export-");
        try {
            Path old=dir.resolve("1793065034621_front_347f8561-171f-4b2d-bff5-ca367c4cd22a.jpg");
            Path video=dir.resolve(MediaNames.create("back","mp4"));
            byte[] content=new byte[250000];new Random(7).nextBytes(content);
            Files.write(old,content);Files.write(video,new byte[]{1,2,3,4});
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(ZipOutputStream zip=new ZipOutputStream(bytes)){MediaArchive.write(zip,Arrays.asList(old.toString(),video.toString()));}
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
                ZipEntry first=zip.getNextEntry();
                if(first==null||!first.getName().startsWith("前摄_2026-")||first.getName().contains("/"))throw new AssertionError("legacy name");
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=zip.read(b))!=-1)out.write(b,0,n);
                if(!Arrays.equals(content,out.toByteArray()))throw new AssertionError("export corrupted");
                ZipEntry second=zip.getNextEntry();if(second==null||!second.getName().startsWith("后摄_")||!second.getName().endsWith(".mp4"))throw new AssertionError("video name");
                if(!Files.exists(old)||!Files.exists(video))throw new AssertionError("source lost");
            }
            boolean failed=false;
            try(ZipOutputStream zip=new ZipOutputStream(new ByteArrayOutputStream())){MediaArchive.write(zip,Arrays.asList(dir.resolve("missing.jpg").toString()));}catch(IOException e){failed=true;}
            if(!failed)throw new AssertionError("missing file must fail");
            System.out.println("PASS: legacy names, Unicode ZIP, large file integrity, video entry, preserved originals, missing file failure");
        } finally {try(java.util.stream.Stream<Path> files=Files.list(dir)){files.forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}Files.delete(dir);}
    }
}
