package apidemo;

import client.OrderAugmented;
import controller.ApiController;
import util.AutoOrderType;

import java.io.File;
import java.time.ZoneId;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AutoTraderMain {

    //global
    static AtomicBoolean globalTradingOn = new AtomicBoolean(false);
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
    public static ZoneId chinaZone = ZoneId.of("Asia/Shanghai");
    public static ZoneId nyZone = ZoneId.of("America/New_York");
    //position
    static volatile Map<String, Double> ibPositionMap = new ConcurrentHashMap<>();

    static long getOrderSizeForTradeType(String name, AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(name))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().isPrimaryOrder())
                .count();
    }
}



