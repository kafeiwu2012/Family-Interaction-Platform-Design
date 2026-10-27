package com.family.recorder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/** Only this app's dedicated segment names are ever eligible for removal. */
final class LoopStore {
    static boolean owned(File f) { return f.isFile() && ClipNames.owned(f.getName()); }
    static boolean locked(File f) { return new File(f.getPath() + ".lock").exists(); }
    static File[] files(File dir) {
        File[] fs = dir.listFiles(LoopStore::owned);
        if (fs == null) return new File[0];
        Arrays.sort(fs, (a,b) -> { int time=Long.compare(a.lastModified(),b.lastModified()); return time==0?a.getName().compareTo(b.getName()):time; });
        return fs;
    }
    static long used(File dir) { long n = 0; for (File f : files(dir)) n += f.length(); return n; }
    static synchronized void prepare(File dir, long quota, long reserve, long freeFloor) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法创建目录");
        if (reserve > quota) throw new IOException("容量不足以容纳一个片段，请增加容量或缩短分段");
        for (File f : files(dir)) {
            if (used(dir) + reserve <= quota && dir.getUsableSpace() >= reserve + freeFloor) return;
            if (locked(f)) continue;
            if (!f.delete()) throw new IOException("无法清理旧录像：" + f.getName());
        }
        if (used(dir) + reserve > quota || dir.getUsableSpace() < reserve + freeFloor)
            throw new IOException("空间不足：请导出或解锁录像，或增加容量");
    }
}
