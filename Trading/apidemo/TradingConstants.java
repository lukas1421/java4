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

    //public static final LocalDate A50_FRONT_EXP_DATE =;

    public static final String FTSE_INDEX = "FTSEA50";
    public static final String INDEX_000001 = "sh000001";
    public static final String INDEX_000016 = "sh000016";


    public static String getFutLastExpiry() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = now.toLocalTime();
        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getFutureExpiryDateString(today);
        } else {
            return getFutureExpiryDateString(today.minusMonths(1L));
        }
        //return A50_LAST_EXPIRY;
    }


    public static String getFutFrontExpiry() {
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();
        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);

        //pr(" getting fut front expiry this month exp date ", thisMonthExpiryDate);

        if (today.isAfter(thisMonthExpiryDate) ||
                (today.equals(thisMonthExpiryDate) && time.isAfter(LocalTime.of(15, 0)))) {
            return getFutureExpiryDateString(today.plusMonths(1L));
        } else {
            return getFutureExpiryDateString(today);
        }
        //return A50_FRONT_EXPIRY;
    }

    public static String getFutBackExpiry() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();

        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getFutureExpiryDateString(today.plusMonths(2L));
        } else {
            return getFutureExpiryDateString(today.plusMonths(1L));
        }
        //return A50_BACK_EXPIRY;
    }

    public static String getFut2BackExpiry() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();

        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getFutureExpiryDateString(today.plusMonths(3L));
        } else {
            return getFutureExpiryDateString(today.plusMonths(2L));
        }
        //return A50_BACK_EXPIRY;
    }

    public static final String GLOBALPATH = System.getProperty("os.name").equalsIgnoreCase("linux") ?
            "/home/luke/Trading/" : "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\Trading\\";

    public static final String DESKTOPPATH = System.getProperty("os.name").equalsIgnoreCase("linux") ?
            "/home/luke/" : "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\";

    public static final String tdxPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export_1m\\" : "J:\\TDX\\T0002\\export_1m\\";
    public final static int PORT_IBAPI = 4001;
    public final static int PORT_NORMAL = 7496;
    public static final int GLOBALWIDTH = 1900;

    public static final Predicate<? super Map.Entry<LocalTime, ?>> TRADING_HOURS =
            e -> ((e.getKey().isAfter(LocalTime.of(9, 29)) && e.getKey().isBefore(LocalTime.of(11, 31))) || Utility.PM_PRED.test(e));

    public static final Predicate<LocalDateTime> STOCK_COLLECTION_TIME =
            lt -> !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SATURDAY) &&
                    !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SUNDAY)
                    && ((lt.toLocalTime().isAfter(LocalTime.of(8, 59)) && lt.toLocalTime().isBefore(LocalTime.of(11, 35)))
                    || (lt.toLocalTime().isAfter(LocalTime.of(12, 57))) && lt.toLocalTime().isBefore(LocalTime.of(15, 30)));

    public static final Predicate<LocalDateTime> FUT_COLLECTION_TIME =
            ldt -> ldt.toLocalTime().isBefore(LocalTime.of(5, 0)) || ldt.toLocalTime().isAfter(LocalTime.of(8, 59));

    //&& ltof.toLocalTime().isBefore(LocalTime.of(15, 5))


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
        //LocalDate res = LocalDate.of(year, m.plus(1), 1);
        LocalDate res = LocalDate.of(year, m, 1).plusMonths(1);
//        while (res.getDayOfWeek() != TradingConstants.futExpiryWeekDay) {
//            res = res.minusDays(1);
//        }
        //pr(" get fut expiry date ", res);
        int count = 0;
        while (count < 2) {
            res = res.minusDays(1);

            if (res.getDayOfWeek() != DayOfWeek.SATURDAY && res.getDayOfWeek() != DayOfWeek.SUNDAY
                    && !res.equals(LocalDate.of(2018, 12, 31))) {
                count++;
            }
            //pr(" res count ", res, count);
            //pr("fut expiry date ", res);
        }

        return res;
    }

    private static String getFutureExpiryDateString(LocalDate d) {
        return getFutureExpiryDateString(d.getYear(), d.getMonth());
    }


    private static String getFutureExpiryDateString(int year, Month m) {
        //LocalDate res = LocalDate.of(year + ((m == Month.DECEMBER) ? 1 : 0), m.plus(1), 1);
//        LocalDate res = LocalDate.of(year, m, 1).plusMonths(1);
//        int i = 0;
//        while (i != 2) {
//            res = res.minusDays(1);
//            if (res.getDayOfWeek() != DayOfWeek.SATURDAY && res.getDayOfWeek() != DayOfWeek.SUNDAY
//                    && !res.isEqual(LocalDate.of(2018, Month.DECEMBER, 31))) {
//                i++;
//            }
//        }

        LocalDate res = getFutureExpiryDate(LocalDate.now());
        return res.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

}
