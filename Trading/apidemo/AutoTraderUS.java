package apidemo;

import client.*;
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
    private static volatile ConcurrentHashMap<String, Direction> usOpenDevDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> usHiloDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualUSDevMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualUSHiloMap = new ConcurrentHashMap<>();

    //public static volatile ;
    private static final int MAX_US_ORDERS = 4;
    public static List<String> usSymbols = new ArrayList<>();
    private static final double US_SIZE = 100;
    static String ticker = "IQ";


    public static Contract tickerToUSContract(String ticker) {
        Contract ct = new Contract();
        ct.symbol(ticker);
        ct.exchange("SMART");
        ct.currency("USD");
        ct.secType(Types.SecType.STK);
        return ct;
    }

    AutoTraderUS() {
        Contract iq = tickerToUSContract("IQ");
        String iqSymbol = ibContractToSymbol(iq);
        usSymbols.add(iqSymbol);
        usSymbols.forEach(s -> {
            if (!priceMapBarDetail.containsKey(s)) {
                priceMapBarDetail.put(s, new ConcurrentSkipListMap<>());
            }
            usBidMap.put(s, 0.0);
            usAskMap.put(s, 0.0);
            usOpenMap.put(s, 0.0);
            usFreshPriceMap.put(s, 0.0);
            usHiloDirection.put(s, Direction.Flat);
            usOpenDevDirection.put(s, Direction.Flat);
            manualUSDevMap.put(s, new AtomicBoolean(false));
            manualUSHiloMap.put(s, new AtomicBoolean(false));
        });
    }

    public static void processMainUS(String symbol, LocalDateTime nowMilli, double freshPrice) {
        if (!globalTradingOn.get()) {
            return;
        }
        usOpenDeviationTrader(symbol, nowMilli, freshPrice);
        usHiloTrader(symbol, nowMilli, freshPrice);
        USLiqTrader(symbol, nowMilli, freshPrice);
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
        LocalTime cutoff = ltof(10, 0);

        cancelAfterDeadline(nowMilli.toLocalTime(), symbol, US_STOCK_OPENDEV, cutoff);

        if (lt.isBefore(ltof(9, 28)) || lt.isAfter(ltof(10, 30))) {
            return;
        }
        double buySize = US_SIZE;
        double sellSize = US_SIZE;

        if (lt.isAfter(cutoff)) {
            if (currPos > 0) {
                buySize = 0;
                sellSize = currPos;
            } else if (currPos < 0) {
                buySize = Math.abs(currPos);
                sellSize = 0;
            } else {
                return;
            }
        }


        if (!manualUSDevMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 35))) {
                outputOrderToAutoLogXU(str("Setting manual US Dev 935", lt));
                manualUSDevMap.get(symbol).set(true);
            } else {
                if (freshPrice > open) {
                    outputOrderToAutoLogXU(str("Setting manual US Dev: last>open", lt));
                    usOpenDevDirection.put(symbol, Direction.Long);
                    manualUSDevMap.get(symbol).set(true);
                } else if (freshPrice < open) {
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

        double manualOpen = prices.ceilingEntry(ltof(9, 20)).getValue();

        if (numOrders >= MAX_US_ORDERS) {
            return;
        }

        pr(" US open dev", lt, nowMilli, "name, price ", symbol, freshPrice,
                "open/manual open ", usOpenMap.getOrDefault(symbol, 0.0), manualOpen,
                "last order T/ millilast2/ waitsec", lastOrderTime, milliLastTwo, waitSec,
                "curr pos ", currPos, "dir: ", usOpenDevDirection.get(symbol));


        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec)
            if (!noMoreBuy.get() && freshPrice > open && usOpenDevDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, buySize, Types.TimeInForce.DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_OPENDEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLogXU(str(o.orderId(), "US open dev BUY", globalIdOrderMap.get(id),
                        "buy size/ currpos", buySize, currPos,
                        "last order T, milliLast2, waitsec", lastOrderTime, milliLastTwo, waitSec,
                        "dir", usOpenDevDirection.get(symbol)));
                usOpenDevDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && freshPrice < open && usOpenDevDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o;
                if (currPos > 0) {
                    o = placeOfferLimitTIF(freshPrice, Math.min(sellSize, currPos), DAY);
                } else {
                    o = placeShortSellLimitTIF(freshPrice, sellSize, DAY);
                }
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_OPENDEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLogXU(str(o.orderId(), "US open dev SELL", globalIdOrderMap.get(id),
                        "sell size/ currpos", sellSize, currPos,
                        "last order T, milliLast2, waitsec", lastOrderTime, milliLastTwo, waitSec,
                        "dir", usOpenDevDirection.get(symbol)));
                usOpenDevDirection.put(symbol, Direction.Short);
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
        LocalTime cutoff = ltof(10, 0);

        //(symbol,US_STOCK_HILO,nowMilli,);
        cancelAfterDeadline(nowMilli.toLocalTime(), symbol, US_STOCK_HILO, cutoff);

        if (lt.isBefore(ltof(9, 28)) || lt.isAfter(ltof(10, 30))) {
            return;
        }

        if (prices.size() < 1) {
            return;
        }

        LocalTime lastKey = prices.lastKey();
        double maxSoFar = prices.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);
        double minSoFar = prices.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalTime maxT = getFirstMaxTPred(prices, e -> true);
        LocalTime minT = getFirstMaxTPred(prices, e -> true);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);

        double buySize = US_SIZE;
        double sellSize = US_SIZE;

        if (lt.isAfter(cutoff)) {
            if (currPos > 0) {
                buySize = 0;
                sellSize = currPos;
            } else if (currPos < 0) {
                buySize = Math.abs(currPos);
                sellSize = 0;
            } else {
                return;
            }
        }

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
        int waitSec = milliLastTwo < 60000 ? 300 : 10;

        pr(" US hilo, name, price ", nowMilli, symbol, freshPrice,
                "last order T, milliLast2, wait sec", lastOrderT, milliLastTwo, waitSec,
                "dir: ", usHiloDirection.get(symbol), "currPos ", currPos);

        if (SECONDS.between(lastOrderT, nowMilli) > waitSec && maxSoFar != 0.0 && minSoFar != 0.0) {
            if (!noMoreBuy.get() && (freshPrice > maxSoFar || maxT.isAfter(minT))
                    && usHiloDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, buySize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU(str(o.orderId(), "US hilo buy", globalIdOrderMap.get(id),
                        "buy size/curr pos", buySize, currPos,
                        "last order T, milliLast2, waitsec", lastOrderT, milliLastTwo, waitSec,
                        "dir", usHiloDirection.get(symbol)));
                usHiloDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minSoFar || minT.isAfter(maxT))
                    && usHiloDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o;
                if (currPos > 0) {
                    o = placeOfferLimitTIF(freshPrice, Math.min(sellSize, currPos), IOC);
                } else {
                    o = placeShortSellLimitTIF(freshPrice, sellSize, IOC);
                }
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU(str(o.orderId(), "US hilo sell", globalIdOrderMap.get(id),
                        "sell size/curr pos", sellSize, currPos,
                        "last order T, millilast2, waitsec", lastOrderT, milliLastTwo, waitSec,
                        "dir", usHiloDirection.get(symbol)));
                usHiloDirection.put(symbol, Direction.Short);
            }
        }
    }

    private static void USLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime liqStartTime = ltof(15, 50);
        LocalTime liqEndTime = ltof(16, 0);
        Contract ct = tickerToUSContract(symbol);
        long liqWaitSecs = 60;

        if (nowMilli.toLocalTime().isBefore(liqStartTime) || nowMilli.toLocalTime().isAfter(liqEndTime)) {
            return;
        }

        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_CLOSE);
        OrderStatus lastOrderStatus = getLastOrderStatusForType(symbol, US_CLOSE);
        long numOrderCloseLiq = getOrderSizeForTradeType(symbol, US_CLOSE);

        double pos = ibPositionMap.getOrDefault(symbol, 0.0);

        if (numOrderCloseLiq >= 1) {
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > liqWaitSecs) {
            if (pos < 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, pos, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_CLOSE));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU(str(o.orderId(), " US close liq buy #:", numOrderCloseLiq,
                        globalIdOrderMap.get(id), "last order time", lastOrderTime, "currPos", pos, "size", pos));
            } else if (pos > 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, pos, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_CLOSE));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputOrderToAutoLogXU(str(o.orderId(), " US close liq sell #:", numOrderCloseLiq
                        , globalIdOrderMap.get(id), "last order time", lastOrderTime, "currPos", pos, "size", pos));
            }
        }

    }
}
