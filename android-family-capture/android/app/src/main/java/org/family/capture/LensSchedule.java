package org.family.capture;
/** Monotonic dwell scheduling; switching is only evaluated between complete media operations. */
final class LensSchedule {
    final boolean alternate;final String initial;final long dwell;
    String current;long since;
    LensSchedule(String initial,boolean alternate,int seconds,long now){this.initial=initial;current=initial;this.alternate=alternate;dwell=seconds*1000L;since=now;}
    String next(long now){if(alternate&&now-since>=dwell){current="front".equals(current)?"back":"front";since=now;}return current;}
}
