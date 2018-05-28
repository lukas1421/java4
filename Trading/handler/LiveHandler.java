package handler;

import client.TickType;

import java.time.LocalDateTime;

import static utility.Utility.pr;

public interface LiveHandler extends GeneralHandler {
    void handlePrice(TickType tt, String name, double price, LocalDateTime t);
    void handleVol(String name, double vol, LocalDateTime t);

    class DefaultLiveHandler implements  LiveHandler {

        @Override
        public void handlePrice(TickType tt, String name, double price, LocalDateTime t) {
            if(tt == TickType.LAST) {
                pr(name, tt, price, t);
            }
        }

        @Override
        public void handleVol(String name, double vol, LocalDateTime t) {
        }
    }
}
