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
import java.util.function.ToDoubleFunction;

public class SharpeUtility {

    public SharpeUtility() {
        throw new UnsupportedOperationException("utility class");
    }

    public static <T extends Temporal> NavigableMap<T, Double>
    getReturnSeries(NavigableMap<T, SimpleBar> in, T startPoint) {

        NavigableMap<T, SimpleBar> inSub = in.tailMap(startPoint, true);
        NavigableMap<T, Double> res = new TreeMap<>();

        if (in.size() > 0) {
            T firstKey = inSub.keySet().stream().findFirst().get();
            inSub.keySet().forEach(t -> {
                if (t.equals(firstKey)) {
                    res.put(t, inSub.get(t).getBarReturn());
                } else {
                    res.put(t, inSub.get(t).getClose() / inSub.lowerEntry(t).getValue().getClose() - 1);
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

    public static double getSharpe(NavigableMap<? extends Temporal, Double> mp, double annuFactor) {
        double mean = getMean(mp);
        double sd = getSD(mp);
        return mp.size() == 0 ? 0 : mean / sd * Math.sqrt(annuFactor);
    }

    public static int getPercentile(NavigableMap<? extends Temporal, SimpleBar> mp) {
        if (mp.size() > 0) {
            double last = mp.lastEntry().getValue().getClose();
            double max = mp.entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max().getAsDouble();
            double min = mp.entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min().getAsDouble();
            //System.out.println(" last max min " + last + " " + max + " " + min);
            return (int) Math.round(100d * (last - min) / (max - min));
        }
        return 0;
    }

    /**
     * This method takes in a map of arbitrage type and spits out a return map
     *
     * @param <T>
     * @param mp             the map to operate on ( could be bar or double)
     * @param getDiff        takes in two values and computeYtd the return
     * @param getClose       depending on data type, get the close, either identity or
     *                       SimpleBar::getClose
     * @param getFirstReturn function to get the first return
     * @return the return map Map<String, Double>
     */
    public static <T> NavigableMap<LocalTime, Double> genReturnMap(
            NavigableMap<LocalTime, T> mp, DoubleBinaryOperator getDiff, ToDoubleFunction<T> getClose,
            ToDoubleFunction<T> getFirstReturn, LocalTime cutoff) {

        NavigableMap<LocalTime, Double> retMap = new TreeMap<>();
        mp.navigableKeySet().forEach(k -> {
            if (k.isBefore(cutoff)) {
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
        //System.out.println(" computeYtd minute sharpe from mtm mp ");
        NavigableMap<LocalTime, Double> retMap = new TreeMap<>();
        retMap = genReturnMap(mtmDeltaMp, (u, v) -> u / v - 1, d -> d, d -> 0.0, LocalTime.of(15, 1));

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
        diffMap = genReturnMap(netPnlMp, (u, v) -> u - v, d -> d, d -> 0.0, LocalTime.of(15, 1));
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
            retMap = genReturnMap(mp, (u, v) -> u / v - 1, SimpleBar::getClose, SimpleBar::getBarReturn,
                    LocalTime.of(16, 1));
            double minuteMean = Utility.computeMean(retMap);
            double minuteSD = Utility.computeSD(retMap);
            if (minuteSD != 0.0) {
                //System.out.println(" mean is " + (minuteMean * 240) + " minute sd " + (minuteSD * Math.sqrt(240)));
                return (minuteMean * 240) / (minuteSD * Math.sqrt(240));
            }
        }
        return 0.0;
    }

    public static double computeMinuteSharpeHK(NavigableMap<LocalTime, SimpleBar> mp, String name) {
        NavigableMap<LocalTime, Double> retMap = new TreeMap<>();
        if (mp.size() > 0) {
            retMap = genReturnMap(mp, (u, v) -> u / v - 1, SimpleBar::getClose, SimpleBar::getBarReturn,
                    LocalTime.of(16, 1));
            double minuteMean = Utility.computeMean(retMap);
            double minuteSD = Utility.computeSD(retMap);
            if (minuteSD != 0.0) {
//                if(name.equals("700")) {
//                    System.out.println(" name length retmap mean sd "+ name + " "
//                            + mp.size() + " " + minuteMean + " " + minuteSD );
//                    System.out.println(" retmap first 5" + retMap.headMap(LocalTime.of(9,35)));
//                    System.out.println(" retmap last 5" + retMap.tailMap(LocalTime.of(15,55)));
//                }
                //System.out.println(" mean is " + (minuteMean * 240) + " minute sd " + (minuteSD * Math.sqrt(240)));
                return (minuteMean * 240) / (minuteSD * Math.sqrt(240));
            }
        }
        return 0.0;
    }
}
