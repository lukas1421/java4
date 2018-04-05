package apidemo;

import auxiliary.SimpleBar;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class XuTraderHelper {

    public static NavigableMap<LocalDateTime, Double> getMAGen(NavigableMap<LocalDateTime, SimpleBar> mp, int period) {
        NavigableMap<LocalDateTime, Double> sma = new ConcurrentSkipListMap<>();
        for (Map.Entry<LocalDateTime, SimpleBar> e : mp.entrySet()) {
            long n = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).count();
            if (n > period) {
                long size = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).skip(n - period)
                        .count();
                double val = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey()))
                        .skip(n - period).mapToDouble(e2 -> e2.getValue().getAverage()).sum() / size;
                sma.put(e.getKey(), val);
            }
        }
        return sma;
    }
}
