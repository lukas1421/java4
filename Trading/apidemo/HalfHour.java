package apidemo;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static apidemo.AutoTraderXU.ltof;

public enum HalfHour {

    H0(ltof(9, 0)), H1(ltof(9, 30)), H2(ltof(10, 0)), H3(ltof(10, 30))
    , H4(ltof(11, 0)), H5(ltof(13, 0)), H6(ltof(13, 30)), H7(ltof(14, 0)), H8(ltof(14, 30));

    private LocalTime startTime;

    private static final Map<LocalTime, HalfHour> lookup = new HashMap<>();

    static {
        for (HalfHour h : HalfHour.values()) {
            lookup.put(h.getStartTime(), h);
        }
    }


    public static HalfHour get(LocalTime t) {
        if (lookup.containsKey(t)) {
            return lookup.get(t);
        }
        throw new IllegalArgumentException(" cannot find time");
    }


    HalfHour(LocalTime t) {
        startTime = t;
    }

    public LocalTime getStartTime() {
        return startTime;
    }
}
