package handler;

import apidemo.ChinaData;
import apidemo.ChinaStock;
import auxiliary.SimpleBar;
import client.TickType;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static utility.Utility.pr;

public interface LiveHandler extends GeneralHandler {
    void handlePrice(TickType tt, String name, double price, LocalDateTime t);

    void handleVol(String name, double vol, LocalDateTime t);

    class DefaultLiveHandler implements LiveHandler {

        @Override
        public void handlePrice(TickType tt, String name, double price, LocalDateTime t) {
            LocalTime lt = t.toLocalTime();
            if (tt == TickType.LAST) {

                ChinaStock.priceMap.put(name, price);

                if (ChinaData.priceMapBar.get(name).containsKey(lt)) {
                    ChinaData.priceMapBar.get(name).get(lt).add(price);
                } else {
                    ChinaData.priceMapBar.get(name).put(lt, new SimpleBar(price));
                }
                pr(name, tt, price, t);
            }
        }

        @Override
        public void handleVol(String name, double vol, LocalDateTime t) {
        }
    }
}
