package utility;

import api.TradingConstants;
import client.Contract;
import client.Order;
import client.OrderType;
import client.Types;
import historical.Request;

import javax.naming.OperationNotSupportedException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static utility.Utility.pr;

public class TradingUtility {

    public static final String A50_LAST_EXPIRY = getFutLastExpiry().format(TradingConstants.expPattern);
    public static final String A50_FRONT_EXPIRY = getFutFrontExpiry().format(TradingConstants.expPattern);
    public static final String A50_BACK_EXPIRY = getFutBackExpiry().format(TradingConstants.expPattern);
    public static volatile Map<Integer, Request> globalRequestMap = new ConcurrentHashMap<>();

    private TradingUtility() throws OperationNotSupportedException {
        throw new OperationNotSupportedException(" cannot instantiate utility class ");
    }


    public static Contract getActiveA50Contract() {
        long daysUntilFrontExp = ChronoUnit.DAYS.between(LocalDate.now(), getFutFrontExpiry());
        //return frontFut;
        pr(" **********  days until expiry **********", daysUntilFrontExp);
        if (daysUntilFrontExp <= 1) {
            pr(" using back fut ");
            return getBackFutContract();
        } else {
            pr(" using front fut ");
            return getFrontFutContract();
        }
    }

    public static Contract getActiveBTCContract() {
        Contract ct = new Contract();
        ct.symbol("GXBT");
        ct.exchange("CFECRYPTO");
        //ct.secType(Types.SecType.CONTFUT);
        ct.secType(Types.SecType.FUT);
        pr("BTC expiry ", getActiveBTCExpiry());
        ct.lastTradeDateOrContractMonth(getActiveBTCExpiry().format(Utility.futExpPattern));
        ct.currency("USD");
        return ct;
    }


    public static Contract getBackFutContract() {
        Contract ct = new Contract();
        ct.symbol("XINA50");
        ct.exchange("SGX");
        ct.currency("USD");
        ct.lastTradeDateOrContractMonth(A50_BACK_EXPIRY);
        ct.secType(Types.SecType.FUT);
        return ct;
    }

    public static Contract getFrontFutContract() {
        Contract ct = new Contract();
        ct.symbol("XINA50");
        ct.exchange("SGX");
        ct.currency("USD");
        pr("front exp date ", A50_FRONT_EXPIRY);
        ct.lastTradeDateOrContractMonth(A50_FRONT_EXPIRY);
        ct.secType(Types.SecType.FUT);
        return ct;
    }

    public static boolean isChinaStock(String s) {
        return s.startsWith("sz") || s.startsWith("sh");
    }

    public static boolean isHKStock(String s) {
        return s.startsWith("hk");
    }

    public static Order placeBidLimit(double p, double quantity) {
        return placeBidLimitTIF(p, quantity, Types.TimeInForce.DAY);
    }

    public static Order placeOfferLimit(double p, double quantity) {
        return placeOfferLimitTIF(p, quantity, Types.TimeInForce.DAY);
    }

    public static Order placeOfferLimitTIF(double p, double quantity, Types.TimeInForce tif) {
        if (quantity <= 0) throw new IllegalStateException(" cannot have negative or 0 quantity");
        //System.out.println(" place offer limit " + p);
        Order o = new Order();
        o.action(Types.Action.SELL);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.tif(tif);
        o.outsideRth(true);
        return o;
    }

    static Order placeShortSellLimitTIF(double p, double quantity, Types.TimeInForce tif) {
        if (quantity <= 0) throw new IllegalStateException(" cannot have negative or 0 quantity");
        //System.out.println(" place short sell " + p);
        Order o = new Order();
        o.action(Types.Action.SSHORT);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.tif(tif);
        o.outsideRth(true);
        return o;
    }

    public static Order placeBidLimitTIF(double p, double quantity, Types.TimeInForce tif) {
        if (quantity <= 0) throw new IllegalStateException(" cannot have 0 quantity ");
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        o.tif(tif);
        return o;
    }

    public static Order buyAtOffer(double p, double quantity) {
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        return o;
    }

    public static Order sellAtBid(double p, double quantity) {
        Order o = new Order();
        o.action(Types.Action.SELL);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        return o;
    }

    public static boolean checkTimeRangeBool(LocalTime t, int hrBeg, int minBeg, int hrEnd, int minEnd) {
        return t.isAfter(LocalTime.of(hrBeg, minBeg)) && t.isBefore(LocalTime.of(hrEnd, minEnd));
    }

    public static void outputToError(String s) {
        File output = new File(TradingConstants.GLOBALPATH + "autoError.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void outputToSpecial(String s) {
        pr(s);
        outputToError(s);
        File output = new File(TradingConstants.GLOBALPATH + "specialError.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static LocalDate getThirdWednesday(LocalDate day) {
        LocalDate currDay = LocalDate.of(day.getYear(), day.getMonth(), 1);
        while (currDay.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            currDay = currDay.plusDays(1L);
        }
        return currDay.plusDays(14L);
    }

    public static LocalDate getActiveBTCExpiry() {
        LocalDate thisMonthExpiry = getThirdWednesday(LocalDate.now());
        LocalDate nextMonthExpiry = getThirdWednesday(LocalDate.now().plusMonths(1));
        return LocalDate.now().isAfter(thisMonthExpiry) ? nextMonthExpiry : thisMonthExpiry;
    }


    private static LocalDate getFutureExpiryDate(LocalDate d) {
        LocalDate res = LocalDate.of(d.getYear(), d.getMonth(), 1).plusMonths(1);
        int count = 0;
        while (count < 2) {
            res = res.minusDays(1);
            if (res.getDayOfWeek() != DayOfWeek.SATURDAY && res.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return res;
    }

    public static LocalDate getFut2BackExpiry() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();

        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getFutureExpiryDate(today.plusMonths(3L));
        } else {
            return getFutureExpiryDate(today.plusMonths(2L));
        }
    }

    private static LocalDate getFutBackExpiry() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();

        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);

        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getFutureExpiryDate(today.plusMonths(2L));
        } else {
            return getFutureExpiryDate(today.plusMonths(1L));
        }
    }

    public static LocalDate getFutLastExpiry() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = now.toLocalTime();
        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getFutureExpiryDate(today);
        } else {
            return getFutureExpiryDate(today.minusMonths(1L));
        }
    }

    public static LocalDate getFutFrontExpiry() {
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();
        LocalDate thisMonthExpiryDate = getFutureExpiryDate(today);

        if (today.isAfter(thisMonthExpiryDate) ||
                (today.equals(thisMonthExpiryDate) && time.isAfter(LocalTime.of(15, 0)))) {
            return getFutureExpiryDate(today.plusMonths(1L));
        } else {
            return getFutureExpiryDate(today);
        }
    }
}
