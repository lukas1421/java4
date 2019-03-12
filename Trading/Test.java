import java.time.LocalDate;

import static utility.Utility.pr;

public class Test {


    public static LocalDate getLastMonthLastDay(LocalDate d) {
        LocalDate now = d.withDayOfMonth(1);
        return now.minusDays(1L);
    }

    public static void main(String[] args) {

        LocalDate today = LocalDate.now();
        pr(today.getDayOfWeek());


        //pr(getLastMonthLastDay(LocalDate.of(2019, Month.MARCH, 1)));
    }
}
