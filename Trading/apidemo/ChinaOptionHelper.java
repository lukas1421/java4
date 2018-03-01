package apidemo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;

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
}
