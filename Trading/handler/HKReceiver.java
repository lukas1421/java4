package handler;

import apidemo.ChinaData;
import client.TickType;

import java.time.LocalDateTime;

import static apidemo.AutoTraderHK.*;
import static utility.Utility.pr;


public class HKReceiver implements LiveHandler {

    private HKReceiver() {
    }

    private static HKReceiver rec = new HKReceiver();

    public static HKReceiver getReceiver() {
        return rec;
    }

    @Override
    // name starts with hk
    public void handlePrice(TickType tt, String name, double price, LocalDateTime t) {
        pr("hk tt name, price t", tt, name, price, t);

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
                //hkPriceMapDetail.get(name).put(t, price);
                ChinaData.priceMapBarDetail.get(name).put(t.toLocalTime(), price);
                hkFreshPriceMap.put(name, price);
                processeMainHK(name, t, price);
                break;
        }
    }

    @Override
    public void handleVol(String name, double vol, LocalDateTime t) {

    }
}
