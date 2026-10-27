package com.family.recorder;

/** Camera API 1 sensor orientation corrected for the chosen display rotation. */
final class OrientationMath {
    static int normalize(int degrees){return (degrees%360+360)%360;}
    static int rotation(int sensor,boolean front,int display,int correction){return normalize(sensor+(front?display:-display)+correction);}
    // Front preview needs mirror compensation; MP4 orientation metadata does not.
    static int previewRotation(int sensor,boolean front,int display,int correction){
        int raw=rotation(sensor,front,display,0);
        return normalize((front?-raw:raw)+correction);
    }
    static int displayDegrees(int surfaceRotation){return new int[]{0,90,180,270}[Math.max(0,Math.min(3,surfaceRotation))];}
    static float[] scale(int width,int height,int rotation,int outputWidth,int outputHeight,boolean fill){
        if(width<=0||height<=0||outputWidth<=0||outputHeight<=0)return new float[]{1,1};
        float aspect=rotation%180==0?width/(float)height:height/(float)width;
        float target=outputWidth/(float)outputHeight;
        if(fill)return aspect>target?new float[]{aspect/target,1}:new float[]{1,target/aspect};
        return aspect>target?new float[]{1,target/aspect}:new float[]{aspect/target,1};
    }
}
