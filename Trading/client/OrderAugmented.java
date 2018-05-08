package client;

import util.AutoOrderType;
import utility.Utility;

import java.time.LocalDateTime;

public class OrderAugmented {

    private final LocalDateTime orderTime;
    private final Order order;
    private final String msg;
    private AutoOrderType tradeType;

    public OrderAugmented(LocalDateTime t, Order o, String m, AutoOrderType tt) {
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

    public AutoOrderType getTradeType() {
        return tradeType;
    }


    @Override
    public String toString() {
        return Utility.getStr("T order msg tradeType TWSID",
                orderTime, order, msg, tradeType, order.orderId() == 0 ? "" : order.orderId());
    }
}
