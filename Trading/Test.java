import utility.TradingUtility;

import java.time.*;

import static utility.Utility.*;
import static utility.Utility.ltBtwn;


public class Test {

    private static LocalDate getSecLastFriday(LocalDate day) {
        LocalDate currDay = day.plusMonths(1L).withDayOfMonth(1).minusDays(1);
        while (currDay.getDayOfWeek() != DayOfWeek.FRIDAY) {
            currDay = currDay.minusDays(1L);
        }
        return currDay.minusDays(7L);
    }

    public static void main(String[] args) {

        for (int i = 0; i < 24; i++) {
            LocalDate ldt = LocalDate.now().plusMonths(i);
            int monthsToAddToNextExpiry = (3 - ldt.getMonthValue() % 3) % 3;
            LocalDate thisMonthExpiry = getSecLastFriday(ldt.plusMonths(monthsToAddToNextExpiry));
            LocalDate nextMonthExpiry = getSecLastFriday(ldt.plusMonths(monthsToAddToNextExpiry + 3));
            pr(ldt, thisMonthExpiry, nextMonthExpiry);
        }
    }
}

//public class Test {
//
//
//    public static void main(String[] args) {
//
//
//    }
//
//}
