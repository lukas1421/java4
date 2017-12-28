package handler;

import apidemo.*;
import auxiliary.SimpleBar;
import client.TickType;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.TradingConstants.DATA_COLLECTION_TIME;

public class SGXFutureReceiver implements LiveHandler {

    private SGXFutureReceiver() {
    }

    private static SGXFutureReceiver gr = new SGXFutureReceiver();

    public static SGXFutureReceiver getReceiver() {
        return gr;
    }


    @Override
    public void handlePrice(TickType tt, String name, double price, LocalDateTime ldt) {
        FutType f = FutType.get(name);
        LocalTime t = ldt.toLocalTime();

        switch (tt) {
            case BID:
                XUTrader.bidMap.put(f, price);
                break;

            case ASK:
                XUTrader.askMap.put(f, price);
                break;

            case LAST:
                ChinaStock.priceMap.put(name, price);
                XUTrader.futPriceMap.put(f, price);

                if (t.isAfter(LocalTime.of(8, 55))) {
                    if (DATA_COLLECTION_TIME.test(ldt)) {
                        ChinaMain.currentTradingDate = ldt.toLocalDate();
                        if (priceMapBar.get(name).containsKey(t)) {
                            priceMapBar.get(name).get(t).add(price);
                        } else {
                            priceMapBar.get(name).put(t, new SimpleBar(price));
                        }

                        if (XUTrader.futData.get(f).containsKey(t)) {
                            XUTrader.futData.get(f).get(t).add(price);
                        } else {
                            XUTrader.futData.get(f).put(t, new SimpleBar(price));
                        }
                    }
                }
                break;
        }
    }

    @Override
    public void handleVol(String name, double vol, LocalDateTime ldt) {

        LocalTime t = ldt.toLocalTime();
        ChinaStock.sizeMap.put(name, (long) vol);

        if (DATA_COLLECTION_TIME.test(ldt)) {
            XU.frontFutVol.put(t, (int) vol);
            ChinaData.sizeTotalMap.get(name).put(t, 1d * vol);
        }
    }
}
