package apidemo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.NavigableMap;
import java.util.function.DoubleUnaryOperator;

import static utility.Utility.getStr;

public class ChinaOptionHelper {

    private ChinaOptionHelper() {
        throw new UnsupportedOperationException(" utility class ");
    }

    public static LocalDate getOptionExpiryDate(int year, Month m) {
        LocalDate res = LocalDate.of(year, m.plus(1), 1);

        while(res.getDayOfWeek()!= DayOfWeek.WEDNESDAY) {
            res = res.minusDays(1);
        }
        System.out.println(getStr(" return expiry date for month ",year,m, res));
        return res;
    }

    public static void main(String[] args) {
        System.out.println(getOptionExpiryDate(2018, Month.MARCH));
        System.out.println(getOptionExpiryDate(2018, Month.APRIL));
        System.out.println(getOptionExpiryDate(2018, Month.JUNE));
        System.out.println(getOptionExpiryDate(2018, Month.SEPTEMBER));
    }

    static double simpleSolver(double target, DoubleUnaryOperator o, double lowerGuess, double higherGuess) {
        double guess = 0.0;
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
}
