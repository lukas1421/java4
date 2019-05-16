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
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static utility.Utility.*;

public class TradingUtility {

    public static final String A50_LAST_EXPIRY = getXINA50PrevExpiry().format(TradingConstants.expPattern);
    public static final String A50_FRONT_EXPIRY = getXINA50FrontExpiry().format(TradingConstants.expPattern);
    public static final String A50_BACK_EXPIRY = getXINA50BackExpiry().format(TradingConstants.expPattern);
    public static volatile Map<Integer, Request> globalRequestMap = new ConcurrentHashMap<>();

    private TradingUtility() throws OperationNotSupportedException {
        throw new OperationNotSupportedException(" cannot instantiate utility class ");
    }


    public static Contract getActiveA50Contract() {
        long daysUntilFrontExp = ChronoUnit.DAYS.between(LocalDate.now(), getXINA50FrontExpiry());
        //return frontFut;
        pr(" **********  days until expiry **********", daysUntilFrontExp, getXINA50FrontExpiry());
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
//        pr("front exp date ", A50_FRONT_EXPIRY);
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

    public static void outputToFile(String s, File f) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(f, true))) {
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
        LocalDateTime ldt = LocalDateTime.now();

        LocalDate thisMonthExpiry = getThirdWednesday(ldt.toLocalDate());
        LocalDate nextMonthExpiry = getThirdWednesday(ldt.toLocalDate().plusMonths(1));

        ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();

        return usLdt.isAfter(LocalDateTime.of(thisMonthExpiry, ltof(16, 0)))
                ? nextMonthExpiry : thisMonthExpiry;
    }

    public static LocalDate get2ndBTCExpiry() {
        LocalDateTime ldt = LocalDateTime.now();

        LocalDate thisMonthExpiry = getThirdWednesday(ldt.toLocalDate());
        LocalDate plus1MonthExpiry = getThirdWednesday(ldt.toLocalDate().plusMonths(1));
        LocalDate plus2MonthExpiry = getThirdWednesday(ldt.toLocalDate().plusMonths(2));

        ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();

        return usLdt.isAfter(LocalDateTime.of(thisMonthExpiry, ltof(16, 0)))
                ? plus2MonthExpiry : plus1MonthExpiry;
    }


    public static LocalDate getPrevBTCExpiry() {
        LocalDateTime ldt = LocalDateTime.now();

        LocalDate lastMonthExpiry = getThirdWednesday(ldt.toLocalDate().minusMonths(1));
        LocalDate thisMonthExpiry = getThirdWednesday(ldt.toLocalDate());

        ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();

        return usLdt.isAfter(LocalDateTime.of(thisMonthExpiry, ltof(16, 0)))
                ? thisMonthExpiry : lastMonthExpiry;
    }

    public static LocalDate getPrevBTCExpiryGivenTime(LocalDateTime ldt) {

        LocalDate lastMonthExpiry = getThirdWednesday(ldt.toLocalDate().minusMonths(1));
        LocalDate thisMonthExpiry = getThirdWednesday(ldt.toLocalDate());

        ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();

        return usLdt.isAfter(LocalDateTime.of(thisMonthExpiry, ltof(16, 0)))
                ? thisMonthExpiry : lastMonthExpiry;
    }


    private static LocalDate getXINA50ExpiryDate(LocalDate d) {
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

        LocalDate thisMonthExpiryDate = getXINA50ExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getXINA50ExpiryDate(today.plusMonths(3L));
        } else {
            return getXINA50ExpiryDate(today.plusMonths(2L));
        }
    }

    private static LocalDate getXINA50BackExpiry() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();

        LocalDate thisMonthExpiryDate = getXINA50ExpiryDate(today);

        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getXINA50ExpiryDate(today.plusMonths(2L));
        } else {
            return getXINA50ExpiryDate(today.plusMonths(1L));
        }
    }

    public static LocalDate getXINA50PrevExpiry() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = now.toLocalTime();
        LocalDate thisMonthExpiryDate = getXINA50ExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getXINA50ExpiryDate(today);
        } else {
            return getXINA50ExpiryDate(today.minusMonths(1L));
        }
    }

    public static LocalDate getXINA50FrontExpiry() {
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();
        LocalDate thisMonthExpiryDate = getXINA50ExpiryDate(today);

        if (today.isAfter(thisMonthExpiryDate) ||
                (today.equals(thisMonthExpiryDate) && time.isAfter(LocalTime.of(15, 0)))) {
            return getXINA50ExpiryDate(today.plusMonths(1L));
        } else {
            return getXINA50ExpiryDate(today);
        }
    }

    private static Contract getShanghaiConnectStock(String symb) {
        Contract ct = new Contract();
        ct.symbol(symb);
        ct.exchange("SEHKNTL");
        ct.currency("CNH");
        ct.secType(Types.SecType.STK);
        return ct;
    }

    public static LocalDate getPrevMonthDay(Contract ct, LocalDate defaultDate) {
        if (ct.secType() == Types.SecType.FUT || ct.secType() == Types.SecType.CONTFUT) {
            if (ct.symbol().equalsIgnoreCase("GXBT")) {
                return getPrevBTCExpiry();
            } else if (ct.symbol().equalsIgnoreCase("XINA50")) {
                return getXINA50PrevExpiry();
            }
        }
        return defaultDate;
    }

    private static Contract getOilContract() {
        Contract ct = new Contract();
        ct.symbol("CL");
        ct.exchange("NYMEX");
        ct.currency("USD");
        ct.secType(Types.SecType.FUT);
        ct.lastTradeDateOrContractMonth("20190220");
        return ct;
    }
}
