package com.family.recorder;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Short-lived read grants allow an installed video player to open a segment. */
public final class ClipProvider extends ContentProvider {
    static Uri uri(File f){return new Uri.Builder().scheme("content").authority("com.family.recorder.clips").appendPath(f.getAbsolutePath()).build();}
    private File resolve(Uri uri) throws FileNotFoundException {
        try {
            if(uri.getPathSegments().size()!=1)throw new IOException();
            File f=new File(uri.getLastPathSegment()).getCanonicalFile();
            boolean allowed=f.getParentFile().equals(Config.directory(getContext()).getCanonicalFile());
            for(String p:Config.prefs(getContext()).getString("history","").split("\n"))if(!p.isEmpty()&&f.getParentFile().equals(new File(p).getCanonicalFile()))allowed=true;
            if(!allowed||!LoopStore.owned(f)||!f.getName().endsWith(".mp4"))throw new IOException();return f;
        }catch(Exception e){throw new FileNotFoundException("不可访问此录像");}
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "video/mp4";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {if(!"r".equals(mode))throw new FileNotFoundException("只读");return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File f=resolve(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]=f.getName();else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=f.length();}c.addRow(values);return c;}catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
