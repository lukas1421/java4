package handler;

import apidemo.ChinaData;
import apidemo.ChinaStock;
import auxiliary.SimpleBar;
import client.TickType;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public interface LiveHandler extends GeneralHandler {
    void handlePrice(TickType tt, String symbol, double price, LocalDateTime t);

    void handleVol(TickType tt, String symbol, double vol, LocalDateTime t);

    void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t);

    class DefaultLiveHandler implements LiveHandler {

        @Override
        public void handlePrice(TickType tt, String symbol, double price, LocalDateTime t) {
            LocalTime lt = t.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
            //pr(name, tt, price, t.toLocalTime());

            if (tt == TickType.LAST) {
                ChinaStock.priceMap.put(symbol, price);
                if (ChinaData.priceMapBar.get(symbol).containsKey(lt)) {
                    ChinaData.priceMapBar.get(symbol).get(lt).add(price);
                } else {
                    ChinaData.priceMapBar.get(symbol).put(lt, new SimpleBar(price));
                }
            } else if (tt == TickType.CLOSE) {
                ChinaStock.closeMap.put(symbol, price);
                if (ChinaStock.priceMap.getOrDefault(symbol, 0.0) == 0.0) {
                    ChinaStock.priceMap.put(symbol, price);
                }
            } else if (tt == TickType.OPEN) {
                ChinaStock.openMap.put(symbol, price);
            } else if (tt == TickType.BID || tt == TickType.ASK) {
//                if (ChinaStock.priceMap.getOrDefault(name, 0.0) == 0.0) {
//                    ChinaStock.priceMap.put(name, price);
//                }
            }
        }

        @Override
        public void handleVol(TickType tt, String symbol, double vol, LocalDateTime t) {
        }

        @Override
        public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {

        }
    }
}
