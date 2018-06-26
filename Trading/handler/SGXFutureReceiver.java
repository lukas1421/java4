package handler;

import apidemo.*;
import auxiliary.SimpleBar;
import client.TickType;
import utility.Utility;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.TradingConstants.FUT_COLLECTION_TIME;
import static apidemo.TradingConstants.STOCK_COLLECTION_TIME;
import static java.time.temporal.ChronoUnit.MINUTES;

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
        LocalDateTime ldtMin = ldt.truncatedTo(MINUTES);
        LocalTime t = ldtMin.toLocalTime();

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

                // need to capture overnight data
                if (t.isAfter(LocalTime.of(8, 55)) || t.isBefore(LocalTime.of(5, 0))) {
                    if (STOCK_COLLECTION_TIME.test(ldtMin)) {
                        ChinaMain.currentTradingDate = ldtMin.toLocalDate();
                        if (priceMapBar.get(name).containsKey(t)) {
                            priceMapBar.get(name).get(t).add(price);
                        } else {
                            priceMapBar.get(name).put(t, new SimpleBar(price));
                        }
                    }

                    if (FUT_COLLECTION_TIME.test(ldt)) {
                        if (XUTrader.futData.get(f).containsKey(ldtMin)) {
                            XUTrader.futData.get(f).get(ldtMin).add(price);
                        } else {
                            XUTrader.futData.get(f).put(ldtMin, new SimpleBar(price));
                        }

                        String activeFut = Utility.ibContractToFutType(XUTrader.activeFuture).getTicker();

                        if (name.equalsIgnoreCase(activeFut) &&
                                XUTrader.futData.get(f).lastKey().truncatedTo(MINUTES).equals(ldt.truncatedTo(MINUTES))) {
                            XUTrader.processAll(ldt, price);
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

        if (STOCK_COLLECTION_TIME.test(ldt)) {
            XU.frontFutVol.put(t, (int) vol);
            ChinaData.sizeTotalMap.get(name).put(t, 1d * vol);
        }
    }
}
