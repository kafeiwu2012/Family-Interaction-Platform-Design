package com.family.recorder;
public final class OrientationMathTest {
    private static void equal(int a,int b,String why){if(a!=b)throw new AssertionError(why+": "+a+" != "+b);}
    private static void near(float a,float b,String why){if(Math.abs(a-b)>.001f)throw new AssertionError(why+": "+a+" != "+b);}
    public static void main(String[] args){
        equal(OrientationMath.rotation(90,false,90,0),0,"rear landscape upright");
        equal(OrientationMath.rotation(90,false,0,0),90,"rear portrait upright");
        equal(OrientationMath.rotation(90,false,270,0),180,"rear reverse landscape");
        equal(OrientationMath.rotation(270,true,90,0),0,"front landscape upright");
        equal(OrientationMath.rotation(270,true,0,0),270,"front portrait upright");
        equal(OrientationMath.rotation(90,true,90,0),180,"front sensor mounting variant");
        equal(OrientationMath.rotation(90,false,90,180),180,"manual mounting correction");
        // Regression: R7sm front portrait preview was upside down when it reused MP4 rotation.
        equal(OrientationMath.previewRotation(270,true,0,0),90,"front portrait preview mirror compensation");
        equal(OrientationMath.rotation(270,true,0,0),270,"MP4 portrait metadata must remain separate");
        equal(OrientationMath.previewRotation(270,true,90,0),0,"front landscape preview unchanged");
        equal(OrientationMath.previewRotation(270,true,180,0),270,"front reverse portrait preview");
        equal(OrientationMath.previewRotation(270,true,270,0),180,"front reverse landscape preview unchanged");
        equal(OrientationMath.previewRotation(90,true,0,0),270,"alternate front sensor portrait compensation");
        equal(OrientationMath.previewRotation(90,true,90,0),180,"alternate front sensor landscape preserved");
        equal(OrientationMath.previewRotation(90,false,0,0),90,"rear portrait preview preserved");
        equal(OrientationMath.previewRotation(90,false,90,0),0,"rear landscape preview preserved");
        equal(OrientationMath.previewRotation(270,true,0,180),270,"front correction applies after preview compensation");
        equal(OrientationMath.normalize(-90),270,"normalization");
        for(int rotation=0;rotation<4;rotation++)equal(OrientationMath.displayDegrees(rotation),rotation*90,"Surface rotation conversion");
        float[] crop=OrientationMath.scale(1280,720,0,1080,1920,true);
        near(crop[0],(1280f/720)/(1080f/1920),"fill must crop sides without stretching");near(crop[1],1,"fill height");
        float[] fit=OrientationMath.scale(1280,720,0,1080,1920,false);near(fit[0],1,"fit width");near(fit[1],(1080f/1920)/(1280f/720),"fit must letterbox");
        float[] portrait=OrientationMath.scale(1280,720,90,720,1280,true);near(portrait[0],1,"rotated portrait width");near(portrait[1],1,"rotated portrait height");
        float[] wide=OrientationMath.scale(1280,720,0,2000,720,true);near(wide[0],1,"wide screen crop width");near(wide[1],2000f/1280,"wide screen crop height");
        System.out.println("PASS: front/rear orientation, manual correction, portrait and landscape fill/fit geometry");
    }
}
