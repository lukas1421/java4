package apidemo;

import client.Contract;
import client.Order;
import client.OrderAugmented;
import client.Types;
import handler.GuaranteeUSHandler;

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
import static java.time.temporal.ChronoUnit.SECONDS;
import static util.AutoOrderType.*;
import static utility.Utility.*;

public class AutoTraderUS {

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDateTime, Double>> usPriceMapDetail
            = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> usBidMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> usAskMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> usOpenMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> usFreshPriceMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> usShortableValueMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> usOpenDevDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> usHiloDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> usPMOpenDevDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> usPMHiloDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualUSDevMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualUSHiloMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualUSPMDevMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualUSPMHiloMap = new ConcurrentHashMap<>();

    private static final double US_MIN_SHORT_LEVEL = 1.5;

    private static final int MAX_US_ORDERS = 4;
    public static List<String> usSymbols = new ArrayList<>();
    private static final double US_SIZE = 100;


    public static Contract tickerToUSContract(String ticker) {
        Contract ct = new Contract();
        ct.symbol(ticker);
        ct.exchange("SMART");
        ct.currency("USD");
        ct.secType(Types.SecType.STK);
        return ct;
    }

    AutoTraderUS() {
        usSymbols.add(ibContractToSymbol(tickerToUSContract("IQ")));
        usSymbols.add(ibContractToSymbol(tickerToUSContract("QTT")));
        usSymbols.add(ibContractToSymbol(tickerToUSContract("NIO")));

        usSymbols.forEach(s -> {
            if (!priceMapBarDetail.containsKey(s)) {
                priceMapBarDetail.put(s, new ConcurrentSkipListMap<>());
            }
            usBidMap.put(s, 0.0);
            usAskMap.put(s, 0.0);
            usOpenMap.put(s, 0.0);
            usFreshPriceMap.put(s, 0.0);
            usShortableValueMap.put(s, 0.0);

            usOpenDevDirection.put(s, Direction.Flat);
            usPMOpenDevDirection.put(s, Direction.Flat);

            usHiloDirection.put(s, Direction.Flat);
            usPMHiloDirection.put(s, Direction.Flat);

            manualUSDevMap.put(s, new AtomicBoolean(false));
            manualUSPMDevMap.put(s, new AtomicBoolean(false));

            manualUSHiloMap.put(s, new AtomicBoolean(false));
            manualUSPMHiloMap.put(s, new AtomicBoolean(false));
        });
    }

    /**
     * process US traders
     *
     * @param symbol     stock name
     * @param nowMilli   time
     * @param freshPrice last price
     */
    public static void processMainUS(String symbol, LocalDateTime nowMilli, double freshPrice) {

        cancelAllOrdersAfterDeadline(nowMilli.toLocalTime(), ltof(10, 0, 0));
        cancelAllOrdersAfterDeadline(nowMilli.toLocalTime(), ltof(13, 30, 0));
        usCloseLiqTrader(symbol, nowMilli, freshPrice);

        if (!globalTradingOn.get()) {
            return;
        }

        usOpenDeviationTrader(symbol, nowMilli, freshPrice);
        usHiloTrader(symbol, nowMilli, freshPrice);
        usPostAMCutoffLiqTrader(symbol, nowMilli, freshPrice);

        usPMOpenDeviationTrader(symbol, nowMilli, freshPrice);
        usPMHiloTrader(symbol, nowMilli, freshPrice);
        usPostPMCutoffLiqTrader(symbol, nowMilli, freshPrice);
    }

    /**
     * us open deviation trader
     *
     * @param symbol     symbol
     * @param nowMilli   time now
     * @param freshPrice price
     */
    private static void usOpenDeviationTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double open = usOpenMap.getOrDefault(symbol, 0.0);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        LocalTime amStart = ltof(9, 29, 59);
        LocalTime amObservationStart = ltof(9, 29, 55);
        LocalTime cutoff = ltof(10, 0);

        cancelAfterDeadline(lt, symbol, US_STOCK_OPENDEV, cutoff);

        if (lt.isBefore(amStart) || lt.isAfter(cutoff)) {
            return;
        }
        double buySize = US_SIZE;
        double sellSize = US_SIZE;

        double manualOpen = prices.ceilingEntry(amObservationStart).getValue();

        if (!manualUSDevMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 35))) {
                outputOrderToAutoLogXU(str("Setting manual US Dev 935", lt));
                manualUSDevMap.get(symbol).set(true);
            } else {
                if (freshPrice > manualOpen) {
                    outputOrderToAutoLogXU(str("Setting manual US Dev: last>open", lt));
                    usOpenDevDirection.put(symbol, Direction.Long);
                    manualUSDevMap.get(symbol).set(true);
                } else if (freshPrice < manualOpen) {
                    outputOrderToAutoLogXU(str("Setting manual US Dev: last < open", lt));
                    usOpenDevDirection.put(symbol, Direction.Short);
                    manualUSDevMap.get(symbol).set(true);
                } else {
                    usOpenDevDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, US_STOCK_OPENDEV);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_STOCK_OPENDEV);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, US_STOCK_OPENDEV);
        int waitSec = (milliLastTwo < 60000) ? 300 : 10;

        if (numOrders >= MAX_US_ORDERS) {
            return;
        }

        pr(" US open dev#:", numOrders, lt, nowMilli, "name, price ", symbol, freshPrice,
                "open/manual open ", usOpenMap.getOrDefault(symbol, 0.0), manualOpen,
                "last order T/ millilast2/ waitsec", lastOrderTime, milliLastTwo, waitSec,
                "curr pos ", currPos, "dir: ", usOpenDevDirection.get(symbol));

        double buyPrice;
        double sellPrice;
        if (numOrders < 2) {
            buyPrice = freshPrice;
            sellPrice = freshPrice;
        } else {
            buyPrice = Math.min(freshPrice, manualOpen);
            sellPrice = Math.max(freshPrice, manualOpen);
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && usShortableValueMap.get(symbol) > US_MIN_SHORT_LEVEL)
            if (!noMoreBuy.get() && freshPrice > manualOpen && usOpenDevDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(buyPrice, buySize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_OPENDEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), "US open dev BUY#", numOrders, globalIdOrderMap.get(id),
                        "open, manual Open(9 29 55)", open, manualOpen, "buy size/ currpos", buySize, currPos,
                        "last order T, milliLast2, waitSec, nextT", lastOrderTime, milliLastTwo, waitSec,
                        lastOrderTime.plusSeconds(waitSec),
                        "dir", usOpenDevDirection.get(symbol), "manual?", manualUSDevMap.get(symbol)
                        , "short value", usShortableValueMap.get(symbol)));
                usOpenDevDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && freshPrice < manualOpen && usOpenDevDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(sellPrice, sellSize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_OPENDEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), "US open dev SELL#:", numOrders, globalIdOrderMap.get(id),
                        "open, manual Open(9 29 55)", open, manualOpen, "sell size/ currpos", sellSize, currPos,
                        "last order T, milliLast2, waitSec, nextT", lastOrderTime, milliLastTwo, waitSec,
                        lastOrderTime.plusSeconds(waitSec),
                        "dir", usOpenDevDirection.get(symbol), "manual?", manualUSDevMap.get(symbol)
                        , "short value", usShortableValueMap.get(symbol)));
                usOpenDevDirection.put(symbol, Direction.Short);
            }
    }

    /**
     * us pm open deviation trader
     *
     * @param symbol     us stock
     * @param nowMilli   time now
     * @param freshPrice last stock price
     */
    private static void usPMOpenDeviationTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        LocalTime pmStart = ltof(12, 59, 59);
        LocalTime pmObservationStart = ltof(12, 59, 55);
        LocalTime pmCutoff = ltof(13, 30);

        cancelAfterDeadline(lt, symbol, US_STOCK_PMOPENDEV, pmCutoff);

        if (lt.isBefore(pmStart) || lt.isAfter(pmCutoff)) {
            return;
        }
        double buySize = US_SIZE;
        double sellSize = US_SIZE;

        double pmOpen = prices.ceilingEntry(pmObservationStart).getValue();

        if (!manualUSPMDevMap.get(symbol).get()) {
            if (lt.isBefore(ltof(13, 5))) {
                outputOrderToAutoLogXU(str("Setting manual US PM Dev 1305", lt));
                manualUSPMDevMap.get(symbol).set(true);
            } else {
                if (freshPrice > pmOpen) {
                    outputOrderToAutoLogXU(str("Setting manual US PM Dev: last > open", lt));
                    usPMOpenDevDirection.put(symbol, Direction.Long);
                    manualUSPMDevMap.get(symbol).set(true);
                } else if (freshPrice < pmOpen) {
                    outputOrderToAutoLogXU(str("Setting manual US PM Dev: last < open", lt));
                    usPMOpenDevDirection.put(symbol, Direction.Short);
                    manualUSPMDevMap.get(symbol).set(true);
                } else {
                    usPMOpenDevDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, US_STOCK_PMOPENDEV);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_STOCK_PMOPENDEV);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, US_STOCK_PMOPENDEV);
        int waitSec = (milliLastTwo < 60000) ? 300 : 10;

        if (numOrders >= MAX_US_ORDERS) {
            return;
        }

        pr(" US pm open dev#:", numOrders, lt, nowMilli, "name, price ", symbol, freshPrice,
                "open/manual open ", usOpenMap.getOrDefault(symbol, 0.0), pmOpen,
                "last order T/ millilast2/ waitsec", lastOrderTime, milliLastTwo, waitSec,
                "curr pos ", currPos, "dir: ", usPMOpenDevDirection.get(symbol)
                , "manual:", manualUSPMDevMap.get(symbol));

        double buyPrice;
        double sellPrice;

        if (numOrders < 2) {
            buyPrice = freshPrice;
            sellPrice = freshPrice;
        } else {
            buyPrice = Math.min(freshPrice, pmOpen);
            sellPrice = Math.max(freshPrice, pmOpen);
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && usShortableValueMap.get(symbol) > US_MIN_SHORT_LEVEL)
            if (!noMoreBuy.get() && freshPrice > pmOpen && usPMOpenDevDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(buyPrice, buySize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_PMOPENDEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), "US PM open dev BUY#:", numOrders, globalIdOrderMap.get(id),
                        "pm Open(12 59 59)", pmOpen, "buy size/ currpos", buySize, currPos,
                        "last order T, milliLast2, waitSec, nextT", lastOrderTime, milliLastTwo, waitSec,
                        lastOrderTime.plusSeconds(waitSec),
                        "dir", usPMOpenDevDirection.get(symbol), "manual?", manualUSPMDevMap.get(symbol)
                        , "short value", usShortableValueMap.get(symbol)));
                usPMOpenDevDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && freshPrice < pmOpen && usPMOpenDevDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(sellPrice, sellSize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_PMOPENDEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), "US PM open dev SELL#:", numOrders, globalIdOrderMap.get(id),
                        "pm Open(12 59 59)", pmOpen, "sell size/ currpos", sellSize, currPos,
                        "last order T, milliLast2, waitSec, nextT", lastOrderTime, milliLastTwo, waitSec,
                        lastOrderTime.plusSeconds(waitSec),
                        "dir", usPMOpenDevDirection.get(symbol), "manual?", manualUSPMDevMap.get(symbol)
                        , "short value", usShortableValueMap.get(symbol)));
                usPMOpenDevDirection.put(symbol, Direction.Short);
            }
    }


    /**
     * us hilo trader
     *
     * @param symbol     symbol
     * @param nowMilli   time now
     * @param freshPrice price last
     */
    private static void usHiloTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        Contract ct = tickerToUSContract(symbol);
        LocalTime amStart = ltof(9, 29, 59);
        LocalTime amObservationStart = ltof(9, 29, 55);
        LocalTime amCutoff = ltof(10, 0);

        cancelAfterDeadline(nowMilli.toLocalTime(), symbol, US_STOCK_HILO, amCutoff);

        if (lt.isBefore(amStart) || lt.isAfter(amCutoff)) {
            return;
        }

        if (prices.size() < 1 || prices.lastKey().isBefore(amStart)) {
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
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);

        double buySize = US_SIZE;
        double sellSize = US_SIZE;

        if (!manualUSHiloMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 35))) {
                manualUSHiloMap.get(symbol).set(true);
                outputOrderToAutoLogXU(str("Setting manual US Hilo 935", lt));
            } else {
                if (maxT.isAfter(minT)) {
                    outputOrderToAutoLogXU(str("Setting manual US Hilo: maxT>minT", lt));
                    usHiloDirection.put(symbol, Direction.Long);
                    manualUSHiloMap.get(symbol).set(true);
                } else if (minT.isAfter(maxT)) {
                    outputOrderToAutoLogXU(str("Setting manual US Hilo: minT>maxT", lt));
                    usHiloDirection.put(symbol, Direction.Short);
                    manualUSHiloMap.get(symbol).set(true);
                } else {
                    usHiloDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, US_STOCK_HILO);
        if (numOrders >= MAX_US_ORDERS) {
            return;
        }

        LocalDateTime lastOrderT = getLastOrderTime(symbol, US_STOCK_HILO);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, US_STOCK_HILO);
        int waitSec = (milliLastTwo < 60000) ? 300 : 10;

//        pr(" US hilo#:", numOrders, ", name, price ", nowMilli, symbol, freshPrice,
//                "last order T, milliLast2, waitSec", lastOrderT, milliLastTwo, waitSec,
//                "max min maxt mint ", maxSoFar, minSoFar, maxT, minT,
//                lastOrderT.plusSeconds(waitSec), "dir: ", usHiloDirection.get(symbol), "currPos ", currPos,
//                "shortability", usShortableValueMap.get(symbol));

        if (SECONDS.between(lastOrderT, nowMilli) > waitSec && maxSoFar != 0.0 && minSoFar != 0.0 &&
                usShortableValueMap.get(symbol) > US_MIN_SHORT_LEVEL) {
            if (!noMoreBuy.get() && (freshPrice > maxSoFar || maxT.isAfter(minT))
                    && usHiloDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, buySize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), "US hilo buy#:", numOrders, globalIdOrderMap.get(id),
                        "buyQ/curr pos", buySize, currPos, "max min maxT minT ", maxSoFar, minSoFar, maxT, minT,
                        "last order T, milliLast2, waitSec,nexT", lastOrderT, milliLastTwo, waitSec,
                        lastOrderT.plusSeconds(waitSec), "dir", usHiloDirection.get(symbol),
                        "manual?", manualUSHiloMap.get(symbol), "short value", usShortableValueMap.get(symbol)));
                usHiloDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minSoFar || minT.isAfter(maxT))
                    && usHiloDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, sellSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), "US hilo sell#:", numOrders, globalIdOrderMap.get(id),
                        "sellQ/curr pos", sellSize, currPos, "max min maxT minT ", maxSoFar, minSoFar, maxT, minT,
                        "last order T, milliLast2, waitSec, nextT", lastOrderT, milliLastTwo, waitSec,
                        lastOrderT.plusSeconds(waitSec), "dir", usHiloDirection.get(symbol),
                        "manual?", manualUSHiloMap.get(symbol), "short value", usShortableValueMap.get(symbol)));
                usHiloDirection.put(symbol, Direction.Short);
            }
        }
    }

    /**
     * US pm hilo trader
     *
     * @param symbol     usstock
     * @param nowMilli   now time
     * @param freshPrice last stock price
     */
    private static void usPMHiloTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        Contract ct = tickerToUSContract(symbol);
        LocalTime pmStart = ltof(12, 59, 59);
        LocalTime pmObservationStart = ltof(12, 59, 55);
        LocalTime pmCutoff = ltof(13, 30);

        cancelAfterDeadline(nowMilli.toLocalTime(), symbol, US_STOCK_PMHILO, pmCutoff);

        if (lt.isBefore(pmStart) || lt.isAfter(pmCutoff)) {
            return;
        }

        if (prices.size() < 1 || prices.lastKey().isBefore(pmStart)) {
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
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);

        double buySize = US_SIZE;
        double sellSize = US_SIZE;

        if (!manualUSPMHiloMap.get(symbol).get()) {
            if (lt.isBefore(ltof(13, 5))) {
                manualUSPMHiloMap.get(symbol).set(true);
                outputOrderToAutoLogXU(str("Setting manual US PM Hilo 1305", lt));
            } else {
                if (maxPMT.isAfter(minPMT)) {
                    outputOrderToAutoLogXU(str("Setting manual US PM Hilo: maxT>minT", lt));
                    usPMHiloDirection.put(symbol, Direction.Long);
                    manualUSPMHiloMap.get(symbol).set(true);
                } else if (minPMT.isAfter(maxPMT)) {
                    outputOrderToAutoLogXU(str("Setting manual US PM Hilo: minT>maxT", lt));
                    usPMHiloDirection.put(symbol, Direction.Short);
                    manualUSPMHiloMap.get(symbol).set(true);
                } else {
                    usPMHiloDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, US_STOCK_PMHILO);
        if (numOrders >= MAX_US_ORDERS) {
            return;
        }

        LocalDateTime lastOrderT = getLastOrderTime(symbol, US_STOCK_PMHILO);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, US_STOCK_PMHILO);
        int waitSec = (milliLastTwo < 60000) ? 300 : 10;

//        pr(" US PM hilo#:", numOrders, ", name, price ", nowMilli, symbol, freshPrice,
//                "last order T, milliLast2, waitSec", lastOrderT, milliLastTwo, waitSec,
//                "max min maxt mint ", maxPMSoFar, minPMSoFar, maxPMT, minPMT,
//                lastOrderT.plusSeconds(waitSec), "dir: ", usPMHiloDirection.get(symbol), "currPos ", currPos,
//                "shortability", usShortableValueMap.get(symbol));

        if (SECONDS.between(lastOrderT, nowMilli) > waitSec && maxPMSoFar != 0.0 && minPMSoFar != 0.0
                && usShortableValueMap.get(symbol) > US_MIN_SHORT_LEVEL) {
            if (!noMoreBuy.get() && (freshPrice > maxPMSoFar || maxPMT.isAfter(minPMT))
                    && usPMHiloDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, buySize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_PMHILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), "US PM hilo buy#:", numOrders, globalIdOrderMap.get(id),
                        "buy size/curr pos", buySize, currPos,
                        "max min maxT minT ", maxPMSoFar, minPMSoFar, maxPMT, minPMT,
                        "last order T, milliLast2, waitSec,nexT", lastOrderT, milliLastTwo, waitSec,
                        lastOrderT.plusSeconds(waitSec), "dir", usPMHiloDirection.get(symbol),
                        "manual?", manualUSPMHiloMap.get(symbol), "short value", usShortableValueMap.get(symbol)));
                usPMHiloDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minPMSoFar || minPMT.isAfter(maxPMT))
                    && usPMHiloDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, sellSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_PMHILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), "US PM hilo sell#:", numOrders, globalIdOrderMap.get(id),
                        "sell size/curr pos", sellSize, currPos,
                        "max min maxT minT ", maxPMSoFar, minPMSoFar, maxPMT, minPMT,
                        "last order T, milliLast2, waitSec, nextT", lastOrderT, milliLastTwo, waitSec,
                        lastOrderT.plusSeconds(waitSec), "dir", usPMHiloDirection.get(symbol),
                        "manual?", manualUSPMHiloMap.get(symbol), "short value", usShortableValueMap.get(symbol)));
                usPMHiloDirection.put(symbol, Direction.Short);
            }
        }

    }

    /**
     * liquidation after cut off if hit
     *
     * @param symbol     symbol
     * @param nowMilli   time now
     * @param freshPrice fresh price
     */
    private static void usPostAMCutoffLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        LocalTime amStart = ltof(9, 29, 59);
        LocalTime amObservationStart = ltof(9, 29, 55);
        LocalTime amEnd = ltof(12, 0, 0);
        LocalTime amCutoff = ltof(10, 0);
        LocalTime pmClose = ltof(16, 0);

        if (lt.isBefore(amCutoff) || lt.isAfter(amEnd)) {
            return;
        }

        if (prices.lastKey().isBefore(amStart)) {
            return;
        }

        double manualOpen = prices.ceilingEntry(amObservationStart).getValue();

        long numOrderPostCutoff = getOrderSizeForTradeType(symbol, US_POST_AMCUTOFF_LIQ);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_POST_AMCUTOFF_LIQ);


        if (numOrderPostCutoff >= 1) {
            return;
        }

        if (lt.isAfter(amCutoff) && lt.isBefore(amEnd)) {
            if (currPos < 0 && freshPrice > manualOpen) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_POST_AMCUTOFF_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), " US postAMCutoff liq sell BUY #:", numOrderPostCutoff
                        , "T now", lt, " cutoff", amCutoff, "price, manualOpen", freshPrice, manualOpen
                        , globalIdOrderMap.get(id), "last order T", lastOrderTime, "currPos", currPos));
            } else if (currPos > 0 && freshPrice < manualOpen) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, currPos, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_POST_AMCUTOFF_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), " US postAMCutoff liq SELL #:", numOrderPostCutoff
                        , "T now", lt, " cutoff", amCutoff, "price, manualOpen", freshPrice, manualOpen
                        , globalIdOrderMap.get(id), "last order T", lastOrderTime, "currPos", currPos));
            }
        }
    }

    /**
     * adding US pm trader
     *
     * @param symbol     us stock
     * @param nowMilli   time now
     * @param freshPrice price now
     */
    private static void usPostPMCutoffLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        LocalTime pmStart = ltof(12, 29, 59);
        LocalTime pmObservationStart = ltof(12, 59, 55);
        LocalTime pmCutoff = ltof(13, 30);
        LocalTime pmClose = ltof(16, 0);

        if (lt.isBefore(pmCutoff) || lt.isAfter(pmClose)) {
            return;
        }

        if (prices.lastKey().isBefore(pmStart)) {
            return;
        }

        double pmOpen = prices.ceilingEntry(pmObservationStart).getValue();

        long numOrderPMCutoff = getOrderSizeForTradeType(symbol, US_POST_PMCUTOFF_LIQ);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_POST_PMCUTOFF_LIQ);


        if (numOrderPMCutoff >= 1) {
            return;
        }

        if (lt.isAfter(pmCutoff) && lt.isBefore(pmClose)) {
            if (currPos < 0 && freshPrice > pmOpen) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_POST_PMCUTOFF_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), " US postPMCutoff liq sell BUY #:", numOrderPMCutoff
                        , "T now", lt, " cutoff", pmCutoff, "last, pmOpen", freshPrice, pmOpen
                        , globalIdOrderMap.get(id), "last order T", lastOrderTime, "currPos", currPos));
            } else if (currPos > 0 && freshPrice < pmOpen) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, currPos, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_POST_PMCUTOFF_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), " US postPMCutoff liq SELL #:", numOrderPMCutoff
                        , "T now", lt, " cutoff", pmCutoff, "last, pmOpen", freshPrice, pmOpen
                        , globalIdOrderMap.get(id), "last order T", lastOrderTime, "currPos", currPos));
            }
        }
    }

    /**
     * liquidate at US close
     *
     * @param symbol     symbol
     * @param nowMilli   now time
     * @param freshPrice price
     */
    private static void usCloseLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime liqStartTime = ltof(15, 50);
        LocalTime liqEndTime = ltof(16, 0);
        Contract ct = tickerToUSContract(symbol);
        long liqWaitSecs = 60;

        if (nowMilli.toLocalTime().isBefore(liqStartTime) || nowMilli.toLocalTime().isAfter(liqEndTime)) {
            return;
        }

        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_CLOSE_LIQ);
        long numOrderCloseLiq = getOrderSizeForTradeType(symbol, US_CLOSE_LIQ);

        double pos = ibPositionMap.getOrDefault(symbol, 0.0);

        if (numOrderCloseLiq >= 1) {
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > liqWaitSecs) {
            if (pos < 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, Math.abs(pos), IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_CLOSE_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), " US close liq BUY #:", numOrderCloseLiq,
                        globalIdOrderMap.get(id), "last order T", lastOrderTime, "pos", pos));
            } else if (pos > 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, pos, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_CLOSE_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU("**********");
                outputOrderToAutoLogXU(str("NEW", o.orderId(), " US close liq SELL #:", numOrderCloseLiq
                        , globalIdOrderMap.get(id), "last order T", lastOrderTime, "pos", pos));
            }
        }
    }
}
