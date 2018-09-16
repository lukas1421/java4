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
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static apidemo.AutoTraderMain.*;
import static apidemo.XuTraderHelper.*;
import static util.AutoOrderType.US_STOCK_DEV;
import static utility.Utility.pr;
import static utility.Utility.str;

public class AutoTraderUS {

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDateTime, Double>> usPriceMapDetail;
    public static volatile ConcurrentHashMap<String, Double> usBidMap;
    public static volatile ConcurrentHashMap<String, Double> usAskMap;
    public static volatile ConcurrentHashMap<String, Double> usOpenMap;
    public static volatile ConcurrentHashMap<String, Double> usFreshPriceMap;
    private static volatile ConcurrentHashMap<String, Direction> usOpenDevDirection;
    private static volatile ConcurrentHashMap<String, Direction> usHiloDirection;


    //public static volatile ;
    public static final int MAX_US_ORDERS = 4;
    List<String> usList = new ArrayList<>();
    static double defaultStockSize = 100;

    String ticker = "NIO";

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
        usList.forEach(s -> {
            usOpenDevDirection.put(s, Direction.Flat);
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

        if (last > open && usOpenDevDirection.get(name) != Direction.Long) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, defaultStockSize, Types.TimeInForce.DAY);
            globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, US_STOCK_DEV));
            apcon.placeOrModifyOrder(ct, o, new ApiController.IOrderHandler.DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "US open dev BUY", globalIdOrderMap.get(id)));
            usHiloDirection.put(name, Direction.Long);
        } else if (last < open && usOpenDevDirection.get(name) != Direction.Short) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, defaultStockSize, Types.TimeInForce.DAY);
            globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, US_STOCK_DEV));
            apcon.placeOrModifyOrder(ct, o, new ApiController.IOrderHandler.DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "US open dev SELL", globalIdOrderMap.get(id)));
            usHiloDirection.put(name, Direction.Short);
        }
    }


    static void usHiloTrader(LocalDateTime nowMilli, String name, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        NavigableMap<LocalDateTime, Double> prices = usPriceMapDetail.get(name);
        Contract ct = generateUSContract(name);
        double open = usOpenMap.getOrDefault(name, 0.0);
        double last = usFreshPriceMap.getOrDefault(name, 0.0);
        Direction currDir = usOpenDevDirection.get(name);

        pr(" US open dev, name, price ", nowMilli, name, freshPrice);

        if (last > open && usHiloDirection.get(name) != Direction.Long) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, defaultStockSize, Types.TimeInForce.DAY);
            globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, US_STOCK_DEV));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
            outputOrderToAutoLog(str(o.orderId(), "US hilo buy", globalIdOrderMap.get(id)));
            usHiloDirection.put(name, Direction.Long);
        } else if (last < open && usHiloDirection.get(name) != Direction.Short) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, defaultStockSize, Types.TimeInForce.IOC);
            globalIdOrderMap.put(id, new OrderAugmented(name, nowMilli, o, US_STOCK_DEV));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeOrderHandler(id, apcon));
            outputOrderToAutoLog(str(o.orderId(), "US hilo sell", globalIdOrderMap.get(id)));
            usHiloDirection.put(name, Direction.Short);
        }

    }

}
