package utility;

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
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utility {

    public static final BiPredicate<? super Map<String, ? extends Number>, String> NO_ZERO = (mp, name) -> mp.containsKey(name) && mp.get(name).doubleValue() != 0.0;
    public static final Predicate<? super Map.Entry<LocalTime, SimpleBar>> CONTAINS_NO_ZERO = e -> !e.getValue().containsZero();
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
}
