/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package utility;

import auxiliary.SimpleBar;

import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public class SharpeUtility {

    public SharpeUtility() {
        throw new UnsupportedOperationException("utility class");
    }

    public static <T extends Temporal> NavigableMap<T, Double>
    getReturnSeries(NavigableMap<T, SimpleBar> in, Predicate<T> fil) {

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

    public static double getMean(NavigableMap<? extends Temporal, Double> mp) {

        return mp.entrySet().stream().mapToDouble(Map.Entry::getValue).average().orElse(0.0);
    }

    public static double getSD(NavigableMap<? extends Temporal, Double> mp) {
        double mean = getMean(mp);
        int count = mp.size();

        return count == 0 ? 0 : Math.sqrt(mp.entrySet().stream().mapToDouble(Map.Entry::getValue).map(v -> Math.pow(v - mean, 2)).sum() / (count - 1));
    }

    public static double getSharpe(NavigableMap<? extends Temporal, Double> mp) {
        double mean = getMean(mp);
        double sd = getSD(mp);
        return mp.size() == 0 ? 0 : mean / sd * Math.sqrt(252);
    }

    public static int getPercentile(NavigableMap<? extends Temporal, SimpleBar> mp) {
        if (mp.size() > 0) {
            double last = mp.lastEntry().getValue().getClose();
            double max = mp.entrySet().stream().map(Map.Entry::getValue).mapToDouble(SimpleBar::getHigh).max().getAsDouble();
            double min = mp.entrySet().stream().map(Map.Entry::getValue).mapToDouble(SimpleBar::getLow).min().getAsDouble();
            return (int) Math.round(100d * (last - min) / (max - min));
        }
        return 0;
    }

    /**
     * This method takes in a map of arbitrage type and spits out a return map
     *
     * @param <T>
     * @param mp the map to operate on ( could be bar or double)
     * @param getDiff takes in two values and compute the return
     * @param getClose depending on data type, get the close, either identity or
     * SimpleBar::getClose
     * @param getFirstReturn function to get the first return
     * @return the return map Map<String, Double>
     */
    public static <T> NavigableMap<LocalTime, Double> genReturnMap(
            NavigableMap<LocalTime, T> mp, DoubleBinaryOperator getDiff, ToDoubleFunction<T> getClose,
            ToDoubleFunction<T> getFirstReturn) {

        NavigableMap<LocalTime, Double> retMap = new TreeMap<>();
        mp.navigableKeySet().forEach(k -> {
            if (k.isBefore(LocalTime.of(15, 1))) {
                if (!k.equals(mp.firstKey())) {
                    double prevClose = getClose.applyAsDouble(mp.lowerEntry(k).getValue());
                    retMap.put(k, getDiff.applyAsDouble(getClose.applyAsDouble(mp.get(k)), prevClose));
                } else {
                    retMap.put(k, getFirstReturn.applyAsDouble(mp.get(k)));
                }
            }
        });
        return retMap;
    }

    public static double computeMinuteSharpeFromMtmDeltaMp(NavigableMap<LocalTime, Double> mtmDeltaMp) {
        //System.out.println(" compute minute sharpe from mtm mp ");
        NavigableMap<LocalTime, Double> retMap = new TreeMap<>();
        retMap = genReturnMap(mtmDeltaMp, (u, v) -> u / v - 1, d -> d, d -> 0.0);

        double minuteMean = Utility.computeMean(retMap);
        double minuteSD = Utility.computeSD(retMap);
        if (minuteSD != 0.0) {
            //System.out.println(" mean is " + (minuteMean * 240) + " minute sd " + (minuteSD * Math.sqrt(240)));
            return (minuteMean * 240) / (minuteSD * Math.sqrt(240));
        }
        return 0.0;
    }

    public static double computeMinuteNetPnlSharpe(NavigableMap<LocalTime, Double> netPnlMp) {
        NavigableMap<LocalTime, Double> diffMap = new TreeMap<>();
        diffMap = genReturnMap(netPnlMp, (u, v) -> u - v, d -> d, d -> 0.0);
        double minuteMean = Utility.computeMean(diffMap);
        double minuteSD = Utility.computeSD(diffMap);
        if (minuteSD != 0.0) {
            //System.out.println(" mean is " + (minuteMean * 240) + " minute sd " + (minuteSD * Math.sqrt(240)));
            return (minuteMean * 240) / (minuteSD * Math.sqrt(240));
        }
        return 0.0;
    }

    public static double computeMinuteSharpe(NavigableMap<LocalTime, SimpleBar> mp) {
        NavigableMap<LocalTime, Double> retMap = new TreeMap<>();
        if (mp.size() > 0) {
            retMap = genReturnMap(mp, (u, v) -> u / v - 1, SimpleBar::getClose, SimpleBar::getBarReturn);
//            mp.navigableKeySet().forEach(k -> {
//                if (!k.equals(mp.firstKey())) {
//                    double prevClose = mp.lowerEntry(k).getValue().getClose();
//                    //double prevLow = mp.lowerEntry(k).getValue().getLow();
//                    retMap.put(k, mp.get(k).getClose() / prevClose - 1);
//                } else {
//                    retMap.put(k, mp.get(k).getBarReturn());
//                }
//            });
            double minuteMean = Utility.computeMean(retMap);
            double minuteSD = Utility.computeSD(retMap);
            if (minuteSD != 0.0) {
                //System.out.println(" mean is " + (minuteMean * 240) + " minute sd " + (minuteSD * Math.sqrt(240)));
                return (minuteMean * 240) / (minuteSD * Math.sqrt(240));
            }
        }
        return 0.0;
    }
}
