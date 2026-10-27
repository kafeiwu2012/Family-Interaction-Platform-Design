package com.family.recorder;
import java.util.*;
public final class SettingsPolicyTest {
    public static void main(String[] args){
        Map<String,Object> before=new HashMap<>();before.put("path","/Movies/VehicleRecorder");before.put("hd",false);before.put("fps",30);before.put("quota",2048);
        Map<String,Object> edit=new HashMap<>(before);if(SettingsPolicy.requiresRestart(before,edit))throw new AssertionError("unchanged settings must not interrupt video");
        edit.put("auto_start",false);edit.put("preview_fps",15);if(SettingsPolicy.requiresRestart(before,edit))throw new AssertionError("startup preference and preview FPS must apply without splitting video");
        for(String key:new String[]{"path","hd","fps","quota"}){edit=new HashMap<>(before);edit.put(key,key.equals("path")?"/NewTarget":key.equals("hd")?true:24);if(!SettingsPolicy.requiresRestart(before,edit))throw new AssertionError("recording setting must create a safe boundary: "+key);}
        System.out.println("PASS: live preferences preserve recording; encoding and storage changes require a safe segment boundary");
    }
}
