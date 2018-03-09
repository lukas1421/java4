package apidemo;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import saving.ChinaVolSave;
import saving.HibernateUtil;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Pattern;

public class ChinaOptionHelper {

    static final Pattern DATA_PATTERN = Pattern.compile("(?<=var\\shq_str_)((?:sh|sz)\\d{6})");
    static final Pattern CALL_NAME_PATTERN =
            Pattern.compile("(?<=var\\shq_str_OP_UP_510050\\d{4}=)\"(.*?),\"");
    static final Pattern PUT_NAME_PATTERN =
            Pattern.compile("(?<=var\\shq_str_OP_DOWN_510050\\d{4}=)\"(.*?),\"");
    static final Pattern OPTION_PATTERN =
            Pattern.compile("(?<=var\\shq_str_)(CON_OP_\\d{8})=\"(.*?)\";");

    private ChinaOptionHelper() {
        throw new UnsupportedOperationException(" utility class ");
    }

    public static LocalDate getOptionExpiryDate(int year, Month m) {
        LocalDate res = LocalDate.of(year, m.plus(1), 1);

        while (res.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            res = res.minusDays(1);
        }
        //System.out.println(getStr(" return expiry date for month ",year,m, res));
        return res;
    }

    public static void saveVolsEODHib() {
        CompletableFuture.runAsync(() -> {
            SessionFactory sessionF = HibernateUtil.getSessionFactory();
            try (Session session = sessionF.openSession()) {
                session.getTransaction().begin();
                try {

                    int i = 0;
                    while (i < 500) {
                        ChinaVolSave v = new ChinaVolSave();
                        session.saveOrUpdate(v);
                        i++;
                    }

//                    symbolNames.forEach(name -> {
//                        if (Utility.noZeroArrayGen(name, openMap, maxMap, minMap, priceMap, closeMap, sizeMap)) {
//                            ChinaSaveOHLCYV c = new ChinaSaveOHLCYV(name, openMap.get(name), maxMap.get(name), minMap.get(name),
//                                    priceMap.get(name), closeMap.get(name), sizeMap.get(name).intValue());
//
//                            session.saveOrUpdate(c);
//                        }
//                    });
                    session.getTransaction().commit();
                } catch (org.hibernate.exception.LockAcquisitionException x) {
                    x.printStackTrace();
                    session.getTransaction().rollback();
                    session.close();
                }
            }
        });

    }

//    public static void main(String[] args) {
//        System.out.println(getOptionExpiryDate(2018, Month.MARCH));
//        System.out.println(getOptionExpiryDate(2018, Month.APRIL));
//        System.out.println(getOptionExpiryDate(2018, Month.JUNE));
//        System.out.println(getOptionExpiryDate(2018, Month.SEPTEMBER));
//    }

    static double simpleSolver(double target, DoubleUnaryOperator o) {
        double lowerGuess = 0.0;
        double higherGuess = 1.0;
        double res;
        double midGuess = (lowerGuess + higherGuess) / 2;
        while (!((Math.abs(target - o.applyAsDouble(midGuess)) < 0.000001) || midGuess == 0.0 || midGuess == 1.0)) {
            if (o.applyAsDouble(midGuess) < target) {
                lowerGuess = midGuess;
            } else {
                higherGuess = midGuess;
            }
            midGuess = (lowerGuess + higherGuess) / 2;
            //System.out.println("mid guess is " + midGuess);
        }
        return Math.round(10000d * midGuess) / 10000d;
    }

    public static double interpolateVol(double strike, NavigableMap<Double, Double> mp) {
        if (mp.size() > 0) {
            if (mp.containsKey(strike)) {
                return mp.get(strike);
            } else {
                if (strike >= mp.firstKey() && strike <= mp.lastKey()) {
                    double higherKey = mp.ceilingKey(strike);
                    double lowerKey = mp.floorKey(strike);
                    return mp.get(lowerKey) + (strike - lowerKey) / (higherKey - lowerKey)
                            * (mp.get(higherKey) - mp.get(lowerKey));
                } else {
                    return 0.0;
                }
            }
        }
        return 0.0;
    }

    static double getVolByMoneyness(NavigableMap<Integer, Double> moneyVolMap, int moneyness) {

        if (moneyVolMap.size() > 0) {
            if (moneyVolMap.containsKey(moneyness)) {
                return moneyVolMap.get(moneyness);
            } else {
                Map.Entry<Integer, Double> ceilEntry = moneyVolMap.ceilingEntry(moneyness);
                Map.Entry<Integer, Double> floorEntry = moneyVolMap.floorEntry(moneyness);
                return floorEntry.getValue()
                        + (1.0 * (moneyness - floorEntry.getKey()) / (ceilEntry.getKey() - floorEntry.getKey()))
                        * (ceilEntry.getValue() - floorEntry.getValue());
            }
        }
        return 0.0;

    }
}
