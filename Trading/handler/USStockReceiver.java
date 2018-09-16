package handler;

import client.TickType;

import java.time.LocalDateTime;

import static apidemo.AutoTraderUS.*;
import static utility.Utility.pr;

public class USStockReceiver implements LiveHandler {
    @Override
    public void handlePrice(TickType tt, String name, double price, LocalDateTime t) {
        switch (tt) {
            case BID:
                usBidMap.put(name, price);
                break;

            case ASK:
                usAskMap.put(name, price);
                break;

            case OPEN:
                usOpenMap.put(name, price);
                break;

            case CLOSE:
                pr("close in US price receiver: ", name, " close ", price);
                break;
            case LAST:
                usFreshPriceMap.put(name, price);
                usPriceMapDetail.get(name).put(t, price);
                break;
        }
    }

    @Override
    public void handleVol(String name, double vol, LocalDateTime t) {

    }
}
