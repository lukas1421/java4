package apidemo;

import client.Contract;
import client.Types;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class AutoTraderUS {

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDateTime, Double>> usPriceMapDetail;
    public static volatile ConcurrentHashMap<String, Double> usBidMap;
    public static volatile ConcurrentHashMap<String, Double> usAskMap;
    public static volatile ConcurrentHashMap<String, Double> usOpenMap;
    public static volatile ConcurrentHashMap<String, Double> usFreshPriceMap;
    public static volatile ConcurrentHashMap<String, Direction> directionMap;
    //public static volatile ;
    public static final int MAX_US_ORDERS = 4;
    List<String> usList = new ArrayList<>();




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
            directionMap.put(s, Direction.Flat);
        });

    }


    static void usOpenDeviationTrader(String name) {
        Contract ct = generateUSContract(name);
        NavigableMap<LocalDateTime, Double> prices = usPriceMapDetail.get(name);
        double open = usOpenMap.getOrDefault(name, 0.0);
        double last = usFreshPriceMap.getOrDefault(name, 0.0);
        Direction currDir = directionMap.get(name);


        if (last > open && directionMap.get(name) != Direction.Long) {
            int tradeID = AutoTraderXU.autoTradeID.incrementAndGet();

        } else if (last < open && directionMap.get(name) != Direction.Short) {


            //double open = prices.ceilingEntry(LocalDateTime.of());
        }
    }


    static void usHiloTrader(String name) {
        NavigableMap<LocalDateTime, Double> prices = usPriceMapDetail.get(name);


    }

}
