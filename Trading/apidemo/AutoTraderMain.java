package apidemo;

import client.OrderAugmented;
import controller.ApiController;
import util.AutoOrderType;

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

import static utility.Utility.pr;

public class AutoTraderMain {

    private static Set<LocalDate> holidaySet = new TreeSet<>();

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


    //buy sell only
    static volatile AtomicBoolean noMoreSell = new AtomicBoolean(false);
    static volatile AtomicBoolean noMoreBuy = new AtomicBoolean(false);

    static ApiController apcon;

    //orders
    static File xuOrderOutput = new File(TradingConstants.GLOBALPATH + "orders.txt");
    static File xuDetailOutput = new File(TradingConstants.GLOBALPATH + "ordersDetailed.txt");
    static File hkOrderOutput = new File(TradingConstants.GLOBALPATH + "hkorders.txt");
    static File hkDetailOutput = new File(TradingConstants.GLOBALPATH + "hkordersDetailed.txt");

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
        pr(d, " is a holiday? ", holidaySet.contains(d), "!");
        return holidaySet.contains(d);
    }
}



