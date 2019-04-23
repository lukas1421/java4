import utility.TradingUtility;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static utility.Utility.pr;

public class Test {

    public static LocalDate getThirdWednesday(LocalDate day) {
        LocalDate monthFirstDay = LocalDate.of(day.getYear(), day.getMonth(), 1);
        LocalDate currDay = monthFirstDay;
        while (currDay.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            currDay = currDay.plusDays(1L);
        }
        return currDay.plusDays(14L);
    }

    public static LocalDate getNextBitcoinFutExpiry() {
        LocalDate thisMonthExpiry = getThirdWednesday(LocalDate.now());
        LocalDate nextMonthExpiry = getThirdWednesday(LocalDate.now().plusMonths(1));
        return LocalDate.now().isAfter(thisMonthExpiry) ? nextMonthExpiry : thisMonthExpiry;
    }

    public static void main(String[] args) {
        pr(getNextBitcoinFutExpiry());
    }
}
