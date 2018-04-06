package apidemo;

import auxiliary.SimpleBar;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

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

    public static boolean priceMAUntouched(NavigableMap<LocalDateTime, SimpleBar> mp, int period, LocalDateTime lastTradeTime) {
        NavigableMap<LocalDateTime, Double> sma = getMAGen(mp, period);
        NavigableMap<LocalDateTime, Double> smaFiltered = sma.entrySet().stream().filter(e -> e.getKey().isAfter(lastTradeTime))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));

        for (LocalDateTime t : smaFiltered.keySet()) {
            SimpleBar sb = mp.get(t);
            if (sb.includes(sma.get(t))) {
                return false;
            }
        }
        return true;
    }

    static int getUntouchedMAPeriod(NavigableMap<LocalDateTime, SimpleBar> mp, LocalDateTime lastTradeTime) {
        int defaultPeriod = 60;
        int increaseStep = 5;
        int maxPeriod = 150;
        int res = defaultPeriod;
        //NavigableMap<LocalDateTime, Double> smaSeed = XuTraderHelper.getMAGen(mp, defaultPeriod);

        while (!priceMAUntouched(mp, res, lastTradeTime) && res <= maxPeriod) {
            res += increaseStep;
            System.out.println(" res is " + res);
        }
        return res;
    }
}
