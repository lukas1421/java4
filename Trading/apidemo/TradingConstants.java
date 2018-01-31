package apidemo;

import utility.Utility;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.function.Predicate;

public final class TradingConstants {
//    public static final String A50_FRONT_EXPIRY = "20171129";
//    public static final String A50_BACK_EXPIRY = "20171228";

    public static final String A50_LAST_EXPIRY = "20180130";
    public static final String A50_FRONT_EXPIRY = "20180227";
    public static final String A50_BACK_EXPIRY = "20180329";
    public static final String ftseIndex = "FTSEA50";

    public static final String GLOBALPATH = "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\Trading\\";
    public static final String tdxPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export_1m\\" : "J:\\TDX\\T0002\\export_1m\\";
    //public final static int PORT_IBAPI = 4001;
    //public final static int PORT_NORMAL = 7496;
    public static final int GLOBALWIDTH = 1900;

    public static final Predicate<? super Map.Entry<LocalTime, ?>> TRADING_HOURS =
            e -> ((e.getKey().isAfter(LocalTime.of(9, 29)) && e.getKey().isBefore(LocalTime.of(11, 31))) || Utility.PM_PRED.test(e));

    public static final Predicate<LocalDateTime> DATA_COLLECTION_TIME =
            lt -> !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SATURDAY) &&
                    !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SUNDAY)
                    && ((lt.toLocalTime().isAfter(LocalTime.of(8, 59)) && lt.toLocalTime().isBefore(LocalTime.of(11, 35)))
                    || (lt.toLocalTime().isAfter(LocalTime.of(12, 58))));

    //&& lt.toLocalTime().isBefore(LocalTime.of(15, 5))


    private static final Predicate<LocalDateTime> FUT_OPEN_PRED = (lt)
            -> !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SATURDAY) && !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SUNDAY)
            && lt.toLocalTime().isAfter(LocalTime.of(9, 0, 30));


    private TradingConstants() {
        throw new UnsupportedOperationException(" all constants ");
    }

    static volatile double CNHHKD = 1.18;
}
