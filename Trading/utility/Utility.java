package utility;

import apidemo.ChinaStock;
import graph.GraphIndustry;
import auxiliary.SimpleBar;
import org.hibernate.Hibernate;
import org.hibernate.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.sql.Blob;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiPredicate;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static java.lang.Math.log;
import static java.lang.Math.round;
import static java.util.stream.Collectors.*;

public class Utility {

    public static final BiPredicate<? super Map<String, ? extends Number>, String> NO_ZERO = (mp, name) -> mp.containsKey(name) && mp.get(name).doubleValue() != 0.0;
    public static final Predicate<? super Map.Entry<LocalTime, SimpleBar>> CONTAINS_NO_ZERO = e -> !e.getValue().containsZero();
    //static final Entry<LocalTime, SimpleBar> dummyBar =  new AbstractMap.SimpleEntry<>(LocalTime.of(23,59), SimpleBar.getInstance());
    //static final Entry<LocalTime, Double> dummyMap =  new AbstractMap.SimpleEntry<>(LocalTime.of(23,59), 0.0);
    public static final Predicate<? super Map.Entry<LocalTime, ?>> AM_PRED = e ->
            e.getKey().isAfter(LocalTime.of(9, 29, 59)) && e.getKey().isBefore(LocalTime.of(11, 30, 01));
    public static final Predicate<? super Map.Entry<LocalTime, ?>> PM_PRED = e ->
            e.getKey().isAfter(LocalTime.of(12, 59, 59)) && e.getKey().isBefore(LocalTime.of(15, 0, 1));
    //static final Comparator<? super Entry<LocalTime,SimpleBar>> BAR_HIGH = (e1,e2)->e1.getValue().getHigh()>=e2.getValue().getHigh()?1:-1;
    //static final Comparator<? super Entry<LocalTime,SimpleBar>> BAR_HIGH = Comparator.comparingDouble(e->e.getValue().getHigh());
    public static final Comparator<? super Map.Entry<LocalTime, SimpleBar>> BAR_HIGH = Map.Entry.comparingByValue(Comparator.comparingDouble(SimpleBar::getHigh));
    public static final Comparator<? super Map.Entry<LocalTime, SimpleBar>> BAR_LOW = (e1, e2) -> e1.getValue().getLow() >= e2.getValue().getLow() ? 1 : -1;
    public static final Predicate<? super Map.Entry<LocalTime, ?>> IS_OPEN_PRED = e -> e.getKey().isAfter(LocalTime.of(9, 29, 59));
    public static final LocalTime AM914T = LocalTime.of(9, 14, 0);
    public static final LocalTime AM941T = LocalTime.of(9, 41, 0);
    public static final LocalTime AM924T = LocalTime.of(9, 24, 0);
    public static final LocalTime AM925T = LocalTime.of(9, 25, 0);
    public static final LocalTime AM929T = LocalTime.of(9, 29, 0);
    public static final LocalTime AMOPENT = LocalTime.of(9, 30, 0);
    public static final LocalTime AM935T = LocalTime.of(9, 35, 0);
    public static final LocalTime AM940T = LocalTime.of(9, 40, 0);
    public static final LocalTime AM950T = LocalTime.of(9, 50, 0);
    public static final LocalTime AM1000T = LocalTime.of(10, 0);
    public static final LocalTime AMCLOSET = LocalTime.of(11, 30, 0);
    public static final LocalTime AM1131T = LocalTime.of(11, 31, 0);
    public static final LocalTime PMOPENT = LocalTime.of(13, 0, 0);
    public static final LocalTime PM1309T = LocalTime.of(13, 9, 0);
    public static final LocalTime PM1310T = LocalTime.of(13, 10, 0);
    public static final LocalTime PMCLOSET = LocalTime.of(15, 0, 0);
    public static final LocalTime TIMEMAX = LocalTime.MAX.truncatedTo(ChronoUnit.MINUTES);
    public static final BetweenTime<LocalTime, Boolean> TIME_BETWEEN = (t1, b1, t2, b2) -> (t -> t.isAfter(b1 ? t1.minusMinutes(1) : t1) && t.isBefore(b2 ? t2.plusMinutes(1) : t2));
    public static final GenTimePred<LocalTime, Boolean> ENTRY_BTWN_GEN = (t1, b1, t2, b2) -> (e -> e.getKey().isAfter(b1 ? t1.minusMinutes(1) : t1) && e.getKey().isBefore(b2 ? t2.plusMinutes(1) : t2));
    public static BiPredicate<? super Map<String, ? extends Map<LocalTime, ?>>, String> NORMAL_MAP = (mp, name) -> mp.containsKey(name) && !mp.get(name).isEmpty() && mp.get(name).size() > 0;

    private Utility() {
        throw new UnsupportedOperationException(" cannot instantiate utility class ");
    }

    public static String timeNowToString() {
        LocalTime now = LocalTime.now();
        return now.truncatedTo(ChronoUnit.SECONDS).toString() + (now.getSecond() == 0 ? ":00" : "");
    }

    public static String timeToString(LocalTime t) {
        return t.truncatedTo(ChronoUnit.SECONDS).toString() + (t.getSecond() == 0 ? ":00" : "");
    }

    public static double computeMean(NavigableMap<LocalTime, Double> retMap) {
        if (retMap.size() > 1) {
            double sum = retMap.entrySet().stream().mapToDouble(e -> e.getValue()).sum();
            return sum / retMap.size();
        }
        return 0;
    }

    public static double computeSD(NavigableMap<LocalTime, Double> retMap) {
        if (retMap.size() > 1) {
            double mean = computeMean(retMap);
            return Math.sqrt((retMap.entrySet().stream().mapToDouble(e -> e.getValue()).map(v -> Math.pow(v - mean, 2)).sum()) / (retMap.size() - 1));
        }
        return 0.0;

    }

    public static Blob blobify(NavigableMap<LocalTime, ?> mp, Session s) {
        ByteArrayOutputStream bos;
        try (ObjectOutputStream out = new ObjectOutputStream(bos = new ByteArrayOutputStream())) {
            out.writeObject(mp);
            byte[] buf = bos.toByteArray();
            Blob b = Hibernate.getLobCreator(s).createBlob(buf);
            return b;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static NavigableMap<LocalTime, Double> mapSynthesizer(NavigableMap<LocalTime, Double>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream())
                .collect(Collectors.groupingBy(e -> e.getKey(), ConcurrentSkipListMap::new, Collectors.summingDouble(e -> e.getValue())));
    }

    public static String getStrTabbed(Object... cs) {
        StringBuilder b = new StringBuilder();
        for (Object ss : cs) {
            b.append(ss.toString()).append("\t");
        }
        return b.toString().trim();
    }

    public static String getStrComma(Object... cs) {
        StringBuilder b = new StringBuilder();
        for (Object ss : cs) {
            b.append(ss.toString()).append(",");
        }
        return b.toString().trim();
    }

    public static String getStr(Object... cs) {
        StringBuilder b = new StringBuilder();
        for (Object ss : cs) {
            b.append(ss.toString()).append(" ");
        }
        return b.toString();
    }

    public static String getStrCheckNull(Object... cs) {
        StringBuilder b = new StringBuilder();
        for (Object ss : cs) {
            if (ss != null) {
                b.append(ss.toString()).append(" ");
            } else {
                b.append(" NULL ");
            }
        }
        return b.toString();
    }

    public static double getMin(NavigableMap<LocalTime, Double> tm) {
        return (tm != null && tm.size() > 2) ? tm.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0) : 0.0;
    }

    public static double getMax(NavigableMap<LocalTime, Double> tm) {
        return (tm != null && tm.size() > 2) ? tm.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0) : 0.0;
    }

    public static double getMaxRtn(NavigableMap<LocalTime, Double> tm) {
        return (tm.size() > 0) ? (double) Math.round((getMax(tm)
                / tm.entrySet().stream().findFirst().map(Map.Entry::getValue).orElse(0.0) - 1) * 1000d) / 10d : 0.0;
    }

    public static double getLast(NavigableMap<LocalTime, Double> tm) {
        return tm.size() > 0 ? Math.round(100d * tm.lastEntry().getValue()) / 100d : 0.0;
    }

    /**
     * test if one-level maps contains value for a given stock
     *
     * @param name name of the stock
     * @param mp hashmap of stock and value
     * @return all of these maps contains info about this stock
     */
    public static boolean noZeroArrayGen(String name, Map<String, ? extends Number>... mp) {
        boolean res = true;
        for (Map<String, ? extends Number> m : mp) {
            res = res && NO_ZERO.test(m, name);
        }
        return res;
    }

    public static boolean normalMapGen(String name, Map<String, ? extends Map<LocalTime, ?>>... mp) {
        boolean res = true;
        for (Map<String, ? extends Map<LocalTime, ?>> m : mp) {
            res = res && NORMAL_MAP.test(m, name);
        }
        return res;
    }

    public static LocalDate getPreviousWorkday(LocalDate ld) {
        if (ld.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
            return ld.minusDays(3L);
        } else {
            return ld.minusDays(1L);
        }
    }

    public static double pd(List<String> l, int index) {
        return (l.size() > index) ? Double.parseDouble(l.get(index)) : 0.0;
    }

    public static <T> Map<String, ? extends NavigableMap<LocalTime, T>> trimMap(Map<String, ? extends NavigableMap<LocalTime, T>> mp) {
        Map<String, NavigableMap<LocalTime, T>> res = new ConcurrentHashMap<>();
        mp.keySet().forEach((String key) -> {
            res.put(key, new ConcurrentSkipListMap<>());
            mp.get(key).keySet().forEach(t -> {
                if ((t.isAfter(LocalTime.of(9, 14)) && t.isBefore(LocalTime.of(11, 35))) || (t.isAfter(LocalTime.of(12, 59)) && t.isBefore(LocalTime.of(15, 1)))) {
                    if (t.isBefore(LocalTime.now().plusMinutes(5))) {
                        res.get(key).put(t, mp.get(key).get(t));
                    }
                }
            });
        });
        return res;
    }

    public static <T> NavigableMap<LocalTime, T> trimSkipMap(NavigableMap<LocalTime, T> mp, LocalTime startTime) {
        NavigableMap<LocalTime, T> res = new ConcurrentSkipListMap<>();
        mp.keySet().forEach(t -> {
            if ((t.isAfter(startTime) && t.isBefore(LocalTime.of(11, 31))) || (t.isAfter(LocalTime.of(12, 59)) && t.isBefore(LocalTime.of(15, 1)))) {
                res.put(t, mp.get(t));
            }
        });
        return res;
    }

    public static double getRtn(NavigableMap<LocalTime, Double> tm1) {
        return tm1.size() > 0 ? round(log(tm1.lastEntry().getValue() / tm1.firstEntry().getValue()) * 1000d) / 10d : 0.0;
    }

    public static void fixNavigableMap(String name, NavigableMap<LocalTime, SimpleBar> nm) {
        nm.entrySet().forEach(e -> {
            if (e.getValue().getHLRange() > 0.03) {
                System.out.println(" name wrong is " + name);
                double close = nm.lowerEntry(e.getKey()).getValue().getClose();
                nm.get(e.getKey()).updateClose(close);
                nm.get(e.getKey()).updateHigh(close);
                nm.get(e.getKey()).updateLow(close);
                nm.get(e.getKey()).updateOpen(close);

            }
        });

    }

    public static void fixVolNavigableMap(String s, NavigableMap<LocalTime, Double> nm) {
        nm.descendingKeySet().stream().forEachOrdered(k -> {
            double thisValue = nm.get(k);

            if (s.equals("sz300315")) {
                System.out.println(" sz300315 ");
                System.out.println(" size size " + nm.size());
                System.out.println(" last entry " + nm.lastEntry());
                nm.replaceAll((k1, v1) -> 0.0);
            }

            if (Optional.ofNullable(nm.lowerEntry(k)).map(Map.Entry::getValue).orElse(thisValue) > thisValue) {
                System.out.println(" fixing vol for " + s + " time " + k + " this value " + thisValue);
                nm.put(nm.lowerKey(k), thisValue);
            }
        });
    }

    public static void fixPriceMap(Map<String, ? extends NavigableMap<LocalTime, SimpleBar>> mp) {
        mp.entrySet().forEach((e) -> {
            fixNavigableMap(e.getKey(), e.getValue());
        });
    }

    public static <T extends NavigableMap<LocalTime, Double>> void getIndustryVolYtd(Map<String, T> mp) {
        CompletableFuture.supplyAsync(()
                -> mp.entrySet().stream().filter(GraphIndustry.NO_GC)
                        .collect(groupingBy(e -> ChinaStock.industryNameMap.get(e.getKey()),
                                mapping(e -> e.getValue(), Collectors.collectingAndThen(toList(), e -> e.stream().flatMap(e1 -> e1.entrySet().stream().filter(GraphIndustry.TRADING_HOURS))
                                .collect(groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new, summingDouble(e1 -> e1.getValue()))))))))
                .thenAccept(m -> m.keySet().forEach(s -> {
            mp.put(s, (T) m.get(s));
        }));
    }

    public static double minGen(double... l) {
        double res = Double.MAX_VALUE;
        for (double d : l) {
            res = Math.min(res, d);
        }
        return res;
    }

    public static double maxGen(double... l) {
        double res = Double.MIN_VALUE;
        for (double d : l) {
            res = Math.max(res, d);
        }
        return res;
    }

    public static double applyAllDouble(DoubleBinaryOperator op, double... num) {
        List<Double> s = DoubleStream.of(num).mapToObj(Double::valueOf).collect(toList());
        if (num.length > 0) {
            double res = s.get(0);
            for (double d : s) {
                res = op.applyAsDouble(res, d);
            }
            return res;
        }
        return 0.0;
    }

    public static Map<String, ConcurrentSkipListMap<LocalTime, Double>> mapConverter(Map<String, ? extends NavigableMap<LocalTime, Double>> mp) {
        ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> res = new ConcurrentHashMap<>();

        mp.keySet().forEach((String name) -> {
            res.put(name, new ConcurrentSkipListMap<>());
            mp.get(name).keySet().forEach((LocalTime t) -> {
                res.get(name).put(t, mp.get(name).get(t));
            });
            System.out.println(" for key " + name + " result " + res.get(name));
        });
        System.out.println(" converting done ");
        return res;
    }
}
