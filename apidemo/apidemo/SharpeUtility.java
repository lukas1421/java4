/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package apidemo;

import java.time.temporal.Temporal;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 *
 * @author Luke Shi
 */
final class SharpeUtility {

    public SharpeUtility() {
        throw new UnsupportedOperationException("utility class");
    }

    static <T extends Temporal> NavigableMap<T, Double> getReturnSeries(NavigableMap<T, SimpleBar> in, Predicate<T> fil) {
        NavigableMap<T, Double> res = new TreeMap<>();
        if (in.size() > 0) {
            T firstKey = in.keySet().stream().filter(fil).findFirst().get();
            in.keySet().stream().filter(fil).forEach(t -> {
                if (t.equals(firstKey)) {
                    res.put(t, in.get(t).getBarReturn());
                } else {
                    res.put(t, in.get(t).getClose() / in.lowerEntry(t).getValue().getClose() - 1);
                }
            });
            //res.entrySet().forEach(System.out::println);
            return res;
        }
        return new TreeMap<>();
    }

    static double getMean(NavigableMap<? extends Temporal, Double> mp) {

        return mp.entrySet().stream().mapToDouble(Map.Entry::getValue).average().orElse(0.0);
    }

    static double getSD(NavigableMap<? extends Temporal, Double> mp) {
        double mean = getMean(mp);
        int count = mp.size();

        return count == 0 ? 0 : Math.sqrt(mp.entrySet().stream().mapToDouble(Map.Entry::getValue).map(v -> Math.pow(v - mean, 2)).sum() / (count - 1));
    }

    static double getSharpe(NavigableMap<? extends Temporal, Double> mp) {
        double mean = getMean(mp);
        double sd = getSD(mp);
        return mp.size() == 0 ? 0 : mean / sd * Math.sqrt(252);
    }

    static int getPercentile(NavigableMap<? extends Temporal, SimpleBar> mp) {
        if (mp.size() > 0) {
            double last = mp.lastEntry().getValue().getClose();
            double max = mp.entrySet().stream().map(Map.Entry::getValue).mapToDouble(SimpleBar::getHigh).max().getAsDouble();
            double min = mp.entrySet().stream().map(Map.Entry::getValue).mapToDouble(SimpleBar::getLow).min().getAsDouble();
            return (int) Math.round(100d * (last - min) / (max - min));
        }
        return 0;
    }

}
