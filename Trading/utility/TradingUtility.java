package utility;

import AutoTraderOld.XuTraderHelper;
import api.TradingConstants;
import client.Contract;
import client.Order;
import client.OrderType;
import client.Types;

import javax.naming.OperationNotSupportedException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static utility.Utility.pr;

public class TradingUtility {

    private TradingUtility() throws OperationNotSupportedException {
        throw new OperationNotSupportedException(" cannot instantiate utility class ");
    }


    public static Contract gettingActiveContract() {
        long daysUntilFrontExp = ChronoUnit.DAYS.between(LocalDate.now(),
                LocalDate.parse(TradingConstants.A50_FRONT_EXPIRY, DateTimeFormatter.ofPattern("yyyyMMdd")));
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

    public static Contract getBackFutContract() {
        Contract ct = new Contract();
        ct.symbol("XINA50");
        ct.exchange("SGX");
        ct.currency("USD");
        ct.lastTradeDateOrContractMonth(TradingConstants.A50_BACK_EXPIRY);
        ct.secType(Types.SecType.FUT);
        return ct;
    }

    public static Contract getFrontFutContract() {
        Contract ct = new Contract();
        ct.symbol("XINA50");
        ct.exchange("SGX");
        ct.currency("USD");
        pr("front exp date ", TradingConstants.A50_FRONT_EXPIRY);
        ct.lastTradeDateOrContractMonth(TradingConstants.A50_FRONT_EXPIRY);
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
        System.out.println(" place offer limit " + p);
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
        System.out.println(" place short sell " + p);
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
        System.out.println(" place bid limit " + p);
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
        System.out.println(" buy at offer " + p);
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        return o;
    }

    public static Order sellAtBid(double p, double quantity) {
        System.out.println(" sell at bid " + p);
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
        pr(s);
        File output = new File(TradingConstants.GLOBALPATH + "autoError.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
