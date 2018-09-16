package handler;

import client.TickType;

import java.time.LocalDateTime;

import static apidemo.AutoTraderHK.*;


public class HKReceiver implements LiveHandler {

    private HKReceiver() {
    }

    private static HKReceiver rec = new HKReceiver();

    public static HKReceiver getReceiver() {
        return rec;
    }

    @Override
    public void handlePrice(TickType tt, String name, double price, LocalDateTime t) {
        switch (tt) {
            case BID:
                hkBidMap.put(name, price);
                break;
            case ASK:
                hkAskMap.put(name, price);
                break;
            case OPEN:
                hkOpenMap.put(name, price);
                break;
            case LAST:
                hkPriceMapDetail.get(name).put(t, price);
                hkFreshPriceMap.put(name, price);
                break;
        }
    }

    @Override
    public void handleVol(String name, double vol, LocalDateTime t) {

    }
}
