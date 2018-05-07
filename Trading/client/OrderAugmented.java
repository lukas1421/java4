package client;

import util.AutoTradeType;
import utility.Utility;

import java.time.LocalDateTime;

public class OrderAugmented {

    private final LocalDateTime orderTime;
    private final Order order;
    private final String msg;
    private AutoTradeType tradeType;

    public OrderAugmented(LocalDateTime t, Order o, String m, AutoTradeType tt) {
        orderTime = t;
        order = o;
        msg = m;
        tradeType = tt;
    }

    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    public Order getOrder() {
        return order;
    }

    public String getMsg() {
        return msg;
    }

    public AutoTradeType getTradeType() {
        return tradeType;
    }


    @Override
    public String toString() {
        return Utility.getStr("T order msg tradeType", orderTime, order, msg, tradeType);
    }
}
