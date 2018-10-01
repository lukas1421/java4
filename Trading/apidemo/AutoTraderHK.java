package apidemo;

import client.Contract;
import client.Order;
import client.OrderAugmented;
import client.Types;
import handler.GuaranteeHKHandler;

import javax.swing.*;
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
import static java.time.temporal.ChronoUnit.SECONDS;
import static util.AutoOrderType.*;
import static utility.Utility.*;

public class AutoTraderHK extends JPanel {

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDateTime, Double>> hkPriceMapDetail
            = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkBidMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkAskMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkOpenMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkFreshPriceMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkShortableValueMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> hkOpenDevDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> hkHiloDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualHKDevMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualHKHiloMap = new ConcurrentHashMap<>();

    private static volatile ConcurrentHashMap<String, Direction> hkPMHiloDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualHKPMHiloMap = new ConcurrentHashMap<>();

    private static final double MIN_SHORT_LEVEL = 1.5;

    private static long MAX_ORDER_HK = 4;

    public static List<String> hkSymbols = new ArrayList<>();

    AutoTraderHK() {
//        Contract ct = tickerToHKContract("1293");
//        String symbol = ibContractToSymbol(ct);
        hkSymbols.add(ibContractToSymbol(tickerToHKContract("1293")));
        hkSymbols.add(ibContractToSymbol(tickerToHKContract("3690")));
        hkSymbols.add(ibContractToSymbol(tickerToHKContract("3333")));

        hkSymbols.forEach((s) -> {
            if (!priceMapBarDetail.containsKey(s)) {
                priceMapBarDetail.put(s, new ConcurrentSkipListMap<>());
            }

            hkBidMap.put(s, 0.0);
            hkAskMap.put(s, 0.0);
            hkOpenMap.put(s, 0.0);
            hkFreshPriceMap.put(s, 0.0);
            hkShortableValueMap.put(s, 0.0);

            hkOpenDevDirection.put(s, Direction.Flat);
            hkHiloDirection.put(s, Direction.Flat);
            manualHKDevMap.put(s, new AtomicBoolean(false));
            manualHKHiloMap.put(s, new AtomicBoolean(false));
        });
    }

    private static int getMinimumSizeHK(double price) {
        if (price < 10) {
            return 2000;
        } else {
            return 1000;
        }
    }

    public static void processeMainHK(String symbol, LocalDateTime nowMilli, double freshPrice) {
        cancelAllOrdersAfterDeadline(nowMilli.toLocalTime(), ltof(10, 0, 0));

        if (!globalTradingOn.get()) {
            return;
        }

        //hkOpenDeviationTrader(symbol, nowMilli, freshPrice);
        hkHiloTrader(symbol, nowMilli, freshPrice);
        hkPostAMCutoffLiqTrader(symbol, nowMilli, freshPrice);

        hkPMHiloTrader(symbol, nowMilli, freshPrice);
        hkPostPMCutoffLiqTrader(symbol, nowMilli, freshPrice);

        hkCloseLiqTrader(symbol, nowMilli, freshPrice);

    }

    public static String hkSymbolToTicker(String symbol) {
        return symbol.substring(2);
    }

    /**
     * cut pos after cutoff if on the wrong side of manual open
     *
     * @param symbol     hk stock symbol (starting with hk)
     * @param nowMilli   time now in milliseconds
     * @param freshPrice last stock price
     */
    private static void hkPostAMCutoffLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        LocalTime amCutoff = ltof(10, 0);
        LocalTime amObservationStart = ltof(9, 19, 0);

        double safetyMargin = freshPrice * 0.003;

        if (lt.isBefore(amCutoff)) {
            return;
        }

        long numOrders = getOrderSizeForTradeType(symbol, HK_POST_AMCUTOFF_LIQ);

        if (numOrders >= 1) {
            return;
        }

        String ticker = hkSymbolToTicker(symbol);
        Contract ct = tickerToHKContract(ticker);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        double manualOpen = prices.ceilingEntry(amObservationStart).getValue();

        if (currPos < 0 && freshPrice > manualOpen) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_POST_AMCUTOFF_LIQ));
            apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK post AM cutoff liq BUY#:", numOrders,
                    globalIdOrderMap.get(id), "freshPrice, manualOpen", freshPrice, manualOpen,
                    "shortability ", hkShortableValueMap.get(symbol), "safety margin ", safetyMargin));
        } else if (currPos > 0 && freshPrice < manualOpen) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, currPos, DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_POST_AMCUTOFF_LIQ));
            apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK post AM cutoff liq SELL#:", numOrders,
                    globalIdOrderMap.get(id), "freshPrice, manualOpen", freshPrice, manualOpen,
                    "shortability ", hkShortableValueMap.get(symbol), "safety margin ", safetyMargin));
        }
    }

    private static void hkPostPMCutoffLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        LocalTime pmCutoff = ltof(13, 30);
        LocalTime pmObservationStart = ltof(12, 58, 0);
        double safetyMargin = freshPrice * 0.003;

        if (lt.isBefore(pmCutoff)) {
            return;
        }

        long numOrders = getOrderSizeForTradeType(symbol, HK_POST_PMCUTOFF_LIQ);

        if (numOrders >= 1) {
            return;
        }

        String ticker = hkSymbolToTicker(symbol);
        Contract ct = tickerToHKContract(ticker);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        double manualPMOpen = prices.ceilingEntry(pmObservationStart).getValue();

        if (currPos < 0 && freshPrice > manualPMOpen - safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_POST_PMCUTOFF_LIQ));
            apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK PM post cutoff liq BUY#:", numOrders,
                    globalIdOrderMap.get(id), "freshPrice, manualPMOpen", freshPrice, manualPMOpen,
                    "shortability ", hkShortableValueMap.get(symbol), "safety margin ", safetyMargin));
        } else if (currPos > 0 && freshPrice < manualPMOpen + safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, currPos, DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_POST_PMCUTOFF_LIQ));
            apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK PM post cutoff liq SELL#:", numOrders,
                    globalIdOrderMap.get(id), "freshPrice, manualPMOpen", freshPrice, manualPMOpen,
                    "shortability ", hkShortableValueMap.get(symbol), "safety margin ", safetyMargin));
        }
    }

    /**
     * hk open deviation trader
     *
     * @param symbol     stock name
     * @param nowMilli   time now
     * @param freshPrice last price
     */
    private static void hkOpenDeviationTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        LocalTime cutoff = ltof(10, 0);
        LocalTime amObservationStart = ltof(9, 19, 0);
        LocalTime amTradingStart = ltof(9, 28, 0);
        String ticker = hkSymbolToTicker(symbol);
        Contract ct = tickerToHKContract(ticker);
        int size = getMinimumSizeHK(freshPrice);

        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double open = hkOpenMap.getOrDefault(symbol, 0.0);

        if (prices.size() == 0) {
            return;
        }

        if (lt.isBefore(amTradingStart) || lt.isAfter(cutoff)) {
            return;
        }

        double manualOpen = prices.ceilingEntry(amObservationStart).getValue();

        double firstTick = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(amObservationStart))
                .filter(e -> Math.abs(e.getValue() - manualOpen) > 0.01).findFirst().map(Map.Entry::getValue)
                .orElse(0.0);

        LocalTime firstTickTime = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(amObservationStart))
                .filter(e -> Math.abs(e.getValue() - manualOpen) > 0.01).findFirst().map(Map.Entry::getKey)
                .orElse(LocalTime.MIN);


        if (!manualHKDevMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 35, 0))) {
                outputDetailedHK(symbol, str("setting manual HK dev:before 935 ", symbol, lt));
                manualHKDevMap.get(symbol).set(true);
            } else {
                if (freshPrice > open) {
                    outputDetailedHK(symbol, str("setting manual HK dev: fresh>open ", symbol, lt));
                    hkOpenDevDirection.put(symbol, Direction.Long);
                    manualHKDevMap.get(symbol).set(true);
                } else if (freshPrice < open) {
                    outputDetailedHK(symbol, str("setting manual HK dev: fresh<open", symbol, lt));
                    hkOpenDevDirection.put(symbol, Direction.Short);
                    manualHKDevMap.get(symbol).set(true);
                } else {
                    hkOpenDevDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, HK_STOCK_DEV);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, HK_STOCK_DEV);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, HK_STOCK_DEV);
        long waitSec = (milliLastTwo < 60000) ? 300 : 10;
        if (numOrders >= MAX_ORDER_HK) {
            return;
        }

        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);


        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && hkShortableValueMap.getOrDefault(symbol, 0.0) >
                MIN_SHORT_LEVEL) {
            if (!noMoreBuy.get() && freshPrice > open && hkOpenDevDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, size, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_DEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputDetailedHK(symbol, "**********");
                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK open dev BUY#:", numOrders, globalIdOrderMap.get(id),
                        "open, manualOpen, ft, ftT", open, manualOpen, firstTick, firstTickTime,
                        "last Order T, milliLastTwo", lastOrderTime, milliLastTwo,
                        "pos", currPos, "dir", hkOpenDevDirection.get(symbol), "manual?", manualHKDevMap.get(symbol),
                        "shortability ", hkShortableValueMap.get(symbol)));
                hkOpenDevDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && freshPrice < open && hkOpenDevDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, size, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_DEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputDetailedHK(symbol, "**********");
                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK open dev SELL#:", numOrders, globalIdOrderMap.get(id),
                        "open, manualOpen, ft, ftT", open, manualOpen, firstTick, firstTickTime,
                        "last Order T, milliLastTwo", lastOrderTime, milliLastTwo,
                        "pos", currPos, "dir", hkOpenDevDirection.get(symbol), "manual?", manualHKDevMap.get(symbol),
                        "shortability ", hkShortableValueMap.get(symbol)));
                hkOpenDevDirection.put(symbol, Direction.Short);
            }
        }
        pr(" open deviation hk ", prices);
        pr(" HK open dev #: ", numOrders, nowMilli, ticker, symbol, "price:", freshPrice,
                "open,manualOpen,ft, ftT", open, manualOpen, firstTick, firstTickTime,
                "last order T", lastOrderTime, "milliLastTwo", milliLastTwo, "waitSec", waitSec,
                "pos", currPos, "dir:", hkOpenDevDirection.get(symbol), "manual? ", manualHKDevMap.get(symbol),
                "shortable value ", hkShortableValueMap.get(symbol));
    }

    /**
     * liquidate holdings at hk close
     *
     * @param symbol     hk stock
     * @param nowMilli   time in milliseconds
     * @param freshPrice last stock price
     */
    private static void hkCloseLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        String ticker = hkSymbolToTicker(symbol);
        Contract ct = tickerToHKContract(ticker);
        if (lt.isBefore(ltof(15, 50)) || lt.isAfter(ltof(16, 0))) {
            return;
        }
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        long numOrders = getOrderSizeForTradeType(symbol, HK_CLOSE_LIQ);
        if (numOrders >= 1) {
            return;
        }
        if (currPos < 0) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_CLOSE_LIQ));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK close Liq BUY:#:", numOrders,
                    globalIdOrderMap.get(id), "pos", currPos, "time", lt));
        } else if (currPos > 0) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, currPos, DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_CLOSE_LIQ));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK close Liq SELL:#:", numOrders,
                    globalIdOrderMap.get(id), "pos", currPos, "time", lt));
        }
    }

    /**
     * hk hilo trader for hk
     *
     * @param symbol     hk stock name
     * @param nowMilli   time now
     * @param freshPrice last hk price
     */

    private static void hkHiloTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();

        LocalTime cutoff = ltof(10, 0);
        LocalTime amObservationStart = ltof(9, 19, 0);
        LocalTime amTradingStart = ltof(9, 28);

        String ticker = hkSymbolToTicker(symbol);
        Contract ct = tickerToHKContract(ticker);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);

        int size = getMinimumSizeHK(freshPrice);

        if (prices.size() <= 1) {
            return;
        }
        if (lt.isBefore(amTradingStart) || lt.isAfter(cutoff)) {
            return;
        }
        LocalTime lastKey = prices.lastKey();
        double maxSoFar = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(amObservationStart))
                .filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minSoFar = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(amObservationStart))
                .filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalTime maxT = getFirstMaxTPred(prices, e -> e.isAfter(amObservationStart));
        LocalTime minT = getFirstMinTPred(prices, e -> e.isAfter(amObservationStart));

        if (!manualHKHiloMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 35))) {
                outputDetailedHK(symbol, str(" setting manual HK hilo: pre 935", symbol, lt));
                manualHKHiloMap.get(symbol).set(true);
            } else {
                if (maxT.isAfter(minT)) {
                    outputDetailedHK(symbol, str(" setting manual HK hilo: maxT>minT", symbol, lt));
                    hkHiloDirection.put(symbol, Direction.Long);
                    manualHKHiloMap.get(symbol).set(true);
                } else if (minT.isAfter(maxT)) {
                    outputDetailedHK(symbol, str(" setting manual HK hilo: minT>maxT", symbol, lt));
                    hkHiloDirection.put(symbol, Direction.Short);
                    manualHKHiloMap.get(symbol).set(true);
                } else {
                    hkHiloDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, HK_STOCK_HILO);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, HK_STOCK_HILO);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, HK_STOCK_HILO);
        long waitSec = (milliLastTwo < 60000) ? 300 : 10;
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        int buySize = size * ((numOrders == 0 || numOrders == (MAX_ORDER_HK - 1)) ? 1 : 2);
        int sellSize = size * ((numOrders == 0 || numOrders == (MAX_ORDER_HK - 1)) ? 1 : 2);

        if (numOrders >= MAX_ORDER_HK) {
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && maxSoFar != 0.0 && minSoFar != 0.0
                && hkShortableValueMap.getOrDefault(symbol, 0.0) > MIN_SHORT_LEVEL) {
            if (!noMoreBuy.get() && (freshPrice > maxSoFar || maxT.isAfter(minT))
                    && hkHiloDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, buySize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
                outputDetailedHK(symbol, "**********");
                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK hilo buy#:", numOrders, globalIdOrderMap.get(id),
                        "max min maxT minT ", maxSoFar, minSoFar, maxT, minT, "pos", currPos,
                        "last order T, milliLast2, waitSec", lastOrderTime, milliLastTwo, waitSec,
                        "dir, manual ", hkHiloDirection.get(symbol), manualHKHiloMap.get(symbol),
                        "shortability ", hkShortableValueMap.get(symbol)));
                hkHiloDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minSoFar || minT.isAfter(maxT))
                    && hkHiloDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, sellSize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
                outputDetailedHK(symbol, "**********");
                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK hilo sell#:", numOrders, globalIdOrderMap.get(id),
                        "max min maxT minT ", maxSoFar, minSoFar, maxT, minT, "pos", currPos,
                        "last order T, milliLast2, wait Sec", lastOrderTime, milliLastTwo, waitSec,
                        "dir, manual ", hkHiloDirection.get(symbol), manualHKHiloMap.get(symbol),
                        "shortability ", hkShortableValueMap.get(symbol)));
                hkHiloDirection.put(symbol, Direction.Short);
            }
        }
        pr(" HK hilo#: ", numOrders, lt, ticker, symbol, "price", freshPrice, "pos", currPos,
                "max min maxT minT", maxSoFar, minSoFar, maxT, minT,
                " last order T", lastOrderTime, " milliLastTwo ", milliLastTwo, "wait Sec", waitSec,
                "dir:", hkHiloDirection.get(symbol), "manual?", manualHKHiloMap.get(symbol),
                "shortable value?", hkShortableValueMap.get(symbol));
    }

    /**
     * hk hilo trader for hk (PM)
     *
     * @param symbol     hk stock name
     * @param nowMilli   time now
     * @param freshPrice last hk price
     */

    private static void hkPMHiloTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();

        LocalTime pmCutoff = ltof(13, 30);
        LocalTime pmObservationStart = ltof(12, 58, 0);
        LocalTime pmTradingStart = ltof(12, 58);

        String ticker = hkSymbolToTicker(symbol);
        Contract ct = tickerToHKContract(ticker);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);

        int size = getMinimumSizeHK(freshPrice);

        if (prices.size() <= 1) {
            return;
        }
        if (lt.isBefore(pmTradingStart) || lt.isAfter(pmCutoff)) {
            return;
        }
        LocalTime lastKey = prices.lastKey();
        double maxPMSoFar = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(pmObservationStart))
                .filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minPMSoFar = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(pmObservationStart))
                .filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalTime maxPMT = getFirstMaxTPred(prices, e -> e.isAfter(pmObservationStart));
        LocalTime minPMT = getFirstMinTPred(prices, e -> e.isAfter(pmObservationStart));

        if (!manualHKPMHiloMap.get(symbol).get()) {
            if (lt.isBefore(ltof(13, 5))) {
                outputDetailedHK(symbol, str(" setting manual HK PM hilo: pre 13:05", lt));
                manualHKPMHiloMap.get(symbol).set(true);
            } else {
                if (maxPMT.isAfter(minPMT)) {
                    outputDetailedHK(symbol, str(" setting manual HK PM hilo: maxT>minT", lt));
                    hkPMHiloDirection.put(symbol, Direction.Long);
                    manualHKPMHiloMap.get(symbol).set(true);
                } else if (minPMT.isAfter(maxPMT)) {
                    outputDetailedHK(symbol, str(" setting manual HK PM hilo: minT>maxT", lt));
                    hkPMHiloDirection.put(symbol, Direction.Short);
                    manualHKPMHiloMap.get(symbol).set(true);
                } else {
                    hkPMHiloDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, HK_STOCK_PMHILO);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, HK_STOCK_PMHILO);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, HK_STOCK_PMHILO);
        long waitSec = (milliLastTwo < 60000) ? 300 : 10;
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        int buySize = size * ((numOrders == 0 || numOrders == (MAX_ORDER_HK - 1)) ? 1 : 2);
        int sellSize = size * ((numOrders == 0 || numOrders == (MAX_ORDER_HK - 1)) ? 1 : 2);

        if (numOrders >= MAX_ORDER_HK) {
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && maxPMSoFar != 0.0 && minPMSoFar != 0.0
                && hkShortableValueMap.getOrDefault(symbol, 0.0) > MIN_SHORT_LEVEL) {
            if (!noMoreBuy.get() && (freshPrice > maxPMSoFar || maxPMT.isAfter(minPMT))
                    && hkPMHiloDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, buySize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_PMHILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
                outputDetailedHK(symbol, "**********");
                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK PM hilo buy#:", numOrders, globalIdOrderMap.get(id),
                        "max min maxT minT ", maxPMSoFar, minPMSoFar, maxPMT, minPMT, "pos", currPos,
                        "last order T, milliLast2, waitSec", lastOrderTime, milliLastTwo, waitSec,
                        "dir, manual ", hkPMHiloDirection.get(symbol), manualHKPMHiloMap.get(symbol),
                        "shortability ", hkShortableValueMap.get(symbol)));
                hkPMHiloDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minPMSoFar || minPMT.isAfter(maxPMT))
                    && hkPMHiloDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, sellSize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_PMHILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
                outputDetailedHK(symbol, "**********");
                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK PM hilo sell#:", numOrders, globalIdOrderMap.get(id),
                        "max min maxT minT ", maxPMSoFar, minPMSoFar, maxPMT, minPMT, "pos", currPos,
                        "last order T, milliLast2, wait Sec", lastOrderTime, milliLastTwo, waitSec,
                        "dir, manual ", hkPMHiloDirection.get(symbol), manualHKPMHiloMap.get(symbol),
                        "shortability ", hkShortableValueMap.get(symbol)));
                hkPMHiloDirection.put(symbol, Direction.Short);
            }
        }
        pr(" HK PM hilo#: ", numOrders, lt, ticker, symbol, "price", freshPrice, "pos", currPos,
                "max min maxT minT", maxPMSoFar, minPMSoFar, maxPMT, minPMT,
                " last order T", lastOrderTime, " milliLastTwo ", milliLastTwo, "wait Sec", waitSec,
                "dir:", hkPMHiloDirection.get(symbol), "manual?", manualHKPMHiloMap.get(symbol),
                "shortable value?", hkShortableValueMap.get(symbol));
    }


    public static Contract tickerToHKContract(String ticker) {
        Contract ct = new Contract();
        ct.symbol(ticker);
        ct.exchange("SEHK");
        ct.currency("HKD");
        ct.secType(Types.SecType.STK);
        return ct;
    }
}
