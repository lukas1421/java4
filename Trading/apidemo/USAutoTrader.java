package apidemo;

import client.Contract;
import client.Types;

import java.time.LocalDateTime;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class USAutoTrader {


    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDateTime, Double>> usPriceMapDetail;
    public static volatile ConcurrentHashMap<String, Double> usBidMap;
    public static volatile ConcurrentHashMap<String, Double> usAskMap;
    public static volatile ConcurrentHashMap<String, Double> usOpenMap;
    public static volatile ConcurrentHashMap<String, Double> usFreshPriceMap;


    String ticker = "NIO";

    private static Contract generateUSContract(String stock) {
        Contract ct = new Contract();
        ct.symbol(stock);
        ct.exchange("SMART");
        ct.currency("USD");
        ct.secType(Types.SecType.STK);
        return ct;
    }

    USAutoTrader() {
        Contract nio = generateUSContract("nio");

    }


    static void usOpenDeviationTrader(String name) {
        Contract ct = generateUSContract(name);
        NavigableMap<LocalDateTime, Double> prices = usPriceMapDetail.get(name);

        //double open = prices.ceilingEntry(LocalDateTime.of());


    }

    static void usHiloTrader(String name) {

    }

}
