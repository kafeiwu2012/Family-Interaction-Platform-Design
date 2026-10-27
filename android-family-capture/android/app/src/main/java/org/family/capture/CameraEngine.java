package org.family.capture;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.*;
import android.os.*;
import android.util.Size;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class CameraEngine {
    interface Result { void done(boolean ok, String detail); }
    private final Context context;
    private final Handler handler;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private Runnable timeout;
    private AtomicBoolean completed = new AtomicBoolean(true);
    private Result result;
    private int generation=0;
    CameraEngine(Context c, Handler h) { context=c; handler=h; }
    static String report(Context c) {
        StringBuilder out=new StringBuilder("本版仅允许一个摄像头工作；支持按时间串行交替，已移除双摄并发。\n");
        try {
            CameraManager m=(CameraManager)c.getSystemService(Context.CAMERA_SERVICE);
            for(String id:m.getCameraIdList()) {
                CameraCharacteristics k=m.getCameraCharacteristics(id);
                Integer facing=k.get(CameraCharacteristics.LENS_FACING),level=k.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                String direction=Integer.valueOf(CameraCharacteristics.LENS_FACING_FRONT).equals(facing)?"前摄":"后摄";
                String hardware=Integer.valueOf(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY).equals(level)?"LEGACY（兼容模式）":String.valueOf(level);
                out.append(direction).append(" · ID ").append(id).append(" · ").append(hardware).append('\n');
            }
        } catch(Exception e) { out.append(e.getClass().getSimpleName()); }
        return out.toString();
    }
    void capture(String facing, File output, Result callback) {
        if(!completed.get()) { callback.done(false,"camera busy"); return; }
        completed=new AtomicBoolean(false); result=callback;
        final int attempt=++generation;
        timeout=()->finish(false,"camera timeout"); handler.postDelayed(timeout,20000);
        try {
            CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
            String selected=null;
            int desired="front".equals(facing)?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK;
            for(String id:manager.getCameraIdList()) {
                if(Integer.valueOf(desired).equals(manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING))) { selected=id; break; }
            }
            if(selected==null) throw new IOException("requested camera unavailable");
            CameraCharacteristics characteristics=manager.getCameraCharacteristics(selected);
            Size[] sizes=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            if(sizes==null || sizes.length==0) throw new IOException("JPEG unavailable");
            Arrays.sort(sizes,(a,b)->Long.compare((long)a.getWidth()*a.getHeight(),(long)b.getWidth()*b.getHeight()));
            Size size=sizes[0];
            for(Size s:sizes) if((long)s.getWidth()*s.getHeight()<=1920L*1080) size=s;
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.JPEG,2);
            reader.setOnImageAvailableListener(r->{
                Image image=null;
                try {
                    image=r.acquireLatestImage(); if(image==null) return;
                    if(attempt!=generation) return;
                    if(completed.get()) return;
                    ByteBuffer buffer=image.getPlanes()[0].getBuffer(); byte[] bytes=new byte[buffer.remaining()]; buffer.get(bytes);
                    File temp=new File(output.getPath()+".part");
                    try(FileOutputStream stream=new FileOutputStream(temp)) { stream.write(bytes); stream.getFD().sync(); }
                    if(!temp.renameTo(output)) throw new IOException("rename failed");
                    image.close(); image=null;
                    finish(true,"photo queued");
                } catch(Exception e) { finish(false,e.getClass().getSimpleName()); }
                finally { if(image!=null) image.close(); }
            },handler);
            manager.openCamera(selected,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice camera) {
                    if(attempt!=generation || completed.get()) { camera.close(); return; } device=camera;
                    try {
                        camera.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                            @Override public void onConfigured(CameraCaptureSession s) {
                                if(attempt!=generation || completed.get()) { s.close(); return; } session=s;
                                try {
                                    CaptureRequest.Builder b=device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                    b.addTarget(reader.getSurface()); b.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                                    b.set(CaptureRequest.JPEG_ORIENTATION,characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
                                    Runnable shoot=()->{if(attempt!=generation||completed.get())return;try{s.capture(b.build(),new CameraCaptureSession.CaptureCallback(){
                                        @Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f) { if(attempt==generation) finish(false,"capture failed"); }
                                    },handler);}catch(Exception e){finish(false,e.toString());}};
                                    shoot.run();
                                } catch(Exception e) { finish(false,e.getClass().getSimpleName()); }
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession s) { s.close(); if(attempt==generation) finish(false,"session failed"); }
                        },handler);
                    } catch(Exception e) { finish(false,e.getClass().getSimpleName()); }
                }
                @Override public void onDisconnected(CameraDevice c) { c.close(); if(attempt==generation) finish(false,"camera disconnected"); }
                @Override public void onError(CameraDevice c,int error) { c.close(); if(attempt==generation) finish(false,cameraError(error)); }
            },handler);
        } catch(Exception e) { finish(false,e.getClass().getSimpleName()); }
    }
    private void finish(boolean ok,String detail) {
        if(!completed.compareAndSet(false,true)) return;
        handler.removeCallbacks(timeout);
        if(session!=null) { session.close(); session=null; }
        if(device!=null) { device.close(); device=null; }
        if(reader!=null) { reader.close(); reader=null; }
        Result cb=result; result=null; cb.done(ok,detail);
    }
    static String cameraError(int error){
        if(error==CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE)return "同时可用摄像头已达上限（错误2），请串行使用前后摄";
        if(error==CameraDevice.StateCallback.ERROR_CAMERA_IN_USE)return "摄像头被占用（错误1），请关闭其他相机应用后重试";
        return "摄像头错误 "+error;
    }
    void close() { if(!completed.get()) finish(false,"stopped"); }
}
