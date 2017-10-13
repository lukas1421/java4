package handler;

import java.time.LocalTime;

public interface LiveHandler extends GeneralHandler {

    void handlePrice(String name, double price, LocalTime t);

    void handleVol(String name, double vol, LocalTime t);

}
