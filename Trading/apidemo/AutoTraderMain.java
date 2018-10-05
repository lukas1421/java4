package apidemo;

import client.Order;
import client.OrderAugmented;
import client.OrderStatus;
import controller.ApiController;
import util.AutoOrderType;

import javax.swing.*;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static apidemo.ChinaData.priceMapBarDetail;
import static apidemo.ChinaStock.*;
import static apidemo.XuTraderHelper.*;
import static client.OrderStatus.*;
import static util.AutoOrderType.*;
import static utility.Utility.str;

public class AutoTraderMain extends JPanel {

    private static Set<LocalDate> holidaySet = new TreeSet<>();

    static BarModel_AUTO m_model;

    public AutoTraderMain() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "holidaySchedule.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                LocalDate d1 = LocalDate.parse(line, DateTimeFormatter.ofPattern("yyyy/M/d"));
                holidaySet.add(d1);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    //global
    public static AtomicBoolean globalTradingOn = new AtomicBoolean(false);
    public static volatile AtomicInteger autoTradeID = new AtomicInteger(100);
    public static volatile NavigableMap<Integer, OrderAugmented> globalIdOrderMap = new ConcurrentSkipListMap<>();
    public static volatile Map<Integer, Order> liveIDOrderMap = new ConcurrentHashMap<>();
    static volatile Map<String, TreeSet<Order>> liveSymbolOrderSet = new ConcurrentHashMap<>();
    static final double SGXA50_AUTO_VOL_THRESH = 0.25;

    static volatile AtomicBoolean noMoreSell = new AtomicBoolean(false);
    static volatile AtomicBoolean noMoreBuy = new AtomicBoolean(false);

    static ApiController apcon;

    static File xuDetailOutput = new File(TradingConstants.GLOBALPATH + "xuOrdersDetailed.txt");
    static File hkDetailOutput = new File(TradingConstants.GLOBALPATH + "hkOrdersDetailed.txt");
    static File usDetailOutput = new File(TradingConstants.GLOBALPATH + "usOrdersDetailed.txt");

    //zones
    public static final ZoneId chinaZone = ZoneId.of("Asia/Shanghai");
    public static final ZoneId nyZone = ZoneId.of("America/New_York");

    //position
    static volatile Map<String, Double> ibPositionMap = new ConcurrentHashMap<>();

    static long getOrderSizeForTradeType(String symbol, AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(symbol))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().isPrimaryOrder())
                .count();
    }

//    static long getOrderSizeForTradeType(String symbol, AutoOrderType type) {
//        return globalIdOrderMap.entrySet().stream()
//                .filter(e -> e.getValue().getSymbol().equals(symbol))
//                .filter(e -> e.getValue().getOrderType() == type)
//                .filter(e -> e.getValue().isPrimaryOrder())
//                .count();
//    }

    static double getFilledForType(String symbol, AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(symbol))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .mapToDouble(e -> e.getValue().getOrder().signedTotalQuantity())
                .sum();
    }

    static boolean checkIfHoliday(LocalDate d) {
        //pr(d, " is a holiday? ", holidaySet.contains(d), "!");
        return holidaySet.contains(d);
    }

    /**
     * cancelling primary orders only (can only cancel 1 min)
     *
     * @param now      time now
     * @param deadline deadeline
     */
    static void cancelAllOrdersAfterDeadline(LocalTime now, LocalTime deadline) {
        if (now.isAfter(deadline) && now.isBefore(deadline.plusMinutes(1L))) {
            globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getAugmentedOrderStatus() != Filled)
                    .filter(e -> e.getValue().getAugmentedOrderStatus() != Inactive)
                    .filter(e -> e.getValue().isPrimaryOrder())
                    .filter(e -> !isCutoffOrLiqTrader(e.getValue().getOrderType()))
                    .forEach(e -> {
                        OrderStatus sta = e.getValue().getAugmentedOrderStatus();
                        if ((sta != Filled) && (sta != PendingCancel) && (sta != Cancelled) &&
                                (sta != DeadlineCancelled)) {
                            apcon.cancelOrder(e.getValue().getOrder().orderId());
                            e.getValue().setFinalActionTime(LocalDateTime.now());
                            e.getValue().setAugmentedOrderStatus(OrderStatus.DeadlineCancelled);
                            outputToAll(str(now, " Cancel ALL after deadline ",
                                    e.getValue().getOrder().orderId(), e.getValue().getSymbol(),
                                    e.getValue().getOrderType(),
                                    "status CHG:", sta, "->", e.getValue().getAugmentedOrderStatus()));
                        }
                    });
        }
    }

    private static boolean isCutoffOrLiqTrader(AutoOrderType tt) {
        return (tt == FTSEA50_POST_AMCUTOFF || tt == FTSEA50_POST_PMCUTOFF ||
                tt == HK_POST_AMCUTOFF_LIQ || tt == HK_POST_PMCUTOFF_LIQ ||
                tt == SGXA50_POST_CUTOFF_LIQ || tt == US_POST_AMCUTOFF_LIQ ||
                tt == US_POST_PMCUTOFF_LIQ || tt == SGXA50_CLOSE_LIQ
                || tt == US_CLOSE_LIQ || tt == HK_CLOSE_LIQ);
    }

    static AutoOrderType getOrderTypeByHalfHour(HalfHour h) {
        switch (h) {
            case H900:
                return H900_DEV;
            case H930:
                return H930_DEV;
            case H1000:
                return H1000_DEV;
            case H1030:
                return H1030_DEV;
            case H1100:
                return H1100_DEV;
            case H1130:
                return H1130_DEV;
            case H1200:
                return H1200_DEV;
            case H1230:
                return H1230_DEV;
            case H1300:
                return H1300_DEV;
            case H1330:
                return H1330_DEV;
            case H1400:
                return H1400_DEV;
            case H1430:
                return H1430_DEV;
            case H1500:
                return H1500_DEV;
            case H1530:
                return H1530_DEV;
        }
        throw new IllegalStateException(" not found");
    }

    static AutoOrderType getOrderTypeByQuarterHour(QuarterHour h) {
        switch (h) {
            case Q900:
                return Q900_DEV;
            case Q915:
                return Q915_DEV;
            case Q930:
                return Q930_DEV;
            case Q945:
                return Q945_DEV;

            case Q1000:
                return Q1000_DEV;
            case Q1015:
                return Q1015_DEV;
            case Q1030:
                return Q1030_DEV;
            case Q1045:
                return Q1045_DEV;

            case Q1100:
                return Q1100_DEV;
            case Q1115:
                return Q1115_DEV;
            case Q1130:
                return Q1130_DEV;
            case Q1145:
                return Q1145_DEV;

            case Q1200:
                return Q1200_DEV;
            case Q1215:
                return Q1215_DEV;
            case Q1230:
                return Q1230_DEV;
            case Q1245:
                return Q1245_DEV;

            case Q1300:
                return Q1300_DEV;
            case Q1315:
                return Q1315_DEV;
            case Q1330:
                return Q1330_DEV;
            case Q1345:
                return Q1345_DEV;

            case Q1400:
                return Q1400_DEV;
            case Q1415:
                return Q1415_DEV;
            case Q1430:
                return Q1430_DEV;
            case Q1445:
                return Q1445_DEV;

            case Q1500:
                return Q1500_DEV;
            case Q1515:
                return Q1515_DEV;
            case Q1530:
                return Q1530_DEV;
            case Q1545:
                return Q1545_DEV;
        }
        throw new IllegalStateException(" not found");
    }


    public static LocalTime ltof(int h, int m) {
        return LocalTime.of(h, m);
    }

    static LocalTime ltof(int h, int m, int s) {
        return LocalTime.of(h, m, s);
    }

    static LocalDateTime getLastOrderTime(String symbol, AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(symbol))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().isPrimaryOrder())
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrderTime())
                .orElse(sessionOpenT());
    }


    static long lastTwoOrderMilliDiff(String symbol, AutoOrderType type) {
        long numOrders = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(symbol))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().isPrimaryOrder())
                .count();
        if (numOrders < 2) {
            return Long.MAX_VALUE;
        } else {
            LocalDateTime last = globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getSymbol().equals(symbol))
                    .filter(e -> e.getValue().getOrderType() == type)
                    .filter(e -> e.getValue().isPrimaryOrder())
                    .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                    .map(e -> e.getValue().getOrderTime()).orElseThrow(() -> new IllegalArgumentException("no"));
            LocalDateTime secLast = globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getSymbol().equals(symbol))
                    .filter(e -> e.getValue().getOrderType() == type)
                    .filter(e -> e.getValue().isPrimaryOrder())
                    .map(e -> e.getValue().getOrderTime())
                    .filter(e -> e.isBefore(last))
                    .max(Comparator.comparing(Function.identity())).orElseThrow(() -> new IllegalArgumentException("no"));
            return ChronoUnit.MILLIS.between(secLast, last);
        }
    }

    /**
     * cancel order of type after deadline
     *
     * @param now      time now
     * @param symbol   symbol
     * @param type     order type
     * @param deadline deadline after which to cut
     */
    static void cancelAfterDeadline(LocalTime now, String symbol, AutoOrderType type, LocalTime deadline) {
        if (now.isAfter(deadline) && now.isBefore(deadline.plusMinutes(1L))) {
            globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getSymbol().equals(symbol))
                    .filter(e -> e.getValue().getOrderType() == type)
                    .filter(e -> e.getValue().getAugmentedOrderStatus() != Filled)
                    .filter(e -> e.getValue().getAugmentedOrderStatus() != Inactive)
                    .forEach(e -> {
                        OrderStatus sta = e.getValue().getAugmentedOrderStatus();
                        if ((sta != Filled) && (sta != PendingCancel) && (sta != Cancelled) &&
                                (sta != DeadlineCancelled)) {
                            apcon.cancelOrder(e.getValue().getOrder().orderId());
                            e.getValue().setFinalActionTime(LocalDateTime.now());
                            e.getValue().setAugmentedOrderStatus(OrderStatus.DeadlineCancelled);
                            String msg = str(now, " Cancel after deadline ",
                                    e.getValue().getOrder().orderId(), "status CHG:",
                                    sta, "->", e.getValue().getAugmentedOrderStatus());
                            outputSymbolMsg(e.getValue().getSymbol(), msg);
                            outputToAll(msg);
                        }
                    });
        }
    }

    static int minuteToQuarterHour(int min) {
        return (min - min % 15);
    }

    private class BarModel_AUTO extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            return priceMapBarDetail.size();
            //return symbolNamesFull.size();
        }

        @Override
        public int getColumnCount() {
            return 10;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "名";
                case 2:
                    return "业";
                case 3:
                    return "参";
                default:
                    return null;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {

            String name = symbolNamesFull.get(rowIn);
            //String name = priceMapBarDetail.keySet().stream().collect(toList()).get(rowIn);

            switch (col) {
                //T
                case 0:
                    return name;
                //名
                case 1:
                    return nameMap.get(name);
                //业
                case 2:
                    return industryNameMap.get(name);
                //bench simple
                case 3:
                    return benchSimpleMap.getOrDefault(name, "");

                default:
                    return null;
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                case 2:
                    return String.class;

                default:
                    return String.class;
            }
        }
    }
}



