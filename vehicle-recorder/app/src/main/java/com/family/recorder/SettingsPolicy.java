package com.family.recorder;
import java.util.Map;
final class SettingsPolicy {
    private static final String[] RESTART={"path","quota","seconds","hd","fps","rotation","audio","stabilize","torch"};
    static boolean requiresRestart(Map<String,?> current,Map<String,?> next){for(String key:RESTART)if(next.containsKey(key)&&!next.get(key).equals(current.get(key)))return true;return false;}
}
