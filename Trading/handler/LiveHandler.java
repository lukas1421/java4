package handler;

import client.TickType;

import java.time.LocalDateTime;
import java.time.LocalTime;

public interface LiveHandler extends GeneralHandler {
    void handlePrice(TickType tt, String name, double price, LocalDateTime t);
    void handleVol(String name, double vol, LocalTime t);
}
