package apidemo;

import client.OrderAugmented;
import controller.ApiController;
import util.AutoOrderType;

import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AutoTraderMain {


    //static final int MAX_FUT_LIMIT = 20;
    //static volatile AtomicBoolean canLongGlobal = new AtomicBoolean(true);
    //static volatile AtomicBoolean canShortGlobal = new AtomicBoolean(true);
    static volatile AtomicInteger autoTradeID = new AtomicInteger(100);
    public static volatile NavigableMap<Integer, OrderAugmented> globalIdOrderMap = new ConcurrentSkipListMap<>();
    //buy sell only
    static volatile AtomicBoolean noMoreSell = new AtomicBoolean(false);
    static volatile AtomicBoolean noMoreBuy = new AtomicBoolean(false);

    static ApiController apcon;

    static long getOrderSizeForTradeType(String name, AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getTicker().equals(name))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().isPrimaryOrder())
                .count();
    }
}



