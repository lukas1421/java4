package apidemo;

import client.Order;
import client.OrderAugmented;
import controller.ApiController;
import util.AutoOrderType;

import javax.swing.*;
import java.io.*;
import java.time.LocalDate;
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
    public static volatile Map<String, TreeSet<Order>> liveSymbolOrderSet = new ConcurrentHashMap<>();
    public static final double XU_AUTO_VOL_THRESH = 0.25;


    //buy sell only
    static volatile AtomicBoolean noMoreSell = new AtomicBoolean(false);
    static volatile AtomicBoolean noMoreBuy = new AtomicBoolean(false);

    static ApiController apcon;

    //orders
    //static File xuOrderOutput = new File(TradingConstants.GLOBALPATH + "orders.txt");
    ////static File hkOrderOutput = new File(TradingConstants.GLOBALPATH + "hkorders.txt");

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
        //pr(d, " is a holiday? ", holidaySet.contains(d), "!");
        return holidaySet.contains(d);
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



