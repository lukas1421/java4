package apidemo;

import auxiliary.SimpleBar;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static utility.Utility.getStr;

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

    private static boolean priceMAUntouched(NavigableMap<LocalDateTime, SimpleBar> mp, int period, LocalDateTime lastTradeTime) {
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

    static void outputToAutoLog(String s) {
        File output = new File(TradingConstants.GLOBALPATH + "autoLog.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static <T extends Temporal> int getPercentileForLast(NavigableMap<T, SimpleBar> map) {
        if (map.size() > 0) {
            double max = map.entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max().orElse(0.0);
            double min = map.entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min().orElse(0.0);
            double last = map.lastEntry().getValue().getClose();
            System.out.println(getStr(" getPercentileForLast max min last ", max, min, last));
            return (int) Math.round(100d * ((last - min) / (max - min)));
        }
        return 50;
    }
}
