package org.family.capture;
public final class LensScheduleTest {
    static void equal(String value,String expected){if(!expected.equals(value))throw new AssertionError(value+" != "+expected);}
    public static void main(String[] args){
        LensSchedule fixed=new LensSchedule("front",false,60,0);equal(fixed.next(999999),"front");
        LensSchedule alternating=new LensSchedule("back",true,60,0);
        equal(alternating.next(0),"back");equal(alternating.next(59999),"back");equal(alternating.next(60000),"front");
        equal(alternating.next(60001),"front");equal(alternating.next(120000),"back");
        // A long-running recording defers evaluation. It must cause one switch, not rapid catch-up flips.
        equal(alternating.next(300000),"front");equal(alternating.next(300001),"front");equal(alternating.next(359999),"front");equal(alternating.next(360000),"back");
        System.out.println("PASS: fixed camera, dwell boundaries, deferred switch, no catch-up oscillation");
    }
}
