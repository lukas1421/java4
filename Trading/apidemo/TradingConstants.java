package apidemo;

import utility.Utility;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Predicate;

public final class TradingConstants {
    //    public static final String A50_FRONT_EXPIRY = "20171129";
//    public static final String A50_BACK_EXPIRY = "20171228";
    //to push
    private static final DayOfWeek futExpiryWeekDay = DayOfWeek.THURSDAY;
//    public static final String A50_LAST_EXPIRY = getFutureExpiryDateString(2018, Month.FEBRUARY, futExpiryWeekDay);
//    public static final String A50_FRONT_EXPIRY = getFutureExpiryDateString(2018, Month.MARCH, futExpiryWeekDay);
//    public static final String A50_BACK_EXPIRY = getFutureExpiryDateString(2018, Month.APRIL, futExpiryWeekDay);

    public static final String A50_LAST_EXPIRY = getFutLastExpiry();
    public static final String A50_FRONT_EXPIRY = getFutFrontExpiry();
    public static final String A50_BACK_EXPIRY = getFutBackExpiry();

    public static final String ftseIndex = "FTSEA50";

    public static String getFutLastExpiry() {
        LocalDate today = LocalDate.now();
        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate)) {
            return getFutureExpiryDateString(today);
        } else {
            return getFutureExpiryDateString(today.minusMonths(1L));
        }
        //return A50_LAST_EXPIRY;
    }

    public static String getFutFrontExpiry() {
        LocalDate today = LocalDate.now();
        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate)) {
            return getFutureExpiryDateString(today.plusMonths(1L));
        } else {
            return getFutureExpiryDateString(today);
        }
        //return A50_FRONT_EXPIRY;
    }

    public static String getFutBackExpiry() {
        LocalDate today = LocalDate.now();
        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate)) {
            return getFutureExpiryDateString(today.plusMonths(2L));
        } else {
            return getFutureExpiryDateString(today.plusMonths(1L));
        }
        //return A50_BACK_EXPIRY;
    }

    public static final String GLOBALPATH = "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\Trading\\";
    public static final String DESKTOPPATH = "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\";
    public static final String tdxPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export_1m\\" : "J:\\TDX\\T0002\\export_1m\\";
    //public final static int PORT_IBAPI = 4001;
    //public final static int PORT_NORMAL = 7496;
    public static final int GLOBALWIDTH = 1900;

    public static final Predicate<? super Map.Entry<LocalTime, ?>> TRADING_HOURS =
            e -> ((e.getKey().isAfter(LocalTime.of(9, 29)) && e.getKey().isBefore(LocalTime.of(11, 31))) || Utility.PM_PRED.test(e));

    public static final Predicate<LocalDateTime> STOCK_COLLECTION_TIME =
            lt -> !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SATURDAY) &&
                    !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SUNDAY)
                    && ((lt.toLocalTime().isAfter(LocalTime.of(8, 59)) && lt.toLocalTime().isBefore(LocalTime.of(11, 35)))
                    || (lt.toLocalTime().isAfter(LocalTime.of(12, 58))) && lt.toLocalTime().isBefore(LocalTime.of(15, 30)));

    public static final Predicate<LocalDateTime> FUT_COLLECTION_TIME =
            ldt -> ldt.toLocalTime().isBefore(LocalTime.of(5, 0)) || ldt.toLocalTime().isAfter(LocalTime.of(8, 59));

    //&& lt.toLocalTime().isBefore(LocalTime.of(15, 5))


    private static final Predicate<LocalDateTime> FUT_OPEN_PRED = (lt)
            -> !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SATURDAY) && !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SUNDAY)
            && lt.toLocalTime().isAfter(LocalTime.of(9, 0, 30));


    private TradingConstants() {
        throw new UnsupportedOperationException(" all constants ");
    }

    static volatile double CNHHKD = 1.18;

    private static LocalDate getFutureExpiryDate(LocalDate d) {
        return getFutureExpiryDate(d.getYear(), d.getMonth());
    }

    private static LocalDate getFutureExpiryDate(int year, Month m) {
        LocalDate res = LocalDate.of(year, m.plus(1), 1);
        while (res.getDayOfWeek() != TradingConstants.futExpiryWeekDay) {
            res = res.minusDays(1);
        }
        return res;
    }

    private static String getFutureExpiryDateString(LocalDate d) {
        return getFutureExpiryDateString(d.getYear(), d.getMonth());
    }


    private static String getFutureExpiryDateString(int year, Month m) {
        LocalDate res = LocalDate.of(year, m.plus(1), 1);
        while (res.getDayOfWeek() != TradingConstants.futExpiryWeekDay) {
            res = res.minusDays(1);
        }
        return res.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

}
