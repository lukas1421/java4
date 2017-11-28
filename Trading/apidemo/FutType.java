package apidemo;

import java.util.HashMap;
import java.util.Map;

public enum FutType {
    FrontFut("SGXA50"), BackFut("SGXA50BM");

    String tickerName;

    FutType(String t) {
        tickerName = t;
    }

//    FutType(Contract c) {
//        tickerName = ibContractToSymbol(c);
//    }

    private static final Map<String, FutType> lookup = new HashMap<String, FutType>();

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
    String getTicker() {
        return tickerName;
    }
}
