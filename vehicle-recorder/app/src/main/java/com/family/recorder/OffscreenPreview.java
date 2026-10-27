package com.family.recorder;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;
import java.nio.*;

/** Camera texture stays service-owned. Visible frames go directly to an EGL window, never to CPU. */
final class OffscreenPreview {
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface drain=EGL14.EGL_NO_SURFACE, window=EGL14.EGL_NO_SURFACE;
    private EGLConfig config;
    private int program,texture,position,uv,transform,projection;
    final SurfaceTexture input;
    private Surface output;
    private int width,height,inputWidth,inputHeight,rotation,fps;
    private long last;
    private boolean closed,fill=true;
    private final float[] texMatrix=new float[16],viewMatrix=new float[16];
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
    OffscreenPreview(Handler handler,int w,int h,int angle,int previewFps) {
        inputWidth=w;inputHeight=h;rotation=angle;fps=previewFps;
        try {
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] v=new int[2];
            if(!EGL14.eglInitialize(display,v,0,v,1))throw new IllegalStateException("EGL 初始化失败");
            EGLConfig[] cs=new EGLConfig[1];int[] n=new int[1];
            EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RENDERABLE_TYPE,4,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT|EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE},0,cs,0,1,n,0);
            if(n[0]==0)throw new IllegalStateException("无可用预览配置");config=cs[0];
            context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            drain=EGL14.eglCreatePbufferSurface(display,config,new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            makeDrain();int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);texture=ids[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            program=GLES20.glCreateProgram();
            int vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p; attribute vec2 t; uniform mat4 m; uniform mat4 r; varying vec2 uv; void main(){gl_Position=r*vec4(p,0.,1.);uv=(m*vec4(t,0.,1.)).xy;}");
            int fs=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 uv; uniform samplerExternalOES tex; void main(){gl_FragColor=texture2D(tex,uv);}");
            GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
            int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);if(linked[0]==0)throw new IllegalStateException("预览着色器连接失败");
            position=GLES20.glGetAttribLocation(program,"p");uv=GLES20.glGetAttribLocation(program,"t");transform=GLES20.glGetUniformLocation(program,"m");projection=GLES20.glGetUniformLocation(program,"r");
            vertices.put(new float[]{-1,-1,0,0,1,-1,1,0,-1,1,0,1,1,1,1,1}).position(0);
            input=new SurfaceTexture(texture);input.setDefaultBufferSize(w,h);
        }catch(RuntimeException e){releaseEgl();throw e;}
        input.setOnFrameAvailableListener(st->{
            if(closed)return;
            try{
                makeDrain();st.updateTexImage();
                long now=SystemClock.elapsedRealtime();
                if(window==EGL14.EGL_NO_SURFACE||now-last<1000/fps-3)return;last=now;
                if(!EGL14.eglMakeCurrent(display,window,window,context)) {setOutput(null,0,0);return;}
                st.getTransformMatrix(texMatrix);GLES20.glViewport(0,0,width,height);GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glUseProgram(program);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
                vertices.position(0);GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(position);
                vertices.position(2);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(uv);
                GLES20.glUniformMatrix4fv(transform,1,false,texMatrix,0);GLES20.glUniformMatrix4fv(projection,1,false,viewMatrix,0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                if(!EGL14.eglSwapBuffers(display,window))setOutput(null,0,0);
            }catch(RuntimeException e){try{if(!closed)setOutput(null,0,0);}catch(RuntimeException ignored){}} // A destroyed UI surface must not stop the video encoder.
        },handler);
    }
    void setStyle(int angle,boolean crop){rotation=angle;fill=crop;updateProjection();}
    private void updateProjection(){
        float[] scale=OrientationMath.scale(inputWidth,inputHeight,rotation,width,height,fill);
        Matrix.setIdentityM(viewMatrix,0);Matrix.scaleM(viewMatrix,0,scale[0],scale[1],1);Matrix.rotateM(viewMatrix,0,-rotation,0,0,1);
    }
    void setFrameRate(int value){fps=value==15?15:30;}
    void setOutput(Surface surface,int w,int h){
        makeDrain();if(window!=EGL14.EGL_NO_SURFACE){EGL14.eglDestroySurface(display,window);window=EGL14.EGL_NO_SURFACE;}output=surface;
        if(output==null||!output.isValid()||w<=0||h<=0)return;
        width=w;height=h;
        try{window=EGL14.eglCreateWindowSurface(display,config,output,new int[]{EGL14.EGL_NONE},0);}catch(RuntimeException e){window=EGL14.EGL_NO_SURFACE;return;}
        if(window==EGL14.EGL_NO_SURFACE)return;
        EGL14.eglMakeCurrent(display,window,window,context);EGL14.eglSwapInterval(display,0);
        updateProjection();
        makeDrain();
    }
    private void makeDrain(){if(!EGL14.eglMakeCurrent(display,drain,drain,context))throw new IllegalStateException("预览上下文不可用");}
    private int shader(int type,String source){int id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);int[] ok=new int[1];GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(id));return id;}
    void close(){if(closed)return;closed=true;input.setOnFrameAvailableListener(null);input.release();releaseEgl();}
    private void releaseEgl(){
        if(display==EGL14.EGL_NO_DISPLAY)return;
        if(context!=EGL14.EGL_NO_CONTEXT&&drain!=EGL14.EGL_NO_SURFACE){EGL14.eglMakeCurrent(display,drain,drain,context);GLES20.glDeleteProgram(program);GLES20.glDeleteTextures(1,new int[]{texture},0);}
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);
        if(drain!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,drain);
        if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
        EGL14.eglTerminate(display);display=EGL14.EGL_NO_DISPLAY;
    }
}
