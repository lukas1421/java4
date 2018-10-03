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
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static apidemo.ChinaData.priceMapBarDetail;
import static apidemo.ChinaStock.*;
import static apidemo.XuTraderHelper.outputToAll;
import static client.OrderStatus.*;
import static util.AutoOrderType.*;
import static utility.Utility.pr;
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

    public static boolean checkIfHoliday(LocalDate d) {
        //pr("holiday set is ", holidaySet);
        pr(d, " is a holiday? ", holidaySet.contains(d), "!");
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
                return H9_DEV;
            case H930:
                return H930_DEV;
            case H1000:
                return H10_DEV;
            case H1030:
                return H1030_DEV;
            case H1100:
                return H11_DEV;
            case H1130:
                return H1130_DEV;
            case H1200:
                return H12_DEV;
            case H1230:
                return H1230_DEV;
            case H1300:
                return H13_DEV;
            case H1330:
                return H1330_DEV;
            case H1400:
                return H14_DEV;
            case H1430:
                return H1430_DEV;
            case H1500:
                return H15_DEV;
            case H1530:
                return H1530_DEV;
        }
        throw new IllegalStateException(" not found");
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



