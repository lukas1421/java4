package apidemo;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("SpellCheckingInspection")
public enum FutType {
    PreviousFut("SGXA50PR"), FrontFut("SGXA50"), BackFut("SGXA50BM");
    private String tickerName;
    FutType(String t) {
        tickerName = t;
    }

    private static final Map<String, FutType> lookup = new HashMap<>();
    static {
        for(FutType t: FutType.values()) {
            lookup.put(t.getTicker(),t);
        }
    }

    public static FutType get(String ticker) {
        if(lookup.containsKey(ticker)) {
            return lookup.get(ticker);
        }
        throw new IllegalArgumentException(" cannot find ticker ");
    }

    /**
     * @return tickername
     */
    public String getTicker() {
        return tickerName;
    }

    @Override
    public String toString() {
        return " fut type is " + getTicker();
    }
}
