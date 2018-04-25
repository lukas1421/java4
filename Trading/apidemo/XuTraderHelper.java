package apidemo;

import TradeType.MAIdea;
import auxiliary.SimpleBar;
import client.Order;
import client.OrderType;
import client.Types;
import controller.ApiController;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static utility.Utility.getStr;

public class XuTraderHelper {

    // ma trades
    private static final LocalTime AM_BEGIN = LocalTime.of(9, 0);
    private static final LocalTime PM_BEGIN = LocalTime.of(13, 0);
    private static final LocalTime OVERNIGHT_BEGIN = LocalTime.of(15, 0);

    public static NavigableMap<LocalDateTime, Double> getMAGen(NavigableMap<LocalDateTime, SimpleBar> mp, int period) {
        NavigableMap<LocalDateTime, Double> sma = new ConcurrentSkipListMap<>();
        for (Map.Entry<LocalDateTime, SimpleBar> e : mp.entrySet()) {
            long n = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).count();
            if (n > period) {
                long size = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).skip(n - period)
                        .count();
                double val = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey()))
                        .skip(n - period).mapToDouble(e2 -> e2.getValue().getAverage()).sum() / size;
                sma.put(e.getKey(), val);
            }
        }
        return sma;
    }

    private static boolean priceMAUntouched(NavigableMap<LocalDateTime, SimpleBar> mp, int period, LocalDateTime lastTradeTime) {
        NavigableMap<LocalDateTime, Double> sma = getMAGen(mp, period);
        NavigableMap<LocalDateTime, Double> smaFiltered = sma.entrySet().stream().filter(e -> e.getKey().isAfter(lastTradeTime))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));

        for (LocalDateTime t : smaFiltered.keySet()) {
            SimpleBar sb = mp.get(t);
            if (sb.includes(sma.get(t))) {
                return false;
            }
        }
        return true;
    }

    static int getUntouchedMAPeriod(NavigableMap<LocalDateTime, SimpleBar> mp, LocalDateTime lastTradeTime) {
        int defaultPeriod = 60;
        int increaseStep = 5;
        int maxPeriod = 150;
        int res = defaultPeriod;
        //NavigableMap<LocalDateTime, Double> smaSeed = XuTraderHelper.getMAGen(mp, defaultPeriod);

        while (!priceMAUntouched(mp, res, lastTradeTime) && res <= maxPeriod) {
            res += increaseStep;
            System.out.println(" res is " + res);
        }
        return res;
    }

    static void outputToAutoLog(String s) {
        System.out.println(s);
        File output = new File(TradingConstants.GLOBALPATH + "autoLog.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static void outputOrderToAutoLog(String s) {
        outputToAutoLog("****************ORDER************************");
        outputToAutoLog(s);
        outputToAutoLog("****************ORDER************************");
    }

    public static <T extends Temporal> int getPercentileForLast(NavigableMap<T, SimpleBar> map) {
        if (map.size() > 0) {
            double max = map.entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max().orElse(0.0);
            double min = map.entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min().orElse(0.0);
            double last = map.lastEntry().getValue().getClose();
            //System.out.println(getStr(" getPercentileForLast max min last ", max, min, last));
            return (int) Math.round(100d * ((last - min) / (max - min)));
        }
        return 50;
    }

    static Order placeOfferLimit(double p, double quantity) {
        System.out.println(" place offer limit " + p);
        Order o = new Order();
        o.action(Types.Action.SELL);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.tif(Types.TimeInForce.GTC);
        o.outsideRth(true);
        return o;
    }

    static Order placeBidLimit(double p, double quantity) {
        System.out.println(" place bid limit " + p);
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        o.tif(Types.TimeInForce.GTC);
        return o;
    }

    static Order buyAtOffer(double p, double quantity) {
        System.out.println(" buy at offer " + p);
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        return o;
    }

    static Order sellAtBid(double p, double quantity) {
        System.out.println(" sell at bid " + p);
        Order o = new Order();
        o.action(Types.Action.SELL);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        return o;
    }

    static void createDialog(String msg) {
        JDialog jd = new JDialog();
        jd.setFocusableWindowState(false);
        jd.setSize(new Dimension(700, 200));

        JLabel j1 = new JLabel(msg);
        j1.setPreferredSize(new Dimension(300, 60));

        j1.setFont(j1.getFont().deriveFont(25F));
        j1.setForeground(Color.red);
        j1.setHorizontalAlignment(SwingConstants.CENTER);
        jd.getContentPane().add(j1, BorderLayout.NORTH);

        jd.getContentPane().add(new JLabel(msg), BorderLayout.CENTER);
        jd.setAlwaysOnTop(false);
        jd.getContentPane().setLayout(new BorderLayout());
        jd.setVisible(true);
    }

    public static int getTimeBetweenTrade(LocalTime t) {
        if (t.isAfter(LocalTime.of(8, 59)) && t.isBefore(LocalTime.of(10, 0))) {
            return 3;
        } else {
            return 5;
        }
    }

    public static Predicate<LocalTime> futureTSession() {
        return t -> t.isAfter(LocalTime.of(8, 59)) && t.isBefore(LocalTime.of(15, 0));
    }

    static Predicate<LocalTime> futureAMSession() {
        return t -> t.isAfter(LocalTime.of(8, 59)) && t.isBefore(LocalTime.of(12, 0));
    }

    static Predicate<LocalTime> futurePMSession() {
        return t -> t.isAfter(LocalTime.of(11, 59)) && t.isBefore(LocalTime.of(15, 0));
    }

    private static Predicate<LocalTime> futureOvernightSession() {
        return t -> t.isAfter(LocalTime.of(15, 0)) || t.isBefore(LocalTime.of(5, 0));
    }

    static LocalDateTime getSessionOpenTime() {
        LocalTime sessionBeginTime = futureOvernightSession().test(LocalTime.now()) ? OVERNIGHT_BEGIN :
                (futureAMSession().test(LocalTime.now()) ? AM_BEGIN : PM_BEGIN);
        LocalDate TDate = LocalTime.now().isAfter(LocalTime.of(0, 0)) && LocalTime.now().isBefore(LocalTime.of(5, 0))
                ? LocalDate.now().minusDays(1L) : LocalDate.now();
        return LocalDateTime.of(TDate, sessionBeginTime);
    }

    static double roundToXUTradablePrice(double x, Direction dir) {
        return (Math.round(x * 10) - Math.round(x * 10) % 25 + (dir == Direction.Long ? 0 : 25)) / 10d;
    }

    static double roundToXUTradablePrice(double x) {
        return (Math.round(x * 10) - Math.round(x * 10) % 25) / 10d;
    }

    static void computeMAProfit(Set<MAIdea> l, double lastPrice) {
        double totalProfit = l.stream().mapToDouble(t -> t.getIdeaSize() * (lastPrice - t.getIdeaPrice())).sum();
        outputToAutoLog(" computeMAProfit total " + Math.round(100d * totalProfit) / 100d);
        for (MAIdea t : l) {
            outputToAutoLog(getStr(t, " PnL: ", Math.round(100d * t.getIdeaSize() * (lastPrice - t.getIdeaPrice())) / 100d));
        }
    }


    static class XUConnectionHandler implements ApiController.IConnectionHandler {
        @Override
        public void connected() {
            System.out.println("connected in XUconnectionhandler");
            XUTrader.connectionStatus = true;
            XUTrader.connectionLabel.setText(Boolean.toString(XUTrader.connectionStatus));
            XUTrader.apcon.setConnectionStatus(true);
        }

        @Override
        public void disconnected() {
            System.out.println("disconnected in XUConnectionHandler");
            XUTrader.connectionStatus = false;
            XUTrader.connectionLabel.setText(Boolean.toString(XUTrader.connectionStatus));
        }

        @Override
        public void accountList(ArrayList<String> list) {
            System.out.println(" account list is " + list);
        }

        @Override
        public void error(Exception e) {
            System.out.println(" error in XUConnectionHandler");
            e.printStackTrace();
        }

        @Override
        public void message(int id, int errorCode, String errorMsg) {
            System.out.println(getStr(" error ID ", id, " error code ", errorCode, " errormsg ", errorMsg));
        }

        @Override
        public void show(String string) {
            System.out.println(" show string " + string);
        }
    }

    public static void main(String[] args) {
        System.out.println(roundToXUTradablePrice(12312.5, Direction.Short));
    }
}
