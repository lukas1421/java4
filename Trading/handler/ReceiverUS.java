package handler;

import client.TickType;

import java.time.LocalDateTime;

import static apidemo.AutoTraderUS.*;


public class ReceiverUS implements LiveHandler {
    private ReceiverUS() {
    }

    private static final ReceiverUS rec = new ReceiverUS();

    public static ReceiverUS getReceiverUS() {
        return rec;
    }

    @Override
    public void handlePrice(TickType tt, String symbol, double price, LocalDateTime t) {
        switch (tt) {
            case BID:
                usBidMap.put(symbol, price);
                break;
            case ASK:
                usAskMap.put(symbol, price);
                break;
            case LAST:
                usFreshPriceMap.put(symbol, price);
                processMainUS(symbol, t, price);
                break;
            case OPEN:
                usOpenMap.put(symbol, price);
                break;
        }

    }

    @Override
    public void handleVol(String name, double vol, LocalDateTime t) {

    }
}
