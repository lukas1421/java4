package client;

import utility.Utility;

import java.time.LocalDateTime;

public class OrderAugmented {

    private final LocalDateTime orderTime;
    private final Order order;
    private final String msg;

    public OrderAugmented(LocalDateTime t, Order o, String m) {
        orderTime = t;
        order = o;
        msg = m;
    }

    @Override
    public String toString() {
        return Utility.getStr("T order msg ", orderTime, order, msg);
    }
}
