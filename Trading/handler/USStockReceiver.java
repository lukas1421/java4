package handler;

import client.Contract;
import client.TickType;

import java.time.LocalDateTime;

import static AutoTraderOld.AutoTraderUS.*;
import static utility.Utility.ibContractToSymbol;
import static utility.Utility.pr;

public class USStockReceiver implements LiveHandler {
    @Override
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        switch (tt) {
            case BID:
                usBidMap.put(symbol, price);
                break;

            case ASK:
                usAskMap.put(symbol, price);
                break;

            case OPEN:
                usOpenMap.put(symbol, price);
                break;

            case CLOSE:
                pr("close in US price receiver: ", symbol, " close ", price);
                break;
            case LAST:
                usFreshPriceMap.put(symbol, price);
                usPriceMapDetail.get(symbol).put(t, price);
                break;
        }
    }

    @Override
    public void handleVol(TickType tt, String name, double vol, LocalDateTime t) {

    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {

    }
}
