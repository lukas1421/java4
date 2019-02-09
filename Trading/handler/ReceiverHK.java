package handler;

import api.ChinaData;
import client.Contract;
import client.TickType;

import java.time.LocalDateTime;

import static api.AutoTraderHK.*;
import static api.XuTraderHelper.outputDetailedHK;
import static utility.Utility.ibContractToSymbol;
import static utility.Utility.str;


public class ReceiverHK implements LiveHandler {

    private ReceiverHK() {
    }

    private static ReceiverHK rec = new ReceiverHK();

    public static ReceiverHK getReceiverHK() {
        return rec;
    }

    @Override
    // name starts with hk
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        //pr("hk tt name, price t", tt, symbol, price, t);
        String symbol = ibContractToSymbol(ct);
        switch (tt) {
            case LAST:
                //hkPriceMapDetail.get(name).put(t, price);
                ChinaData.priceMapBarDetail.get(symbol).put(t, price);
                hkFreshPriceMap.put(symbol, price);
                processeMainHK(symbol, t, price);
                break;
            case BID:
                hkBidMap.put(symbol, price);
                break;
            case ASK:
                hkAskMap.put(symbol, price);
                break;
            case OPEN:
                hkOpenMap.put(symbol, price);
                break;

        }
    }

    @Override
    public void handleVol(TickType tt, String name, double vol, LocalDateTime t) {

    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {
        switch (tt) {
            case SHORTABLE:
                outputDetailedHK(symbol, str("HK handle generic", tt, symbol, value, t));
                hkShortableValueMap.put(symbol, value);
                break;
        }
    }
}
