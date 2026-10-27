package com.family.recorder;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.SystemClock;
import java.nio.*;

/** Service-owned EGL surface survives Activity destruction and the power button. */
final class OffscreenPreview {
    private EGLDisplay display;
    private EGLContext context;
    private EGLSurface surface;
    private final int program;
    private final int texture;
    final SurfaceTexture input;
    private long last;
    private final FloatBuffer vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final ByteBuffer pixels = ByteBuffer.allocateDirect(640 * 360 * 4).order(ByteOrder.nativeOrder());
    interface Frame { void accept(Bitmap image); }
    OffscreenPreview(Handler handler, Frame consumer) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] v = new int[2];
        if (!EGL14.eglInitialize(display, v, 0, v, 1)) throw new IllegalStateException("EGL 初始化失败");
        EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
        EGL14.eglChooseConfig(display, new int[]{EGL14.EGL_RENDERABLE_TYPE,4,EGL14.EGL_SURFACE_TYPE,1,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE},0,configs,0,1,count,0);
        if (count[0] == 0) throw new IllegalStateException("无离屏预览配置");
        context = EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        surface = EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,640,EGL14.EGL_HEIGHT,360,EGL14.EGL_NONE},0);
        if (!EGL14.eglMakeCurrent(display,surface,surface,context)) throw new IllegalStateException("离屏预览不可用");
        int[] ids = new int[1]; GLES20.glGenTextures(1,ids,0); texture=ids[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        program=GLES20.glCreateProgram();
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p; attribute vec2 t; uniform mat4 m; varying vec2 uv; void main(){gl_Position=vec4(p,0.,1.);uv=(m*vec4(t,0.,1.)).xy;}"));
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 uv; uniform samplerExternalOES tex; void main(){gl_FragColor=texture2D(tex,uv);}"));
        GLES20.glLinkProgram(program);
        vertices.put(new float[]{-1,-1,0,1, 1,-1,1,1, -1,1,0,0, 1,1,1,0}).position(0);
        input=new SurfaceTexture(texture);
        input.setOnFrameAvailableListener(st -> {
            try {
                st.updateTexImage();
                if (SystemClock.elapsedRealtime()-last < 500 || !MainActivity.visible) return;
                last=SystemClock.elapsedRealtime();
                float[] m=new float[16]; st.getTransformMatrix(m);
                GLES20.glViewport(0,0,640,360); GLES20.glUseProgram(program);
                int p=GLES20.glGetAttribLocation(program,"p"), t=GLES20.glGetAttribLocation(program,"t");
                vertices.position(0); GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,16,vertices); GLES20.glEnableVertexAttribArray(p);
                vertices.position(2); GLES20.glVertexAttribPointer(t,2,GLES20.GL_FLOAT,false,16,vertices); GLES20.glEnableVertexAttribArray(t);
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"m"),1,false,m,0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                pixels.position(0); GLES20.glReadPixels(0,0,640,360,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
                Bitmap bitmap=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888); pixels.position(0); bitmap.copyPixelsFromBuffer(pixels); consumer.accept(bitmap);
            } catch (RuntimeException ignored) { /* Recorder still owns its camera; status handled by recorder callbacks. */ }
        },handler);
    }
    private int shader(int type,String source) {
        int id=GLES20.glCreateShader(type); GLES20.glShaderSource(id,source); GLES20.glCompileShader(id);
        int[] ok=new int[1]; GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(id)); return id;
    }
    void close() {
        input.setOnFrameAvailableListener(null); input.release(); GLES20.glDeleteProgram(program); GLES20.glDeleteTextures(1,new int[]{texture},0);
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,context); EGL14.eglTerminate(display);
    }
}
