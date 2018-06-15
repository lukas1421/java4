package handler;

import apidemo.ChinaData;
import apidemo.ChinaStock;
import auxiliary.SimpleBar;
import client.TickType;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public interface LiveHandler extends GeneralHandler {
    void handlePrice(TickType tt, String name, double price, LocalDateTime t);

    void handleVol(String name, double vol, LocalDateTime t);

    class DefaultLiveHandler implements LiveHandler {

        @Override
        public void handlePrice(TickType tt, String name, double price, LocalDateTime t) {
            LocalTime lt = t.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
            //Utility.pr(name, tt, price, t.toLocalTime());
            if (tt == TickType.LAST) {
                ChinaStock.priceMap.put(name, price);
                if (ChinaData.priceMapBar.get(name).containsKey(lt)) {
                    ChinaData.priceMapBar.get(name).get(lt).add(price);
                } else {
                    ChinaData.priceMapBar.get(name).put(lt, new SimpleBar(price));
                }
            } else if (tt == TickType.CLOSE) {
                ChinaStock.closeMap.put(name, price);
            } else if (tt == TickType.OPEN) {
                ChinaStock.openMap.put(name, price);
            } else if (tt == TickType.BID || tt == TickType.ASK) {
                if (ChinaStock.priceMap.getOrDefault(name, 0.0) == 0.0) {
                    ChinaStock.priceMap.put(name, price);
                }
            }
        }

        @Override
        public void handleVol(String name, double vol, LocalDateTime t) {
        }
    }
}
