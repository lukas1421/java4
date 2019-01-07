import api.TradingConstants;

import java.time.LocalDate;

import static utility.Utility.pr;

public class Test {


    private static LocalDate getLastMonthLastDay() {
        LocalDate now = LocalDate.now().withDayOfMonth(1);
        return now.minusDays(1L);
    }

    private static LocalDate getLastYearLastDay() {
        LocalDate now = LocalDate.now().withDayOfYear(1);
        return now.minusDays(1L);
    }

    public static void main(String[] args) {
        pr("last expire is", TradingConstants.getFutLastExpiry());
        pr("front expire is", TradingConstants.getFutFrontExpiry());
        pr("back expire is", TradingConstants.getFutBackExpiry());
        pr("back2 expire is", TradingConstants.getFut2BackExpiry());
    }
}
