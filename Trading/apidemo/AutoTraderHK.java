package apidemo;

import client.Contract;
import client.Order;
import client.OrderAugmented;
import client.Types;

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
import static apidemo.AutoTraderXU.*;
import static apidemo.XuTraderHelper.*;
import static historical.HistHKStocks.generateHKContract;
import static java.time.temporal.ChronoUnit.SECONDS;
import static util.AutoOrderType.HK_STOCK_DEV;
import static util.AutoOrderType.HK_STOCK_HILO;
import static utility.Utility.pr;
import static utility.Utility.str;

public class AutoTraderHK {

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

    private static long MAX_ORDER_HK = 4;

    public static List<String> hkNames = new ArrayList<>();

    AutoTraderHK() {
        //start with 1 name
        hkNames.add("700");
        hkNames.forEach((s) -> {
            if (!ChinaData.priceMapBarDetail.containsKey(s)) {
                ChinaData.priceMapBarDetail.put(s, new ConcurrentSkipListMap<>());
            }

            manualHKDevMap.put(s, new AtomicBoolean(false));
            manualHKHiloMap.put(s, new AtomicBoolean(false));
            hkOpenMap.put(s, 0.0);
            hkBidMap.put(s, 0.0);
            hkAskMap.put(s, 0.0);
            hkFreshPriceMap.put(s, 0.0);
        });
    }

    public Contract ct = generateHKContract("700");
    private static int hkStockSize = 100;

    public static void processeMainHK(String name, LocalDateTime nowMilli, double freshPrice) {
        hkOpenDeviationTrader(name, nowMilli, freshPrice);
        hkHiloTrader(name, nowMilli, freshPrice);
    }

    /**
     * hk open deviation trader
     *
     * @param name       stock name
     * @param nowMilli   time now
     * @param freshPrice last price
     */
    private static void hkOpenDeviationTrader(String name, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = generateHKContract(name);
        //persistence is an issue
        NavigableMap<LocalTime, Double> prices = ChinaData.priceMapBarDetail.get(name);
        double open = hkOpenMap.getOrDefault(name, 0.0);
        double last = hkFreshPriceMap.getOrDefault(name, 0.0);
        Direction currDir = hkOpenDevDirection.get(name);

        if (prices.size() < 1) {
            return;
        }
        LocalDate thisDate = nowMilli.toLocalDate();


        if (lt.isBefore(ltof(9, 20)) || lt.isAfter(ltof(10, 0))) {
            return;
        }

        double manualOpen = prices.ceilingEntry(ltof(9, 20)).getValue();
        double firstTick = prices.entrySet().stream().filter(e -> e.getKey().isAfter(ltof(9, 20, 0)))
                .filter(e -> Math.abs(e.getValue() - manualOpen) > 0.01).findFirst().map(Map.Entry::getValue).get();

//        double pmOpen = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(ltof(12, 58)).getValue();
//
//        double pmFirstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
//                .filter(e -> e.getKey().isAfter(ltof(12, 58, 0)))
//                .filter(e -> Math.abs(e.getValue() - pmOpen) > 0.01).findFirst().map(Map.Entry::getValue)
//                .orElse(pmOpen);

        LocalTime firstTickTime = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 20, 0)))
                .filter(e -> Math.abs(e.getValue() - manualOpen) > 0.01).findFirst().map(Map.Entry::getKey)
                .orElse(LocalTime.MIN);

        pr(" HK open dev, name, price ", nowMilli, name, freshPrice,
                "open manualopen firsttick, firstticktime",
                hkOpenMap.getOrDefault(name, 0.0), manualOpen, firstTick, firstTickTime);

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
        long milliLastTwo = lastTwoOrderMilliDiff(name, HK_STOCK_DEV);
        LocalDateTime lastOrderTime = getLastOrderTime(name, HK_STOCK_DEV);
        long waitSec = (milliLastTwo < 60000) ? 300 : 10;
        if (orderSize >= MAX_ORDER_HK) {
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec) {
            if (!noMoreBuy.get() && last > open && hkOpenDevDirection.get(name) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, hkStockSize, Types.TimeInForce.DAY);
                globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, HK_STOCK_DEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "HK open dev BUY", globalIdOrderMap.get(id)));
                hkOpenDevDirection.put(name, Direction.Long);
            } else if (!noMoreSell.get() && last < open && hkOpenDevDirection.get(name) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, hkStockSize, Types.TimeInForce.DAY);
                globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, HK_STOCK_DEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "HK open dev SELL", globalIdOrderMap.get(id)));
                hkOpenDevDirection.put(name, Direction.Short);
            }
        }
    }


    /**
     * hk hilo trader for hk
     *
     * @param name       hk stock name
     * @param nowMilli   time now
     * @param freshPrice last hk price
     */

    private static void hkHiloTrader(String name, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        NavigableMap<LocalTime, Double> prices = ChinaData.priceMapBarDetail.get(name);
        Contract ct = generateHKContract(name);
        Direction currDir = hkHiloDirection.get(name);

        pr(" HK hilo, name, price ", nowMilli, name, freshPrice);

        if (lt.isBefore(ltof(9, 20)) || lt.isAfter(ltof(10, 0))) {
            return;
        }

        LocalTime lastKey = prices.lastKey();

        double maxSoFar = prices.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minSoFar = prices.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        //LocalDate currDate = prices.firstKey().toLocalDate();
        LocalDate currDate = nowMilli.toLocalDate();
        LocalTime maxT = getFirstMaxTPred(prices, e -> e.isAfter(ltof(9, 19)));
        LocalTime minT = getFirstMinTPred(prices, e -> e.isAfter(ltof(9, 19)));

        pr("hk ", name, " currDate max min maxT minT ", currDate, maxSoFar, minSoFar, maxT, minT);

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

        long orderSize = getOrderSizeForTradeType(name, HK_STOCK_HILO);
        long milliLastTwo = lastTwoOrderMilliDiff(name, HK_STOCK_HILO);
        LocalDateTime lastOrderTime = getLastOrderTime(name, HK_STOCK_HILO);
        long waitSec = (milliLastTwo < 60000) ? 300 : 10;

        if (orderSize >= MAX_ORDER_HK) {
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && maxSoFar != 0.0 && minSoFar != 0.0) {
            if (!noMoreBuy.get() && (freshPrice > maxSoFar || maxT.isAfter(minT))
                    && hkHiloDirection.get(name) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, hkStockSize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, HK_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "HK hilo buy", globalIdOrderMap.get(id)));
                hkHiloDirection.put(name, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minSoFar || minT.isAfter(maxT))
                    && hkHiloDirection.get(name) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, hkStockSize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, HK_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "HK hilo sell", globalIdOrderMap.get(id)));
                hkHiloDirection.put(name, Direction.Short);
            }
        }
    }


}
