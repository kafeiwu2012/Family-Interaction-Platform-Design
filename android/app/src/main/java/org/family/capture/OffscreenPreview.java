package org.family.capture;
import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
/** Drain the legacy preview buffer on a valid EGL context, including while screen is off. */
final class OffscreenPreview {
    private android.opengl.EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private android.opengl.EGLContext context=EGL14.EGL_NO_CONTEXT;
    private android.opengl.EGLSurface surface=EGL14.EGL_NO_SURFACE;
    SurfaceTexture texture;int name;
    OffscreenPreview(Handler handler,int width,int height){
        try{
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] version=new int[2];
            if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("EGL init");
            android.opengl.EGLConfig[] configs=new android.opengl.EGLConfig[1];int[] count=new int[1];
            int[] attrs={EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE};
            if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)||count[0]==0)throw new IllegalStateException("EGL config");
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(display,surface,surface,context))throw new IllegalStateException("EGL current");
            int[] names=new int[1];GLES20.glGenTextures(1,names,0);name=names[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,name);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            texture=new SurfaceTexture(name);texture.setDefaultBufferSize(width,height);
            texture.setOnFrameAvailableListener(t->{try{if(texture!=null)t.updateTexImage();}catch(RuntimeException ignored){}},handler);
        }catch(RuntimeException e){close();throw e;}
    }
    void close(){
        if(texture!=null){texture.setOnFrameAvailableListener(null);texture.release();texture=null;}
        if(display!=EGL14.EGL_NO_DISPLAY){
            if(name!=0)GLES20.glDeleteTextures(1,new int[]{name},0);
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglTerminate(display);display=EGL14.EGL_NO_DISPLAY;
        }
    }
}
