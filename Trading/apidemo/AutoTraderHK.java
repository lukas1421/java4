package apidemo;

import client.Contract;
import client.Order;
import client.OrderAugmented;
import client.Types;
import controller.ApiController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static apidemo.AutoTraderMain.*;
import static apidemo.AutoTraderXU.ldtof;
import static apidemo.AutoTraderXU.ltof;
import static apidemo.XuTraderHelper.*;
import static historical.HistHKStocks.generateHKContract;
import static util.AutoOrderType.HK_STOCK_DEV;
import static util.AutoOrderType.HK_STOCK_HILO;
import static utility.Utility.pr;
import static utility.Utility.str;

public class AutoTraderHK  {

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDateTime, Double>> hkPriceMapDetail
            = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkBidMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkAskMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkOpenMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkFreshPriceMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> hkOpenDevDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> hkHiloDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualHKDevMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualHKHiloMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, LocalDateTime> lastOrderTime = new ConcurrentHashMap<>();

    public static long MAX_ORDER_HK = 4;

    public static List<String> hkNames = new ArrayList<>();

    AutoTraderHK() {
        //start with 1 name
        hkNames.add("700");

        hkNames.forEach((s) -> {
            manualHKDevMap.put(s, new AtomicBoolean(false));
            manualHKHiloMap.put(s, new AtomicBoolean(false));
        });


    }

    public Contract ct = generateHKContract("700");
    private static int hkStockSize = 100;

    /**
     * hk open deviation trader
     *
     * @param name       stock name
     * @param nowMilli   time now
     * @param freshPrice last price
     */
    static void hkOpenDeviationTrader(String name, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = generateHKContract(name);
        NavigableMap<LocalDateTime, Double> prices = hkPriceMapDetail.get(name);
        double open = hkOpenMap.getOrDefault(name, 0.0);
        double last = hkFreshPriceMap.getOrDefault(name, 0.0);
        Direction currDir = hkOpenDevDirection.get(name);

        pr(" HK open dev, name, price ", nowMilli, name, freshPrice);

        if (lt.isBefore(ltof(9, 20)) || lt.isAfter(ltof(10, 0))) {
            return;
        }

        if (!manualHKDevMap.get(name).get()) {
            if (lt.isBefore(ltof(9, 20, 0))) {
                manualHKDevMap.get(name).set(true);
            } else {
                if (last > open) {
                    hkOpenDevDirection.put(name, Direction.Long);
                    manualHKDevMap.get(name).set(true);
                } else if (last < open) {
                    hkOpenDevDirection.put(name, Direction.Short);
                    manualHKDevMap.get(name).set(true);
                } else {
                    hkOpenDevDirection.put(name, Direction.Flat);
                }
            }
        }

        long orderSize = getOrderSizeForTradeType(name, HK_STOCK_DEV);

        if (orderSize >= MAX_ORDER_HK) {
            return;
        }

        if (!noMoreBuy.get() && last > open && hkOpenDevDirection.get(name) != Direction.Long) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, hkStockSize, Types.TimeInForce.DAY);
            globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, HK_STOCK_DEV));
            apcon.placeOrModifyOrder(ct, o, new ApiController.IOrderHandler.DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "HK open dev BUY", globalIdOrderMap.get(id)));
            hkOpenDevDirection.put(name, Direction.Long);
        } else if (!noMoreSell.get() && last < open && hkOpenDevDirection.get(name) != Direction.Short) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, hkStockSize, Types.TimeInForce.DAY);
            globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, HK_STOCK_DEV));
            apcon.placeOrModifyOrder(ct, o, new ApiController.IOrderHandler.DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "HK open dev SELL", globalIdOrderMap.get(id)));
            hkOpenDevDirection.put(name, Direction.Short);
        }
    }


    /**
     * hk hilo trader for hk
     *
     * @param name       hk stock name
     * @param nowMilli   time now
     * @param freshPrice last hk price
     */

    static void hkHiloTrader(String name, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        NavigableMap<LocalDateTime, Double> prices = hkPriceMapDetail.get(name);
        Contract ct = generateHKContract(name);
        Direction currDir = hkHiloDirection.get(name);

        pr(" HK hilo, name, price ", nowMilli, name, freshPrice);

        if (lt.isBefore(ltof(9, 20)) || lt.isAfter(ltof(10, 0))) {
            return;
        }

        LocalDateTime lastKey = prices.lastKey();

        double maxSoFar = prices.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minSoFar = prices.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalDate currDate = prices.firstKey().toLocalDate();
        LocalDateTime maxT = getFirstMaxTPredLdt(prices, e -> e.isAfter(ldtof(currDate, ltof(9, 19))));
        LocalDateTime minT = getFirstMinTPredLdt(prices, e -> e.isAfter(ldtof(currDate, ltof(9, 19))));

        if (!manualHKHiloMap.get(name).get()) {
            if (lt.isBefore(ltof(9, 30))) {
                manualHKHiloMap.get(name).set(true);
            } else {
                if (maxT.isAfter(minT)) {
                    hkHiloDirection.put(name, Direction.Long);
                    manualHKHiloMap.get(name).set(true);
                } else if (minT.isAfter(maxT)) {
                    hkHiloDirection.put(name, Direction.Short);
                    manualHKHiloMap.get(name).set(true);
                } else {
                    hkHiloDirection.put(name, Direction.Flat);
                }
            }
        }

        long orderSize = globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getTicker().equals(name))
                .filter(e -> e.getValue().getOrderType() == HK_STOCK_HILO)
                .filter(e -> e.getValue().isPrimaryOrder())
                .count();

        if (orderSize >= MAX_ORDER_HK) {
            return;
        }

        if (!noMoreBuy.get() && (freshPrice > maxSoFar || maxT.isAfter(minT)) && hkHiloDirection.get(name) != Direction.Long) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, hkStockSize, Types.TimeInForce.DAY);
            globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, HK_STOCK_HILO));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
            outputOrderToAutoLog(str(o.orderId(), "HK hilo buy", globalIdOrderMap.get(id)));
            hkHiloDirection.put(name, Direction.Long);
        } else if (!noMoreSell.get() && (freshPrice < minSoFar || minT.isAfter(maxT)) && hkHiloDirection.get(name) != Direction.Short) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, hkStockSize, Types.TimeInForce.IOC);
            globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, HK_STOCK_HILO));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
            outputOrderToAutoLog(str(o.orderId(), "HK hilo sell", globalIdOrderMap.get(id)));
            hkHiloDirection.put(name, Direction.Short);
        }
    }


}
