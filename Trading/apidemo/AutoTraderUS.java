package apidemo;

import client.Contract;
import client.Order;
import client.OrderAugmented;
import client.Types;

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
import static util.AutoOrderType.US_STOCK_DEV;
import static util.AutoOrderType.US_STOCK_HILO;
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
    public static final int MAX_US_ORDERS = 4;
    public static List<String> usSymbols = new ArrayList<>();
    private static final double usStockSize = 100;
    static String ticker = "IQ";


    private static Contract generateUSContract(String stock) {
        Contract ct = new Contract();
        ct.symbol(stock);
        ct.exchange("SMART");
        ct.currency("USD");
        ct.secType(Types.SecType.STK);
        return ct;
    }

    AutoTraderUS() {
        Contract iq = generateUSContract("IQ");
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
        Contract ct = generateUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double open = usOpenMap.getOrDefault(symbol, 0.0);
        double last = usFreshPriceMap.getOrDefault(symbol, 0.0);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);

        if (lt.isBefore(LocalTime.of(9, 20)) || lt.isAfter(LocalTime.of(10, 0))) {
            return;
        }

        if (!manualUSDevMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 25))) {
                manualUSDevMap.get(symbol).set(true);
            } else {
                if (last > open) {
                    usOpenDevDirection.put(symbol, Direction.Long);
                    manualUSDevMap.get(symbol).set(true);
                } else if (last < open) {
                    usOpenDevDirection.put(symbol, Direction.Short);
                    manualUSDevMap.get(symbol).set(true);
                } else {
                    usOpenDevDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, US_STOCK_DEV);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_STOCK_DEV);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, US_STOCK_DEV);
        int waitSec = (milliLastTwo > 60000) ? 300 : 10;

        double manualOpen = prices.ceilingEntry(ltof(9, 20)).getValue();

        if (numOrders >= MAX_US_ORDERS) {
            return;
        }

        pr(" US open dev, name, price ", nowMilli, symbol, freshPrice,
                "open/manual open ", usOpenMap.getOrDefault(symbol, 0.0), manualOpen,
                "last order T/ millilast2/ waitsec", lastOrderTime, milliLastTwo, waitSec,
                "curr pos ", currPos);


        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec)
            if (!noMoreBuy.get() && last > open && usOpenDevDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, usStockSize, Types.TimeInForce.DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_DEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLogXU(str(o.orderId(), "US open dev BUY", globalIdOrderMap.get(id)));
                usOpenDevDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && last < open && usOpenDevDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o;

                if (currPos >= usStockSize) {
                    o = placeOfferLimitTIF(freshPrice, usStockSize, DAY);
                } else {
                    o = placeShortSellLimitTIF(freshPrice, usStockSize, DAY);
                }
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_DEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputOrderToAutoLogXU(str(o.orderId(), "US open dev SELL", globalIdOrderMap.get(id)));
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
        Contract ct = generateUSContract(symbol);

        if (lt.isBefore(LocalTime.of(9, 20)) || lt.isAfter(LocalTime.of(10, 0))) {
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


        if (!manualUSHiloMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 25))) {
                manualUSHiloMap.get(symbol).set(true);
            } else {
                if (maxT.isAfter(minT)) {
                    usHiloDirection.put(symbol, Direction.Long);
                    manualUSHiloMap.get(symbol).set(true);
                } else if (minT.isAfter(maxT)) {
                    usHiloDirection.put(symbol, Direction.Short);
                    manualUSHiloMap.get(symbol).set(true);
                } else {
                    usHiloDirection.put(symbol, Direction.Flat);
                }
            }
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
                Order o = placeBidLimitTIF(freshPrice, usStockSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLogXU(str(o.orderId(), "US hilo buy", globalIdOrderMap.get(id)));
                usHiloDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minSoFar || minT.isAfter(maxT))
                    && usHiloDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o;
                if (currPos >= usStockSize) {
                    o = placeOfferLimitTIF(freshPrice, usStockSize, IOC);
                } else {
                    o = placeShortSellLimitTIF(freshPrice, usStockSize, IOC);
                }
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLogXU(str(o.orderId(), "US hilo sell", globalIdOrderMap.get(id)));
                usHiloDirection.put(symbol, Direction.Short);
            }
        }
    }
}
