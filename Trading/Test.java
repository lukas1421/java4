import api.ChinaOptionHelper;
import utility.TradingUtility;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static api.ChinaOptionHelper.getOptionExpiryDate;
import static utility.Utility.pr;

public class Test {


    static LocalDate getNthExpiryDate(int n) {
        LocalDate today = LocalDate.now();
        LocalDate expiryThisMonth = getOptionExpiryDate(today);
        LocalDate firstMonth = today.plusMonths(today.isAfter(expiryThisMonth) ? 1 : 0);
        LocalDate secondMonth = today.plusMonths(today.isAfter(expiryThisMonth) ? 2 : 1);

        switch (n) {
            case 1:
                return getOptionExpiryDate(firstMonth);
            case 2:
                return getOptionExpiryDate(secondMonth);
            default:
                return getOptionExpiryDate(secondMonth.plusMonths((secondMonth.getMonthValue() % 3 == 0 ? 3 : 1) + (n - 3) * 3));
        }
    }

    public static void main(String[] args) {

        pr(getNthExpiryDate(1));
        pr(getNthExpiryDate(2));
        pr(getNthExpiryDate(3));
        pr(getNthExpiryDate(4));

    }

}
