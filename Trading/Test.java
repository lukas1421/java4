import auxiliary.SimpleBar;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

import static api.AutoTraderMain.chinaZone;
import static api.AutoTraderMain.nyZone;
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
