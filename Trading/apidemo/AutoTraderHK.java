package apidemo;

import client.Contract;
import client.Order;
import client.OrderAugmented;

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
import static apidemo.ChinaData.priceMapBarDetail;
import static apidemo.XuTraderHelper.*;
import static client.Types.TimeInForce.DAY;
import static client.Types.TimeInForce.IOC;
import static historical.HistHKStocks.generateHKContract;
import static java.time.temporal.ChronoUnit.SECONDS;
import static util.AutoOrderType.HK_STOCK_DEV;
import static util.AutoOrderType.HK_STOCK_HILO;
import static utility.Utility.*;

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
    //private static volatile ConcurrentHashMap<String, LocalDateTime> lastOrderTime = new ConcurrentHashMap<>();

    private static long MAX_ORDER_HK = 4;

    public static List<String> hkNames = new ArrayList<>();

    AutoTraderHK() {
        Contract ct = generateHKContract("27");
        String symbol = ibContractToSymbol(ct);
        hkNames.add(symbol);
        hkNames.forEach((s) -> {
            if (!priceMapBarDetail.containsKey(s)) {
                priceMapBarDetail.put(s, new ConcurrentSkipListMap<>());
            }
            manualHKDevMap.put(s, new AtomicBoolean(false));
            manualHKHiloMap.put(s, new AtomicBoolean(false));
            hkOpenMap.put(s, 0.0);
            hkBidMap.put(s, 0.0);
            hkAskMap.put(s, 0.0);
            hkFreshPriceMap.put(s, 0.0);
        });
    }


    private static int hkStockSize = 100;

    public static void processeMainHK(String name, LocalDateTime nowMilli, double freshPrice) {
        String ticker = name.substring(2);
        hkOpenDeviationTrader(ticker, nowMilli, freshPrice);
        hkHiloTrader(ticker, nowMilli, freshPrice);
    }

    /**
     * hk open deviation trader
     *
     * @param ticker     stock name
     * @param nowMilli   time now
     * @param freshPrice last price
     */
    private static void hkOpenDeviationTrader(String ticker, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = generateHKContract(ticker);
        String symbol = ibContractToSymbol(ct);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double open = hkOpenMap.getOrDefault(symbol, 0.0);
        double last = hkFreshPriceMap.getOrDefault(symbol, 0.0);

        if (prices.size() < 1) {
            return;
        }

        if (lt.isBefore(ltof(9, 19)) || lt.isAfter(ltof(10, 0))) {
            return;
        }

        double manualOpen = prices.ceilingEntry(ltof(9, 20)).getValue();
        double firstTick = prices.entrySet().stream().filter(e -> e.getKey().isAfter(ltof(9, 20, 0)))
                .filter(e -> Math.abs(e.getValue() - manualOpen) > 0.01).findFirst().map(Map.Entry::getValue).get();

        LocalTime firstTickTime = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 20, 0)))
                .filter(e -> Math.abs(e.getValue() - manualOpen) > 0.01).findFirst().map(Map.Entry::getKey)
                .orElse(LocalTime.MIN);

        pr(" HK open dev, ticker, symbol, price ", nowMilli, ticker, symbol, freshPrice,
                "open manualopen firsttick, firstticktime",
                hkOpenMap.getOrDefault(symbol, 0.0), manualOpen, firstTick, firstTickTime);

        if (!manualHKDevMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 20, 0))) {
                manualHKDevMap.get(symbol).set(true);
            } else {
                if (last > open) {
                    hkOpenDevDirection.put(symbol, Direction.Long);
                    manualHKDevMap.get(symbol).set(true);
                } else if (last < open) {
                    hkOpenDevDirection.put(symbol, Direction.Short);
                    manualHKDevMap.get(symbol).set(true);
                } else {
                    hkOpenDevDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long orderSize = getOrderSizeForTradeType(symbol, HK_STOCK_DEV);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, HK_STOCK_DEV);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, HK_STOCK_DEV);
        long waitSec = (milliLastTwo < 60000) ? 300 : 10;
        if (orderSize >= MAX_ORDER_HK) {
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec) {
            if (!noMoreBuy.get() && last > open && hkOpenDevDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, hkStockSize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_DEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "HK open dev BUY", globalIdOrderMap.get(id)));
                hkOpenDevDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && last < open && hkOpenDevDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, hkStockSize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_DEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "HK open dev SELL", globalIdOrderMap.get(id)));
                hkOpenDevDirection.put(symbol, Direction.Short);
            }
        }
    }


    /**
     * hk hilo trader for hk
     *
     * @param ticker     hk stock name
     * @param nowMilli   time now
     * @param freshPrice last hk price
     */

    private static void hkHiloTrader(String ticker, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();

        Contract ct = generateHKContract(ticker);
        String symbol = ibContractToSymbol(ct);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);

        if (prices.size() < 1) {
            return;
        }

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

        pr(" HK hilo, ticker, symbol , price ", nowMilli, ticker, symbol, freshPrice,
                "currDate max min maxT minT", currDate, maxSoFar, minSoFar, maxT, minT);

        if (!manualHKHiloMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 30))) {
                manualHKHiloMap.get(symbol).set(true);
            } else {
                if (maxT.isAfter(minT)) {
                    hkHiloDirection.put(symbol, Direction.Long);
                    manualHKHiloMap.get(symbol).set(true);
                } else if (minT.isAfter(maxT)) {
                    hkHiloDirection.put(symbol, Direction.Short);
                    manualHKHiloMap.get(symbol).set(true);
                } else {
                    hkHiloDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long orderSize = getOrderSizeForTradeType(symbol, HK_STOCK_HILO);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, HK_STOCK_HILO);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, HK_STOCK_HILO);
        long waitSec = (milliLastTwo < 60000) ? 300 : 10;

        if (orderSize >= MAX_ORDER_HK) {
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && maxSoFar != 0.0 && minSoFar != 0.0) {
            if (!noMoreBuy.get() && (freshPrice > maxSoFar || maxT.isAfter(minT))
                    && hkHiloDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, hkStockSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "HK hilo buy", globalIdOrderMap.get(id)));
                hkHiloDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minSoFar || minT.isAfter(maxT))
                    && hkHiloDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, hkStockSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "HK hilo sell", globalIdOrderMap.get(id)));
                hkHiloDirection.put(symbol, Direction.Short);
            }
        }
    }
}
