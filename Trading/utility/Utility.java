package utility;

import TradeType.Trade;
import apidemo.ChinaData;
import apidemo.ChinaStock;
import apidemo.MorningTask;
import auxiliary.SimpleBar;
import graph.GraphIndustry;
import historical.HistChinaStocks;
import org.hibernate.Hibernate;
import org.hibernate.Session;

import java.io.*;
import java.sql.Blob;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static apidemo.ChinaStock.priceMap;
import static apidemo.ChinaStock.symbolNames;
import static apidemo.TradingConstants.tdxPath;
import static java.lang.Math.log;
import static java.lang.Math.round;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

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

    public static final Comparator<? super Map.Entry<? extends Temporal, SimpleBar>> BAR_HIGH =
            (e1, e2) -> e1.getValue().getHigh() >= e2.getValue().getHigh() ? 1 : -1;
    //Map.Entry.comparingByValue(Comparator.comparingDouble(SimpleBar::getHigh));

    public static final Comparator<? super Map.Entry<? extends Temporal, SimpleBar>> BAR_LOW =
            (e1, e2) -> e1.getValue().getLow() >= e2.getValue().getLow() ? 1 : -1;

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

    static double computeMean(NavigableMap<LocalTime, Double> retMap) {
        if (retMap.size() > 1) {
            double sum = retMap.entrySet().stream().mapToDouble(Map.Entry::getValue).sum();
            return sum / retMap.size();
        }
        return 0;
    }

    static double computeSD(NavigableMap<LocalTime, Double> retMap) {
        if (retMap.size() > 1) {
            double mean = computeMean(retMap);
            return Math.sqrt((retMap.entrySet().stream().mapToDouble(Map.Entry::getValue).map(v -> Math.pow(v - mean, 2)).sum())
                    / (retMap.size() - 1));
        }
        return 0.0;

    }

    public static LocalDate getMondayOfWeek(LocalDateTime ld) {
        LocalDate res = ld.toLocalDate();
        while (!res.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
            res = res.minusDays(1);
        }
        return res;
    }

    public static Blob blobify(NavigableMap<LocalTime, ?> mp, Session s) {
        ByteArrayOutputStream bos;
        try (ObjectOutputStream out = new ObjectOutputStream(bos = new ByteArrayOutputStream())) {
            out.writeObject(mp);
            byte[] buf = bos.toByteArray();
            return Hibernate.getLobCreator(s).createBlob(buf);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @SafeVarargs
    public static NavigableMap<LocalTime, Double> mapSynthesizer(NavigableMap<LocalTime, Double>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new, Collectors.summingDouble(Map.Entry::getValue)));
    }

    public static <T> BinaryOperator<NavigableMap<T, Double>> mapBinOp(BinaryOperator<Double> o) {
        return (a, b) -> mapCominberGen(o, a, b);
    }

    public static <T> BinaryOperator<NavigableMap<T, Double>> mapBinOp() {
        return (a, b) -> mapCominberGen(Double::sum, a, b);
    }

    public static <T> BinaryOperator<NavigableMap<T, Double>> mapBinOp(Predicate<? super Map.Entry<T, ?>> p) {
        return (a, b) -> mapCominberGen(Double::sum, p, a, b);
    }

    @SafeVarargs
    public static <T> NavigableMap<T, Double> mapCominberGen(BinaryOperator<Double> o, NavigableMap<T, Double>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream()).collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new,
                Collectors.reducing(0.0, Map.Entry::getValue, o)));
    }

    @SafeVarargs
    public static <T> NavigableMap<T, Double> mapCominberGen(BinaryOperator<Double> o, Predicate<? super Map.Entry<T, ?>> p, NavigableMap<T, Double>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream().filter(p))
                .collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new,
                        Collectors.reducing(0.0, Map.Entry::getValue, o)));
    }

    @SafeVarargs
    public static <T> NavigableMap<T, Double> mapCominberGen(NavigableMap<T, Double>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream()).collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new,
                Collectors.reducing(0.0, Map.Entry::getValue, Double::sum)));
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


    public static <T> double getMin(NavigableMap<T, Double> tm) {
        return (tm != null && tm.size() > 2) ? tm.entrySet().stream().min(Map.Entry.comparingByValue()).
                map(Map.Entry::getValue).orElse(0.0) : 0.0;
    }

    public static <T, S> double getMinGen(NavigableMap<T, S> tm, ToDoubleFunction<S> f) {
        return (tm != null && tm.size() > 2) ? tm.values().stream().mapToDouble(f).reduce(Math::min).orElse(0.0) : 0.0;
    }

    public static <T, S> double getMaxGen(NavigableMap<T, S> tm, ToDoubleFunction<S> f) {
        return (tm != null && tm.size() > 2) ? tm.values().stream().mapToDouble(f).reduce(Math::max).orElse(0.0) : 0.0;
    }

    public static <T, S> double reduceMapToDouble(NavigableMap<T, S> tm, ToDoubleFunction<S> f, DoubleBinaryOperator o) {
        return (tm != null && tm.size() > 0) ? tm.values().stream().mapToDouble(f).reduce(o).orElse(0.0) : 0.0;
    }

    public static <T> double getMax(NavigableMap<T, Double> tm) {
        return (tm != null && tm.size() > 2) ? tm.values().stream().reduce(Math::max).orElse(0.0) : 0.0;
    }

    public static double getMaxRtn(NavigableMap<LocalTime, Double> tm) {
        return (tm.size() > 0) ? (double) Math.round((getMax(tm) / tm.firstEntry().getValue() - 1) * 1000d) / 10d : 0.0;
    }

    public static <T> double getLast(NavigableMap<T, Double> tm) {
        return tm.size() > 0 ? Math.round(100d * tm.lastEntry().getValue()) / 100d : 0.0;
    }

    /**
     * test if one-level maps contains value for a given stock
     *
     * @param name name of the stock
     * @param mp   hashmap of stock and value
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

    public static <T> double getRtn(NavigableMap<T, Double> tm1) {
        return tm1.size() > 0 ? round(log(tm1.lastEntry().getValue() / tm1.firstEntry().getValue()) * 1000d) / 10d : 0.0;
    }

    public static <T, S> double getRtn(NavigableMap<T, S> tm1, ToDoubleFunction<S> func) {
        return tm1.size() > 0 ? round((func.applyAsDouble(tm1.lastEntry().getValue()) /
                func.applyAsDouble(tm1.firstEntry().getValue()) - 1) * 1000d) / 10d : 0.0;
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
        mp.forEach(Utility::fixNavigableMap);
    }

    public static <T extends NavigableMap<LocalTime, Double>> void getIndustryVolYtd(Map<String, T> mp) {
        CompletableFuture.supplyAsync(()
                        -> mp.entrySet().stream().filter(GraphIndustry.NO_GC)
                        .collect(groupingBy(e -> ChinaStock.industryNameMap.get(e.getKey()),
                                mapping(Map.Entry::getValue, Collectors.reducing(Utility.mapBinOp(GraphIndustry.TRADING_HOURS)))))
//                                , Collectors.collectingAndThen(toList(),
//                                e -> e.stream().flatMap(e1 -> e1.entrySet().stream().filter(GraphIndustry.TRADING_HOURS))
//                                        .collect(groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new, summingDouble(Map.Entry::getValue)))))))
        )
                .thenAccept(m -> m.keySet().forEach(s -> {
                    mp.put(s, (T) (m.get(s).orElse(new ConcurrentSkipListMap<>())));
                }));
    }

    public static double minGen(double... l) {
        return Arrays.stream(l).reduce(Math::min).orElse(0.0);
    }

    public static double maxGen(double... l) {
        return Arrays.stream(l).reduce(Math::max).orElse(0.0);
    }

    public static double reduceDouble(DoubleBinaryOperator op, double... num) {
        return Arrays.stream(num).reduce(op).orElse(0.0);
    }

    public static Map<String, ? extends NavigableMap<LocalTime, Double>> mapConverter(Map<String, ? extends NavigableMap<LocalTime, Double>> mp) {
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

    public static void roundMap(Map<LocalTime, SimpleBar> mp) {
        mp.forEach((k, v) -> v.round());
    }

    public static <T> void forwardFillHelper(NavigableMap<LocalTime, T> tm, Predicate<T> testZero, Supplier<T> s) {
        if (tm.size() > 1) {
            LocalTime t = LocalTime.of(9, 30);
            while (t.isBefore(LocalTime.of(15, 1))) {
                if (t.isAfter(LocalTime.of(11, 30)) && t.isBefore(LocalTime.of(13, 0))) {
                    if (tm.containsKey(t)) {
                        tm.remove(t);
                    }
                } else {
                    if (!tm.containsKey(t) || testZero.test(tm.get(t))) {
                        System.out.println(" for min " + t);
                        T sb = tm.getOrDefault(t.minusMinutes(1), s.get());
                        tm.put(t, sb);
                    }
                }
                t = t.plusMinutes(1L);
            }
        } else {
            System.out.println(" tm is empty ");
        }
    }

    public static void getFilesFromTDXGen(LocalDate ld, Map<String, ? extends NavigableMap<LocalTime, SimpleBar>> mp1
            , Map<String, ? extends NavigableMap<LocalTime, Double>> mp2) {

        System.out.println(" localdate is " + ld);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        final String dateString = ld.format(formatter);
        System.out.println(" date is " + dateString);

        symbolNames.forEach(e -> {
            //System.out.println(" date stock " + dateString + " " + e);
            boolean found = false;
            String name = (e.substring(0, 2).toUpperCase() + "#" + e.substring(2) + ".txt");
            String line;
            double totalSize = 0.0;

            if (!e.equals("sh204001") && (e.substring(0, 2).toUpperCase().equals("SH") || e.substring(0, 2).toUpperCase().equals("SZ"))) {
                try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxPath + name)))) {
                    while ((line = reader1.readLine()) != null) {
                        List<String> al1 = Arrays.asList(line.split("\t"));
                        if (al1.get(0).equals(dateString)) {
                            found = true;
                            String time = al1.get(1);
                            LocalTime lt = LocalTime.of(Integer.parseInt(time.substring(0, 2)), Integer.parseInt(time.substring(2)));
                            mp1.get(e).put(lt.minusMinutes(1L), new SimpleBar(Double.parseDouble(al1.get(2)), Double.parseDouble(al1.get(3)),
                                    Double.parseDouble(al1.get(4)), Double.parseDouble(al1.get(5))));
                            if (Double.parseDouble(al1.get(7)) == 0.0) {
                                totalSize += (Double.parseDouble(al1.get(6)) / 100);
                                mp2.get(e).put(lt.minusMinutes(1L), totalSize);
                            } else {
                                totalSize += (Double.parseDouble(al1.get(7)) / 1000000);
                                mp2.get(e).put(lt.minusMinutes(1L), totalSize);
                            }
                        }
                    }
                    if (found) {
                        mp1.get(e).put(LocalTime.of(11, 29), mp1.get(e).get(LocalTime.of(11, 28)));
                        mp1.get(e).put(LocalTime.of(11, 30), mp1.get(e).get(LocalTime.of(11, 28)));

                        if (mp1.get(e).containsKey(LocalTime.of(14, 59))) {
                            mp1.get(e).put(LocalTime.of(15, 0), mp1.get(e).get(LocalTime.of(14, 59)));
                        }

                        mp2.get(e).put(LocalTime.of(11, 29), mp2.get(e).get(LocalTime.of(11, 28)));
                        mp2.get(e).put(LocalTime.of(11, 30), mp2.get(e).get(LocalTime.of(11, 28)));

                        if (mp2.get(e).containsKey(LocalTime.of(14, 59))) {
                            mp2.get(e).put(LocalTime.of(15, 0), mp2.get(e).get(LocalTime.of(14, 59)));
                        }
                    } else {
                        System.out.println(" for " + e + " filling done");
                        SimpleBar sb = new SimpleBar(priceMap.getOrDefault(e, 0.0));
                        ChinaData.tradeTimePure.forEach(ti -> {
                            mp1.get(e).put(ti, sb);
                        });
                        //System.out.println( "last key "+e+ " "+ mp1.get(e).lastEntry());
                        //System.out.println( "noon last key "+e+ " " + mp1.get(e).ceilingEntry(LocalTime.of(11,30)).toString());
                    }

                } catch (IOException | NumberFormatException ex) {
                    System.out.println(" does not contain" + e);
                    ex.printStackTrace();
                }
            }
        });
    }

    public static String addSHSZ(String s) {
        if (s.equals("204001") || s.equals("000905") || s.equals("510050")) {
            return "sh" + s;
        }
        return ((s.startsWith("6")) ? "sh" : "sz") + s;
    }

    @SafeVarargs
    public static <T> double reduceMap(DoubleBinaryOperator o, NavigableMap<T, Double>... mps) {
        return Arrays.stream(mps).flatMap(e -> e.entrySet().stream()).mapToDouble(Map.Entry::getValue).reduce(o).orElse(0.0);
    }

    @SafeVarargs
    public static <T, S> double reduceMap(DoubleBinaryOperator o, ToDoubleFunction<S> f, NavigableMap<T, S>... mps) {
        return Arrays.stream(mps).flatMap(e -> e.entrySet().stream()).map(Map.Entry::getValue).mapToDouble(f).reduce(o).orElse(0.0);
    }


    static <T extends Temporal> LocalDateTime convertToLDT(T t, LocalDate ld) {
        if (t.getClass() == LocalDateTime.class) {
            return (LocalDateTime) t;
        } else if (t.getClass() == LocalTime.class) {
            return LocalDateTime.of(ld, (LocalTime) t);
        }
        throw new IllegalArgumentException(" cannot convert ");
    }

    //static LocalDateTime convertToLDT2(? extends Temporal )

    @SafeVarargs
    public static <S> NavigableMap<LocalDateTime, S> mergeMap(NavigableMap<? extends Temporal, S>... mps) {
//        NavigableMap<LocalDateTime, S> res = Stream.of(mps).flatMap(e -> e.entrySet().stream()).collect(
//                Collectors.toMap(e -> convertToLDT(e.getKey(), HistChinaStocks.recentTradingDate)
//                        , Map.Entry::getValue, (a, b)->a, ConcurrentSkipListMap::new));

        NavigableMap<LocalDateTime, S> res = new ConcurrentSkipListMap<>();
        Stream.of(mps).flatMap(e->e.entrySet().stream()).forEach(e->{
            if (e.getKey().getClass() == LocalTime.class) {
                res.put(LocalDateTime.of(HistChinaStocks.recentTradingDate, (LocalTime) e.getKey()), e.getValue());
            } else if (e.getKey().getClass() == LocalDateTime.class) {
                res.put((LocalDateTime) e.getKey(), e.getValue());
            }
        });

        return res;


//        for (NavigableMap<? extends Temporal, S> mp : mps) {
//            if (mp.size() > 0) {
//                for (Map.Entry<? extends Temporal, S> e : mp.entrySet()) {
//                    if (e.getKey().getClass() == LocalTime.class) {
//                        res.put(LocalDateTime.of(HistChinaStocks.recentTradingDate, (LocalTime) e.getKey()), e.getValue());
//                    } else if (e.getKey().getClass() == LocalDateTime.class) {
//                        res.put((LocalDateTime) e.getKey(), e.getValue());
//                    }
//                }
//            }
//        }
        //return res;
    }

    @SafeVarargs
    public static NavigableMap<LocalDateTime, ? super Trade> mergeTradeMap(NavigableMap<LocalDateTime, ? super Trade>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));
    }


    public static NavigableMap<LocalTime, SimpleBar> priceMap1mTo5M(NavigableMap<LocalTime, SimpleBar> mp) {
        NavigableMap<LocalTime, SimpleBar> res = new ConcurrentSkipListMap<>();

        mp.forEach((key, value) -> {
            LocalTime t = roundTo5(key);
            SimpleBar sb = new SimpleBar(value);
            if (!res.containsKey(t)) {
                res.put(t, sb);
            } else {
                res.get(t).updateBar(sb);
            }
        });

//        for (Map.Entry e : mp.entrySet()) {
//            LocalTime t = roundTo5((LocalTime) e.getKey());
//            //very important to make a new SB, otherwise will pollute original mp
//            SimpleBar sb = new SimpleBar((SimpleBar) e.getValue());
//
//            if (!res.containsKey(t)) {
//                res.put(t, sb);
//            } else {
//                res.get(t).updateBar(sb);
//            }
//        }
        return res;
    }

    public static <T> NavigableMap<LocalDateTime, T> priceMapToLDT(NavigableMap<LocalTime, T> mp, LocalDate ld) {


        NavigableMap<LocalDateTime, T> res = new ConcurrentSkipListMap<>();

        mp.forEach((key, value) -> {
            if (key.isBefore(LocalTime.of(15, 1))) {
                res.put(LocalDateTime.of(ld, key), value);
            }
        });
//
//        for (Map.Entry e : mp.entrySet()) {
//            //SimpleBar sb = new SimpleBar((SimpleBar)e.getValue());
//            LocalTime lt = (LocalTime) e.getKey();
//            if (lt.isBefore(LocalTime.of(15, 1))) {
//                res.put(LocalDateTime.of(ld,lt), (T)e.getValue());
//            }
//        }
        return res;
    }

    public static LocalTime roundTo5(LocalTime t) {
        return max(LocalTime.of(9, 0), (t.getMinute() % 5 == 0) ? t : t.plusMinutes(5 - t.getMinute() % 5));
    }

    public static LocalDateTime roundTo5Ldt(LocalDateTime t) {
        return LocalDateTime.of(t.toLocalDate(), roundTo5(t.truncatedTo(ChronoUnit.MINUTES).toLocalTime()));
    }

    public static LocalTime min(LocalTime... lts) {
        return Arrays.stream(lts).reduce(LocalTime.MAX, temporalGen(LocalTime::isBefore));
    }

    public static LocalTime max(LocalTime... lts) {
        return Arrays.stream(lts).reduce(LocalTime.MIN, temporalGen(LocalTime::isAfter));
    }

    public static LocalDate max(LocalDate... lds) {
        return Arrays.stream(lds).reduce(LocalDate.MIN,temporalGen(LocalDate::isAfter));
    }

    public static BinaryOperator<LocalTime> localTimeGen(BiPredicate<LocalTime, LocalTime> bp) {
        return (a, b) -> bp.test(a, b) ? a : b;
    }

    public static <T> BinaryOperator<T> temporalGen(BiPredicate<T,T> bp) {
        return (a,b) -> bp.test(a,b)?a:b;
    }

    public static <T> Comparator<T> reverseThis(Comparator<T> in) {
        return in.reversed();
    }

    public static void simpleWriteToFile(String s, boolean b, File f) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(f, b))) {
            out.append(s);
            out.newLine();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    public static void clearFile(File f) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(f, false))) {
            out.flush();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    //        static void simpleWrite(LinkedList<String> s) {
//        try (BufferedWriter out = new BufferedWriter(new FileWriter(usTestOutput))){
//            String toWrite;
//            while((toWrite=s.poll())!=null) {
//                out.append(toWrite);
//                out.newLine();
//            }
//        } catch ( IOException x) {
//            x.printStackTrace();
//        }
//    }
    public static void simpleWrite(String s, boolean b) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(MorningTask.output, b))) {
            out.append(s);
            out.newLine();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    public static double r(double d) {
        return Math.round(100d*d)/100d;
    }

}
