package apidemo;

import client.Contract;
import client.Order;
import client.OrderAugmented;
import client.Types;
import controller.ApiController;

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
import static client.Types.TimeInForce.IOC;
import static java.time.temporal.ChronoUnit.SECONDS;
import static util.AutoOrderType.US_STOCK_DEV;
import static util.AutoOrderType.US_STOCK_HILO;
import static utility.Utility.pr;
import static utility.Utility.str;

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
    List<String> usList = new ArrayList<>();
    static double usStockSize = 100;
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
        Contract nio = generateUSContract("nio");
        Contract iq = generateUSContract("iq");
        String ticker = "NIO";
        usList.add(ticker);
        usList.forEach(s -> {
            usHiloDirection.put(s, Direction.Flat);
            usOpenDevDirection.put(s, Direction.Flat);
            manualUSDevMap.put(s, new AtomicBoolean(false));
            manualUSHiloMap.put(s, new AtomicBoolean(false));
        });
    }

    static void usOpenDeviationTrader(LocalDateTime nowMilli, String name, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = generateUSContract(name);
        NavigableMap<LocalDateTime, Double> prices = usPriceMapDetail.get(name);
        double open = usOpenMap.getOrDefault(name, 0.0);
        double last = usFreshPriceMap.getOrDefault(name, 0.0);
        Direction currDir = usOpenDevDirection.get(name);

        pr(" US open dev, name, price ", nowMilli, name, freshPrice);


        if (!manualUSDevMap.get(name).get()) {
            if (lt.isBefore(ltof(9, 25))) {
                manualUSDevMap.get(name).set(true);
            } else {
                if (last > open) {
                    usOpenDevDirection.put(name, Direction.Long);
                    manualUSDevMap.get(name).set(true);
                } else if (last < open) {
                    usOpenDevDirection.put(name, Direction.Short);
                    manualUSDevMap.get(name).set(true);
                } else {
                    usOpenDevDirection.put(name, Direction.Flat);
                }
            }
        }

        if (!noMoreBuy.get() && last > open && usOpenDevDirection.get(name) != Direction.Long) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, usStockSize, Types.TimeInForce.DAY);
            globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, US_STOCK_DEV));
            apcon.placeOrModifyOrder(ct, o, new ApiController.IOrderHandler.DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "US open dev BUY", globalIdOrderMap.get(id)));
            usOpenDevDirection.put(name, Direction.Long);
        } else if (!noMoreSell.get() && last < open && usOpenDevDirection.get(name) != Direction.Short) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, usStockSize, Types.TimeInForce.DAY);
            globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, US_STOCK_DEV));
            apcon.placeOrModifyOrder(ct, o, new ApiController.IOrderHandler.DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "US open dev SELL", globalIdOrderMap.get(id)));
            usOpenDevDirection.put(name, Direction.Short);
        }
    }


    static void usHiloTrader(LocalDateTime nowMilli, String name, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        NavigableMap<LocalDateTime, Double> prices = usPriceMapDetail.get(name);
        Contract ct = generateUSContract(name);

        pr(" US open dev, name, price ", nowMilli, name, freshPrice);

        LocalDateTime lastKey = prices.lastKey();
        double maxSoFar = prices.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);
        double minSoFar = prices.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalDateTime maxT = getFirstMaxTPredLdt(prices, e -> true);
        LocalDateTime minT = getFirstMaxTPredLdt(prices, e -> true);


        if (!manualUSHiloMap.get(name).get()) {
            if (lt.isBefore(ltof(9, 25))) {
                manualUSHiloMap.get(name).set(true);
            } else {
                if (maxT.isAfter(minT)) {
                    usHiloDirection.put(name, Direction.Long);
                    manualUSHiloMap.get(name).set(true);
                } else if (minT.isAfter(maxT)) {
                    usHiloDirection.put(name, Direction.Short);
                    manualUSHiloMap.get(name).set(true);
                } else {
                    usHiloDirection.put(name, Direction.Flat);
                }
            }
        }
        LocalDateTime lastOrderT = getLastOrderTime(name, US_STOCK_HILO);
        long milliLastTwo = lastTwoOrderMilliDiff(name, US_STOCK_HILO);
        int waitSec = milliLastTwo < 60000 ? 300 : 10;


        if (SECONDS.between(lastOrderT, nowMilli) > waitSec && maxSoFar != 0.0 && minSoFar != 0.0) {
            if (!noMoreBuy.get() && (freshPrice > maxSoFar || maxT.isAfter(minT))
                    && usHiloDirection.get(name) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, usStockSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, US_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "US hilo buy", globalIdOrderMap.get(id)));
                usHiloDirection.put(name, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minSoFar || minT.isAfter(maxT))
                    && usHiloDirection.get(name) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, usStockSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, US_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "US hilo sell", globalIdOrderMap.get(id)));
                usHiloDirection.put(name, Direction.Short);
            }
        }

    }

}
